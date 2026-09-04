#pragma once
#include <cstdint>
#include <vector>
#include <string>
#include <algorithm>
#include <random>
#include <cmath>
#include "MusicTheory.hpp"

namespace Cobass::Transform {

struct NoteEvent {
    int32_t pitch = 60;
    float velocity = 0.85f;
    int64_t startOffsetTicks = 0;
    int64_t lengthTicks = 480;
    bool isMuted = false;
    bool isSelected = false;
    uint32_t flags = 0;
};

enum class TransformOperatorType : uint32_t {
    EUCLIDEAN_SLICE        = 0,
    RATCHET_BURST          = 1,
    MARKOV_DRIFT           = 2,
    ENCLOSURE_DECORATE     = 3,
    MODAL_INVERSION        = 4,
    DIATONIC_VOICING       = 5,
    CALL_RESPONSE_INFILL   = 6,
    CLAVE_SLIP             = 7,
    PALINDROME_MIRROR      = 8,
    GOLDEN_PHRASE_ARC      = 9,
    HUMANIZE_GROOVE        = 10,
    SCALE_CONSTRAIN        = 11,

    // Phase 3 Melodic & Counterpoint Engines
    SCHENKER_LEAD_TOWARD   = 12,
    BARTOK_PITCH_WEDGE     = 13,
    COMPOUND_POLY_WEAVE    = 14,
    DIATONIC_CASCADE_RUN   = 15,

    // Phase 4 Harmonic Voicings & Bass Extractor
    CHORD_DROP_VOICING     = 16,
    CONTRARY_COUNTERPOINT  = 17,
    SUB_BASS_EXTRACTOR     = 18,

    // Phase 5 Strumming, Expression & Dynamics
    GUITAR_STRUM_PHYSICS   = 19,
    MAQAM_MICROTONAL_BEND  = 20,
    PARABOLIC_VELOCITY_DOME = 21
};

struct LockMasks {
    bool lockDownbeats      = false;
    bool lockPitches        = false;
    bool lockRhythm         = false;
    bool lockVelocities     = false;
    bool lockBassNotes      = false;
};

struct MusicalContext {
    int32_t rootKey = 0;
    uint32_t scaleIntervalMask = 0b101010110101; // Major by default
    int32_t ticksPerBeat = 480;                  // PPQ
    int32_t beatsPerBar = 4;
    int64_t clipStartGlobalTick = 0;
    int64_t clipLengthTicks = 1920 * 2;
    int32_t maxPolyphonyPerBeat = 6;
};

struct TransformRecipe {
    TransformOperatorType type = TransformOperatorType::SCALE_CONSTRAIN;
    float intensity = 0.5f;   // 0.0f to 1.0f
    uint32_t seed = 12345;
    float param1 = 0.0f;
    float param2 = 0.0f;
    bool enabled = true;
};

class NoteTransformEngine {
public:
    static inline bool isMetricDownbeat(int64_t offsetTicks, int32_t ppq, int32_t beatsPerBar) noexcept {
        const int64_t barTicks = static_cast<int64_t>(ppq) * beatsPerBar;
        const int64_t posInBar = (offsetTicks >= 0) ? (offsetTicks % barTicks) : 0;
        return (posInBar == 0) || (posInBar == static_cast<int64_t>(ppq * 2));
    }

    static inline float calculatePhraseArcGain(int64_t currentTick, int64_t startTick, int64_t endTick) noexcept {
        if (endTick <= startTick) return 1.0f;
        const float t = std::clamp(static_cast<float>(currentTick - startTick) / static_cast<float>(endTick - startTick), 0.0f, 1.0f);
        const float phiPower = std::pow(t, 0.618f);
        return 0.70f + 0.30f * std::sin(3.14159265f * phiPower);
    }

    static std::vector<bool> generateEuclidean(int32_t pulses, int32_t steps, int32_t rotation = 0) {
        if (steps <= 0) return {};
        int32_t k = std::clamp(pulses, 0, steps);
        std::vector<bool> pattern(steps, false);
        if (k == 0) return pattern;
        if (k == steps) {
            std::fill(pattern.begin(), pattern.end(), true);
            return pattern;
        }

        std::vector<std::vector<bool>> groups;
        groups.reserve(steps);
        for (int32_t i = 0; i < steps; ++i) groups.push_back({i < k});

        int32_t countZeros = steps - k;
        int32_t countOnes = k;

        while (countZeros > 1 && countOnes > 1) {
            int32_t numMerges = std::min(countOnes, countZeros);
            for (int32_t i = 0; i < numMerges; ++i) {
                auto& back = groups[groups.size() - 1 - i];
                groups[i].insert(groups[i].end(), back.begin(), back.end());
            }
            groups.erase(groups.end() - numMerges, groups.end());

            int32_t nextZeros = std::abs(countZeros - countOnes);
            int32_t nextOnes = numMerges;
            countZeros = nextZeros;
            countOnes = nextOnes;
        }

        size_t idx = 0;
        for (const auto& g : groups) {
            for (bool val : g) {
                if (idx < pattern.size()) pattern[idx++] = val;
            }
        }

        if (rotation != 0) {
            std::vector<bool> rotated(steps, false);
            for (int32_t i = 0; i < steps; ++i) {
                int32_t targetIdx = ((i + rotation) % steps + steps) % steps;
                rotated[targetIdx] = pattern[i];
            }
            return rotated;
        }
        return pattern;
    }

    static std::vector<NoteEvent> process(
        const std::vector<NoteEvent>& sourceNotes,
        const MusicalContext& context,
        const std::vector<TransformRecipe>& recipes,
        const LockMasks& masks,
        float dryWetRatio = 1.0f
    ) {
        if (sourceNotes.empty()) return {};

        std::vector<NoteEvent> currentNotes = sourceNotes;

        for (const auto& recipe : recipes) {
            if (!recipe.enabled) continue;
            currentNotes = applySingleOperator(currentNotes, context, recipe, masks);
        }

        cleanDuplicatesAndLimitDensity(currentNotes, context);

        if (dryWetRatio < 0.999f && sourceNotes.size() == currentNotes.size()) {
            for (size_t i = 0; i < sourceNotes.size(); ++i) {
                const auto& src = sourceNotes[i];
                auto& dst = currentNotes[i];

                if (masks.lockPitches) {
                    dst.pitch = src.pitch;
                } else {
                    float blendedPitch = src.pitch * (1.0f - dryWetRatio) + dst.pitch * dryWetRatio;
                    dst.pitch = Music::snapPitchToScale(std::round(blendedPitch), context.rootKey, context.scaleIntervalMask);
                }

                if (masks.lockVelocities) {
                    dst.velocity = src.velocity;
                } else {
                    dst.velocity = src.velocity * (1.0f - dryWetRatio) + dst.velocity * dryWetRatio;
                }

                if (masks.lockRhythm) {
                    dst.startOffsetTicks = src.startOffsetTicks;
                    dst.lengthTicks = src.lengthTicks;
                }
            }
        }

        return currentNotes;
    }

    static std::vector<NoteEvent> applySingleOperator(
        const std::vector<NoteEvent>& notes,
        const MusicalContext& context,
        const TransformRecipe& recipe,
        const LockMasks& masks
    ) {
        std::vector<NoteEvent> result;
        result.reserve(notes.size() * 3);

        uint32_t saltedSeed = recipe.seed ^ (static_cast<uint32_t>(recipe.type) * 2654435761u);
        std::mt19937 rng(saltedSeed);

        switch (recipe.type) {
            case TransformOperatorType::EUCLIDEAN_SLICE: {
                result.reserve(notes.size() * 8);
                for (const auto& n : notes) {
                    if (masks.lockRhythm || (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar))) {
                        result.push_back(n);
                        continue;
                    }

                    int32_t steps = static_cast<int32_t>(recipe.param1 > 0.0f ? recipe.param1 : 8.0f);
                    int32_t pulses = static_cast<int32_t>(recipe.param2 > 0.0f ? recipe.param2 : std::round(steps * recipe.intensity));
                    pulses = std::clamp(pulses, 1, steps);

                    auto pattern = generateEuclidean(pulses, steps, 0);
                    int64_t sliceDuration = n.lengthTicks / steps;
                    if (sliceDuration < 15) {
                        result.push_back(n);
                        continue;
                    }

                    int32_t activePulseIdx = 0;
                    for (int32_t s = 0; s < steps; ++s) {
                        if (pattern[s]) {
                            NoteEvent slice = n;
                            slice.startOffsetTicks = n.startOffsetTicks + (s * sliceDuration);
                            slice.lengthTicks = std::max<int64_t>(10, static_cast<int64_t>(sliceDuration * 0.85f));
                            float decayMult = std::pow(0.88f, static_cast<float>(activePulseIdx));
                            if (!masks.lockVelocities) {
                                slice.velocity = std::clamp(n.velocity * decayMult, 0.20f, 1.0f);
                            }
                            result.push_back(slice);
                            activePulseIdx++;
                        }
                    }
                }
                break;
            }

            case TransformOperatorType::RATCHET_BURST: {
                result.reserve(notes.size() * 8);
                for (const auto& n : notes) {
                    if (masks.lockRhythm || (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar))) {
                        result.push_back(n);
                        continue;
                    }

                    int32_t subdivisions = static_cast<int32_t>(recipe.param1 >= 2.0f ? recipe.param1 : (2 + std::round(recipe.intensity * 6.0f)));
                    subdivisions = std::clamp(subdivisions, 2, 8);

                    bool accelerating = (recipe.param2 >= 0.0f);
                    int64_t totalLen = n.lengthTicks;
                    int64_t currentOffset = n.startOffsetTicks;

                    float weightSum = 0.0f;
                    std::vector<float> weights(subdivisions);
                    for (int32_t s = 0; s < subdivisions; ++s) {
                        float w = accelerating ? (static_cast<float>(subdivisions - s)) : (static_cast<float>(s + 1));
                        weights[s] = w;
                        weightSum += w;
                    }

                    for (int32_t s = 0; s < subdivisions; ++s) {
                        int64_t segLen = static_cast<int64_t>((weights[s] / weightSum) * totalLen);
                        if (segLen < 15) segLen = 15;

                        NoteEvent burst = n;
                        burst.startOffsetTicks = currentOffset;
                        burst.lengthTicks = static_cast<int64_t>(segLen * 0.85f);

                        if (!masks.lockVelocities) {
                            float progress = static_cast<float>(s) / static_cast<float>(subdivisions - 1);
                            burst.velocity = std::clamp(0.40f + progress * 0.60f, 0.15f, 1.0f);
                        }

                        if (!masks.lockPitches && s >= subdivisions - 2 && recipe.intensity > 0.4f) {
                            burst.pitch = Music::snapPitchToScale(n.pitch + (s == subdivisions - 1 ? 2 : 1), context.rootKey, context.scaleIntervalMask);
                        }

                        result.push_back(burst);
                        currentOffset += segLen;
                    }
                }
                break;
            }

            case TransformOperatorType::ENCLOSURE_DECORATE: {
                result.reserve(notes.size() * 3);
                std::uniform_real_distribution<float> probDist(0.0f, 1.0f);
                for (const auto& n : notes) {
                    if (masks.lockPitches || masks.lockRhythm || probDist(rng) > recipe.intensity || n.lengthTicks < 240) {
                        result.push_back(n);
                        continue;
                    }

                    int32_t upperNote = 0;
                    int32_t lowerNote = 0;
                    Music::getEnclosureTones(n.pitch, context.rootKey, context.scaleIntervalMask, upperNote, lowerNote);

                    int64_t graceLen = std::min<int64_t>(60, n.lengthTicks / 4);

                    NoteEvent upper = n;
                    upper.pitch = upperNote;
                    upper.startOffsetTicks = std::max<int64_t>(0, n.startOffsetTicks - (graceLen * 2));
                    upper.lengthTicks = static_cast<int64_t>(graceLen * 0.8f);
                    if (!masks.lockVelocities) upper.velocity = std::clamp(n.velocity * 0.70f, 0.20f, 1.0f);

                    NoteEvent lower = n;
                    lower.pitch = lowerNote;
                    lower.startOffsetTicks = std::max<int64_t>(0, n.startOffsetTicks - graceLen);
                    lower.lengthTicks = static_cast<int64_t>(graceLen * 0.8f);
                    if (!masks.lockVelocities) lower.velocity = std::clamp(n.velocity * 0.80f, 0.20f, 1.0f);

                    NoteEvent target = n;
                    if (!masks.lockVelocities) target.velocity = std::clamp(n.velocity * 1.15f, 0.30f, 1.0f);

                    result.push_back(upper);
                    result.push_back(lower);
                    result.push_back(target);
                }
                break;
            }

            case TransformOperatorType::DIATONIC_VOICING: {
                result.reserve(notes.size() * 2);
                int32_t degreeShift = static_cast<int32_t>(recipe.param1 != 0.0f ? recipe.param1 : 2.0f);
                int32_t style = static_cast<int32_t>(recipe.param2);

                for (const auto& n : notes) {
                    result.push_back(n);

                    if (masks.lockPitches || (masks.lockBassNotes && n.pitch < 48)) continue;

                    int32_t harmPitch = Music::shiftDiatonicDegree(n.pitch, degreeShift, context.rootKey, context.scaleIntervalMask);

                    if (style == 1) {
                        harmPitch -= 12;
                    } else if (style == 2) {
                        harmPitch = Music::shiftDiatonicDegree(n.pitch, degreeShift + 7, context.rootKey, context.scaleIntervalMask);
                    }

                    if (harmPitch >= 0 && harmPitch <= 127) {
                        NoteEvent harmony = n;
                        harmony.pitch = harmPitch;
                        if (!masks.lockVelocities) {
                            harmony.velocity = std::clamp(n.velocity * 0.82f, 0.20f, 1.0f);
                        }
                        result.push_back(harmony);
                    }
                }
                break;
            }

            case TransformOperatorType::CALL_RESPONSE_INFILL: {
                result = notes;
                if (notes.size() < 2) break;

                for (size_t i = 0; i < notes.size() - 1; ++i) {
                    int64_t gapStart = notes[i].startOffsetTicks + notes[i].lengthTicks;
                    int64_t gapEnd = notes[i + 1].startOffsetTicks;
                    int64_t gapDuration = gapEnd - gapStart;

                    if (gapDuration >= context.ticksPerBeat * 2) {
                        int32_t pulses = 3;
                        int64_t stepTicks = gapDuration / (pulses + 1);
                        int32_t refPitch = notes[i].pitch;

                        for (int32_t p = 0; p < pulses; ++p) {
                            int32_t respPitch = Music::shiftDiatonicDegree(refPitch, -(p + 1), context.rootKey, context.scaleIntervalMask);
                            NoteEvent resp;
                            resp.pitch = respPitch;
                            resp.startOffsetTicks = gapStart + (p * stepTicks);
                            resp.lengthTicks = static_cast<int64_t>(stepTicks * 0.85f);
                            resp.velocity = 0.70f + (p * 0.08f);
                            resp.isSelected = true;
                            result.push_back(resp);
                        }
                    }
                }
                break;
            }

            case TransformOperatorType::PALINDROME_MIRROR: {
                if (notes.empty()) break;
                int64_t centerTick = context.clipLengthTicks / 2;
                result = notes;

                for (const auto& n : notes) {
                    if (n.startOffsetTicks >= centerTick) continue;

                    int64_t deltaFromCenter = centerTick - n.startOffsetTicks;
                    int64_t mirrorStart = centerTick + deltaFromCenter - n.lengthTicks;

                    if (mirrorStart >= centerTick && mirrorStart + n.lengthTicks <= context.clipLengthTicks) {
                        NoteEvent mirrored = n;
                        mirrored.startOffsetTicks = mirrorStart;
                        mirrored.pitch = Music::invertModalPitch(n.pitch, 60, context.rootKey, context.scaleIntervalMask);
                        mirrored.isSelected = true;
                        result.push_back(mirrored);
                    }
                }
                break;
            }

            case TransformOperatorType::CLAVE_SLIP: {
                static constexpr int32_t TRESILLO_OFFSETS[8] = {0, 0, 120, -120, 0, 120, -120, 0};
                for (auto n : notes) {
                    if (masks.lockRhythm || (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar))) {
                        result.push_back(n);
                        continue;
                    }

                    int64_t beatIdx = (n.startOffsetTicks / context.ticksPerBeat) % 8;
                    int32_t shiftTicks = static_cast<int32_t>(TRESILLO_OFFSETS[beatIdx] * recipe.intensity);

                    n.startOffsetTicks = std::max<int64_t>(0, n.startOffsetTicks + shiftTicks);
                    if (shiftTicks != 0 && !masks.lockVelocities) {
                        n.velocity = std::clamp(n.velocity * 1.20f, 0.20f, 1.0f);
                    }
                    result.push_back(n);
                }
                break;
            }

            case TransformOperatorType::MARKOV_DRIFT: {
                result = notes;
                std::uniform_real_distribution<float> probDist(0.0f, 1.0f);
                std::normal_distribution<float> stepDist(0.0f, recipe.intensity * 2.5f);

                int32_t prevPitch = -1;
                int32_t momentumDirection = 0;

                for (auto& n : result) {
                    if (masks.lockPitches) continue;
                    if (masks.lockBassNotes && n.pitch < 48) continue;
                    if (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar)) continue;

                    if (probDist(rng) <= recipe.intensity) {
                        int32_t step = static_cast<int32_t>(std::round(stepDist(rng)));
                        if (momentumDirection > 2) step = -std::abs(step);
                        else if (momentumDirection < -2) step = std::abs(step);

                        int32_t targetPitch = std::clamp(n.pitch + step, 24, 108);
                        n.pitch = Music::solveVoiceLeading(prevPitch, targetPitch, context.rootKey, context.scaleIntervalMask, 0.85f);
                        
                        if (prevPitch > 0) {
                            if (n.pitch > prevPitch) momentumDirection++;
                            else if (n.pitch < prevPitch) momentumDirection--;
                            else momentumDirection = 0;
                        }
                    }
                    prevPitch = n.pitch;
                }
                break;
            }

            case TransformOperatorType::MODAL_INVERSION: {
                result = notes;
                int32_t axisPitch = static_cast<int32_t>(recipe.param1 > 0.0f ? recipe.param1 : 60.0f);
                for (auto& n : result) {
                    if (masks.lockPitches) continue;
                    if (masks.lockBassNotes && n.pitch < 48) continue;
                    if (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar)) continue;
                    n.pitch = Music::invertModalPitch(n.pitch, axisPitch, context.rootKey, context.scaleIntervalMask);
                }
                break;
            }

            case TransformOperatorType::SCALE_CONSTRAIN: {
                result = notes;
                for (auto& n : result) {
                    if (masks.lockPitches) continue;
                    if (masks.lockBassNotes && n.pitch < 48) continue;
                    if (masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar)) continue;
                    n.pitch = Music::snapPitchToScale(n.pitch, context.rootKey, context.scaleIntervalMask);
                }
                break;
            }

            case TransformOperatorType::GOLDEN_PHRASE_ARC: {
                result = notes;
                if (result.empty()) break;
                int64_t startTick = result.front().startOffsetTicks;
                int64_t endTick = result.back().startOffsetTicks + result.back().lengthTicks;

                for (auto& n : result) {
                    if (masks.lockVelocities) continue;
                    float arcFactor = calculatePhraseArcGain(n.startOffsetTicks, startTick, endTick);
                    n.velocity = std::clamp(n.velocity * arcFactor, 0.15f, 1.0f);
                }
                break;
            }

            case TransformOperatorType::HUMANIZE_GROOVE: {
                result = notes;
                std::normal_distribution<float> timeJitter(0.0f, recipe.intensity * 18.0f);
                std::normal_distribution<float> velJitter(0.0f, recipe.intensity * 0.12f);

                for (auto& n : result) {
                    if (!masks.lockRhythm && !(masks.lockDownbeats && isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar))) {
                        int64_t delta = static_cast<int64_t>(std::round(timeJitter(rng)));
                        n.startOffsetTicks = std::max<int64_t>(0, n.startOffsetTicks + delta);
                    }
                    if (!masks.lockVelocities) {
                        n.velocity = std::clamp(n.velocity + velJitter(rng), 0.10f, 1.0f);
                    }
                }
                break;
            }

            case TransformOperatorType::SCHENKER_LEAD_TOWARD: {
                result = notes;
                for (auto& n : result) {
                    if (masks.lockPitches) continue;
                    if (masks.lockBassNotes && n.pitch < 48) continue;
                    if (isMetricDownbeat(n.startOffsetTicks, context.ticksPerBeat, context.beatsPerBar) || recipe.intensity > 0.5f) {
                        n.pitch = Music::resolveTendencyTone(n.pitch, context.rootKey, context.scaleIntervalMask);
                    }
                }
                break;
            }

            case TransformOperatorType::BARTOK_PITCH_WEDGE: {
                result = notes;
                int32_t axisPitch = static_cast<int32_t>(recipe.param1 > 0.0f ? recipe.param1 : (notes.empty() ? 60 : notes.front().pitch));
                bool expanding = (recipe.param2 >= 0.0f);

                for (size_t i = 0; i < result.size(); ++i) {
                    if (masks.lockPitches) continue;
                    if (masks.lockBassNotes && result[i].pitch < 48) continue;
                    result[i].pitch = Music::calculateBartokStep(axisPitch, static_cast<int32_t>(i), expanding, context.rootKey, context.scaleIntervalMask);
                }
                break;
            }

            case TransformOperatorType::COMPOUND_POLY_WEAVE: {
                result = notes;
                if (notes.empty()) break;

                std::vector<NoteEvent> woven;
                woven.reserve(notes.size() * 2);

                for (const auto& n : notes) {
                    NoteEvent bass = n;
                    bass.pitch = Music::snapPitchToScale(std::clamp(n.pitch - 24, 24, 48), context.rootKey, context.scaleIntervalMask);
                    bass.lengthTicks = std::max<int64_t>(30, n.lengthTicks / 2);
                    if (!masks.lockVelocities) bass.velocity = std::clamp(n.velocity * 0.90f, 0.20f, 1.0f);

                    NoteEvent melody = n;
                    melody.pitch = Music::snapPitchToScale(std::clamp(n.pitch + 12, 60, 84), context.rootKey, context.scaleIntervalMask);
                    melody.startOffsetTicks = n.startOffsetTicks + (n.lengthTicks / 2);
                    melody.lengthTicks = std::max<int64_t>(30, n.lengthTicks / 2);
                    if (!masks.lockVelocities) melody.velocity = std::clamp(n.velocity * 1.05f, 0.20f, 1.0f);

                    woven.push_back(bass);
                    woven.push_back(melody);
                }
                result = std::move(woven);
                break;
            }

            case TransformOperatorType::DIATONIC_CASCADE_RUN: {
                result.reserve(notes.size() * 4);
                result.clear();
                for (size_t i = 0; i < notes.size(); ++i) {
                    const auto& curr = notes[i];
                    result.push_back(curr);

                    if (i < notes.size() - 1 && !masks.lockRhythm) {
                        const auto& next = notes[i + 1];
                        int32_t pitchDiff = next.pitch - curr.pitch;
                        int64_t timeGap = next.startOffsetTicks - (curr.startOffsetTicks + curr.lengthTicks);

                        if (std::abs(pitchDiff) >= 4 || timeGap >= context.ticksPerBeat) {
                            int32_t steps = 3;
                            int64_t runStart = curr.startOffsetTicks + curr.lengthTicks;
                            int64_t stepTicks = std::max<int64_t>(30, (next.startOffsetTicks - runStart) / (steps + 1));

                            for (int32_t s = 1; s <= steps; ++s) {
                                int32_t cascadePitch = Music::shiftDiatonicDegree(curr.pitch, (pitchDiff > 0 ? s : -s), context.rootKey, context.scaleIntervalMask);
                                NoteEvent pass;
                                pass.pitch = cascadePitch;
                                pass.startOffsetTicks = runStart + ((s - 1) * stepTicks);
                                pass.lengthTicks = static_cast<int64_t>(stepTicks * 0.85f);
                                pass.velocity = std::clamp(curr.velocity * 0.75f, 0.20f, 1.0f);
                                pass.isSelected = true;
                                result.push_back(pass);
                            }
                        }
                    }
                }
                break;
            }

            case TransformOperatorType::CHORD_DROP_VOICING: {
                result = notes;
                if (notes.size() < 2) break;

                int32_t style = static_cast<int32_t>(recipe.param1);

                std::vector<std::vector<size_t>> chordGroups;
                for (size_t i = 0; i < result.size(); ++i) {
                    bool grouped = false;
                    for (auto& group : chordGroups) {
                        if (std::abs(result[group.front()].startOffsetTicks - result[i].startOffsetTicks) <= 20) {
                            group.push_back(i);
                            grouped = true;
                            break;
                        }
                    }
                    if (!grouped) {
                        chordGroups.push_back({i});
                    }
                }

                for (const auto& group : chordGroups) {
                    if (group.size() >= 3) {
                        std::vector<size_t> sortedGroup = group;
                        std::sort(sortedGroup.begin(), sortedGroup.end(), [&](size_t a, size_t b) {
                            return result[a].pitch > result[b].pitch;
                        });

                        if (style == 0 && sortedGroup.size() >= 2) {
                            size_t targetIdx = sortedGroup[1];
                            if (!masks.lockPitches) {
                                result[targetIdx].pitch = Music::snapPitchToScale(result[targetIdx].pitch - 12, context.rootKey, context.scaleIntervalMask);
                            }
                        } else if (style == 1 && sortedGroup.size() >= 3) {
                            size_t targetIdx = sortedGroup[2];
                            if (!masks.lockPitches) {
                                result[targetIdx].pitch = Music::snapPitchToScale(result[targetIdx].pitch - 12, context.rootKey, context.scaleIntervalMask);
                            }
                        } else if (style == 2) {
                            for (size_t k = 1; k < sortedGroup.size(); ++k) {
                                size_t idx = sortedGroup[k];
                                result[idx].pitch = Music::shiftDiatonicDegree(result[sortedGroup[0]].pitch, -static_cast<int32_t>(k * 3), context.rootKey, context.scaleIntervalMask);
                            }
                        }
                    }
                }
                break;
            }

            case TransformOperatorType::CONTRARY_COUNTERPOINT: {
                result = notes;
                if (notes.empty()) break;

                std::vector<NoteEvent> counterVoice;
                counterVoice.reserve(notes.size());

                int32_t prevLeadPitch = -1;
                int32_t prevCounterPitch = -1;

                for (const auto& n : notes) {
                    int32_t leadDelta = (prevLeadPitch > 0) ? (n.pitch - prevLeadPitch) : 0;
                    int32_t cPitch = Music::solveContraryMotion(leadDelta, prevCounterPitch, n.pitch, context.rootKey, context.scaleIntervalMask);

                    NoteEvent cNote = n;
                    cNote.pitch = cPitch;
                    if (!masks.lockVelocities) {
                        cNote.velocity = std::clamp(n.velocity * 0.85f, 0.20f, 1.0f);
                    }
                    cNote.isSelected = true;
                    counterVoice.push_back(cNote);

                    prevLeadPitch = n.pitch;
                    prevCounterPitch = cPitch;
                }

                result.insert(result.end(), counterVoice.begin(), counterVoice.end());
                break;
            }

            case TransformOperatorType::SUB_BASS_EXTRACTOR: {
                result = notes;
                if (notes.empty()) break;

                int32_t rhythmStyle = static_cast<int32_t>(recipe.param1);
                std::vector<NoteEvent> bassEvents;

                int64_t currentBeat = -1;
                int32_t lowestPitch = 127;
                int64_t beatStart = 0;
                int64_t beatLen = context.ticksPerBeat;

                for (const auto& n : notes) {
                    int64_t bIdx = n.startOffsetTicks / context.ticksPerBeat;
                    if (bIdx != currentBeat) {
                        if (currentBeat >= 0) {
                            int32_t subPitch = Music::snapPitchToScale(std::clamp((lowestPitch % 12) + 24, 24, 48), context.rootKey, context.scaleIntervalMask);
                            if (rhythmStyle == 0) {
                                NoteEvent b;
                                b.pitch = subPitch;
                                b.startOffsetTicks = beatStart;
                                b.lengthTicks = beatLen;
                                b.velocity = 0.95f;
                                b.isSelected = true;
                                bassEvents.push_back(b);
                            } else if (rhythmStyle == 1) {
                                for (int k = 0; k < 2; ++k) {
                                    NoteEvent b;
                                    b.pitch = subPitch;
                                    b.startOffsetTicks = beatStart + (k * (beatLen / 2));
                                    b.lengthTicks = static_cast<int64_t>((beatLen / 2) * 0.85f);
                                    b.velocity = (k == 0 ? 0.95f : 0.75f);
                                    b.isSelected = true;
                                    bassEvents.push_back(b);
                                }
                            } else {
                                NoteEvent b;
                                b.pitch = subPitch;
                                b.startOffsetTicks = beatStart + (beatLen / 2);
                                b.lengthTicks = static_cast<int64_t>((beatLen / 2) * 0.85f);
                                b.velocity = 1.0f;
                                b.isSelected = true;
                                bassEvents.push_back(b);
                            }
                        }
                        currentBeat = bIdx;
                        lowestPitch = n.pitch;
                        beatStart = n.startOffsetTicks;
                        beatLen = n.lengthTicks;
                    } else {
                        lowestPitch = std::min(lowestPitch, n.pitch);
                    }
                }

                if (currentBeat >= 0) {
                    int32_t subPitch = Music::snapPitchToScale(std::clamp((lowestPitch % 12) + 24, 24, 48), context.rootKey, context.scaleIntervalMask);
                    NoteEvent b;
                    b.pitch = subPitch;
                    b.startOffsetTicks = beatStart;
                    b.lengthTicks = beatLen;
                    b.velocity = 0.95f;
                    b.isSelected = true;
                    bassEvents.push_back(b);
                }

                result.insert(result.end(), bassEvents.begin(), bassEvents.end());
                break;
            }

            // --- 19. ACOUSTIC GUITAR STRUM PHYSICS ---
            case TransformOperatorType::GUITAR_STRUM_PHYSICS: {
                result = notes;
                if (notes.size() < 2) break;

                bool downStrum = (recipe.param1 >= 0.0f);
                int64_t spreadTicks = static_cast<int64_t>(recipe.param2 > 0.0f ? recipe.param2 : (20 + recipe.intensity * 30.0f));

                std::vector<std::vector<size_t>> chordGroups;
                for (size_t i = 0; i < result.size(); ++i) {
                    bool grouped = false;
                    for (auto& group : chordGroups) {
                        if (std::abs(result[group.front()].startOffsetTicks - result[i].startOffsetTicks) <= 25) {
                            group.push_back(i);
                            grouped = true;
                            break;
                        }
                    }
                    if (!grouped) chordGroups.push_back({i});
                }

                for (const auto& group : chordGroups) {
                    if (group.size() >= 2) {
                        std::vector<size_t> sortedGroup = group;
                        std::sort(sortedGroup.begin(), sortedGroup.end(), [&](size_t a, size_t b) {
                            return downStrum ? (result[a].pitch < result[b].pitch) : (result[a].pitch > result[b].pitch);
                        });

                        for (size_t k = 0; k < sortedGroup.size(); ++k) {
                            size_t idx = sortedGroup[k];
                            if (!masks.lockRhythm) {
                                result[idx].startOffsetTicks += (k * spreadTicks);
                            }
                            if (!masks.lockVelocities) {
                                float taper = 1.0f - (static_cast<float>(k) * 0.07f);
                                result[idx].velocity = std::clamp(result[idx].velocity * taper, 0.20f, 1.0f);
                            }
                        }
                    }
                }
                break;
            }

            // --- 20. MAQAM & BLUES MICROTONAL INFLECTOR ---
            case TransformOperatorType::MAQAM_MICROTONAL_BEND: {
                result.reserve(notes.size() * 2);
                std::uniform_real_distribution<float> probDist(0.0f, 1.0f);
                for (const auto& n : notes) {
                    int32_t chroma = (n.pitch - context.rootKey) % 12;
                    if (chroma < 0) chroma += 12;

                    // Apply to 3rd (3/4) or 5th/b5 (6/7)
                    if ((chroma == 3 || chroma == 4 || chroma == 6 || chroma == 7) && probDist(rng) <= recipe.intensity && n.lengthTicks >= 240) {
                        int64_t graceLen = std::min<int64_t>(50, n.lengthTicks / 4);

                        // Inflection Grace Note (Slide from half-step below)
                        NoteEvent bendGrace = n;
                        bendGrace.pitch = std::max(0, n.pitch - 1);
                        bendGrace.startOffsetTicks = std::max<int64_t>(0, n.startOffsetTicks - graceLen);
                        bendGrace.lengthTicks = static_cast<int64_t>(graceLen * 0.8f);
                        if (!masks.lockVelocities) bendGrace.velocity = std::clamp(n.velocity * 0.75f, 0.20f, 1.0f);

                        NoteEvent target = n;
                        if (!masks.lockVelocities) target.velocity = std::clamp(n.velocity * 1.10f, 0.20f, 1.0f);

                        result.push_back(bendGrace);
                        result.push_back(target);
                    } else {
                        result.push_back(n);
                    }
                }
                break;
            }

            // --- 21. PARABOLIC VELOCITY DOME & PHRASE SWELL ---
            case TransformOperatorType::PARABOLIC_VELOCITY_DOME: {
                result = notes;
                if (result.empty()) break;

                int64_t phraseStart = result.front().startOffsetTicks;
                int64_t phraseEnd = result.back().startOffsetTicks + result.back().lengthTicks;
                int64_t totalSpan = std::max<int64_t>(1, phraseEnd - phraseStart);

                float minV = recipe.param1 > 0.0f ? (recipe.param1 / 100.0f) : 0.40f;
                float maxV = recipe.param2 > 0.0f ? (recipe.param2 / 100.0f) : 0.95f;

                for (auto& n : result) {
                    if (masks.lockVelocities) continue;
                    float tau = std::clamp(static_cast<float>(n.startOffsetTicks - phraseStart) / static_cast<float>(totalSpan), 0.0f, 1.0f);
                    // Parabolic dome curve: 4 * tau * (1 - tau)
                    float dome = 4.0f * tau * (1.0f - tau);
                    float targetVel = minV + (maxV - minV) * dome;
                    n.velocity = std::clamp(targetVel, 0.10f, 1.0f);
                }
                break;
            }

            default:
                result = notes;
                break;
        }

        return result;
    }

private:
    static void cleanDuplicatesAndLimitDensity(std::vector<NoteEvent>& notes, const MusicalContext& context) {
        if (notes.empty()) return;

        std::sort(notes.begin(), notes.end(), [](const NoteEvent& a, const NoteEvent& b) {
            if (a.startOffsetTicks != b.startOffsetTicks) return a.startOffsetTicks < b.startOffsetTicks;
            return a.pitch < b.pitch;
        });

        std::vector<NoteEvent> uniqueNotes;
        uniqueNotes.reserve(notes.size());

        for (const auto& n : notes) {
            if (!uniqueNotes.empty()) {
                const auto& prev = uniqueNotes.back();
                if (prev.pitch == n.pitch && std::abs(prev.startOffsetTicks - n.startOffsetTicks) < 15) {
                    continue;
                }
            }
            uniqueNotes.push_back(n);
        }

        if (context.maxPolyphonyPerBeat > 0 && uniqueNotes.size() > 1) {
            std::vector<NoteEvent> cappedNotes;
            cappedNotes.reserve(uniqueNotes.size());

            int64_t currentBeatWindow = -1;
            int32_t countInBeat = 0;

            for (const auto& n : uniqueNotes) {
                int64_t beatIdx = n.startOffsetTicks / context.ticksPerBeat;
                if (beatIdx != currentBeatWindow) {
                    currentBeatWindow = beatIdx;
                    countInBeat = 0;
                }

                if (countInBeat < context.maxPolyphonyPerBeat) {
                    cappedNotes.push_back(n);
                    countInBeat++;
                }
            }
            notes = std::move(cappedNotes);
        } else {
            notes = std::move(uniqueNotes);
        }
    }
};

} // namespace Cobass::Transform
