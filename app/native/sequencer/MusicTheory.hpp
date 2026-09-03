#pragma once
#include <cstdint>
#include <array>
#include <vector>
#include <cmath>
#include <algorithm>
#include <string_view>

namespace Cobass::Music {

enum class ScaleType : uint32_t {
    Chromatic = 0,
    Major = 1,              // Ionian
    NaturalMinor = 2,       // Aeolian
    Dorian = 3,
    Phrygian = 4,
    Lydian = 5,
    Mixolydian = 6,
    Locrian = 7,
    HarmonicMinor = 8,
    MelodicMinor = 9,
    MinorPentatonic = 10,
    MajorPentatonic = 11,
    Blues = 12,
    BebopDominant = 13
};

struct ScaleDescriptor {
    ScaleType type;
    std::string_view name;
    uint32_t intervalMask; // 12-bit bitmask (bit 0 = root, bit 1 = m2, ..., bit 11 = M7)
    uint32_t degreeCount;
};

inline constexpr ScaleDescriptor SCALE_TABLE[] = {
    {ScaleType::Chromatic,        "Chromatic",         0b111111111111, 12},
    {ScaleType::Major,            "Major (Ionian)",    0b101010110101, 7},
    {ScaleType::NaturalMinor,     "Natural Minor",     0b010110101101, 7},
    {ScaleType::Dorian,           "Dorian Minor",      0b010101101101, 7},
    {ScaleType::Phrygian,         "Phrygian",          0b010110101011, 7},
    {ScaleType::Lydian,           "Lydian",            0b101010101101, 7},
    {ScaleType::Mixolydian,       "Mixolydian",        0b011010110101, 7},
    {ScaleType::Locrian,          "Locrian",           0b010110101011, 7},
    {ScaleType::HarmonicMinor,    "Harmonic Minor",    0b100110101101, 7},
    {ScaleType::MelodicMinor,     "Melodic Minor",     0b101001101101, 7},
    {ScaleType::MinorPentatonic,  "Minor Pentatonic",  0b010010101001, 5},
    {ScaleType::MajorPentatonic,  "Major Pentatonic",  0b001010100101, 5},
    {ScaleType::Blues,            "Blues Scale",       0b010010111001, 6},
    {ScaleType::BebopDominant,    "Bebop Dominant",    0b111010110101, 8}
};

inline constexpr const ScaleDescriptor& getScaleDescriptor(ScaleType type) noexcept {
    const size_t idx = static_cast<size_t>(type);
    if (idx < std::size(SCALE_TABLE)) {
        return SCALE_TABLE[idx];
    }
    return SCALE_TABLE[0];
}

inline constexpr bool isPitchInScale(int32_t midiPitch, int32_t rootKey, uint32_t scaleMask) noexcept {
    if (scaleMask == 0b111111111111) return true;
    int32_t chroma = (midiPitch - rootKey) % 12;
    if (chroma < 0) chroma += 12;
    return (scaleMask & (1u << chroma)) != 0;
}

inline int32_t snapPitchToScale(int32_t rawPitch, int32_t rootKey, uint32_t scaleMask) noexcept {
    if (rawPitch < 0 || rawPitch > 127) {
        return std::clamp(rawPitch, 0, 127);
    }
    if (scaleMask == 0b111111111111 || isPitchInScale(rawPitch, rootKey, scaleMask)) {
        return rawPitch;
    }

    int32_t bestPitch = rawPitch;
    for (int32_t delta = 1; delta <= 6; ++delta) {
        int32_t upPitch = rawPitch + delta;
        int32_t downPitch = rawPitch - delta;

        if (upPitch <= 127 && isPitchInScale(upPitch, rootKey, scaleMask)) {
            bestPitch = upPitch;
            break;
        }
        if (downPitch >= 0 && isPitchInScale(downPitch, rootKey, scaleMask)) {
            bestPitch = downPitch;
            break;
        }
    }
    return std::clamp(bestPitch, 0, 127);
}

inline int32_t invertModalPitch(int32_t rawPitch, int32_t axisPitch, int32_t rootKey, uint32_t scaleMask) noexcept {
    int32_t rawInverted = 2 * axisPitch - rawPitch;
    return snapPitchToScale(rawInverted, rootKey, scaleMask);
}

inline int32_t solveVoiceLeading(int32_t previousPitch, int32_t targetPitch, int32_t rootKey, uint32_t scaleMask, float parsimoniousWeight) noexcept {
    int32_t snappedTarget = snapPitchToScale(targetPitch, rootKey, scaleMask);
    if (previousPitch < 0 || parsimoniousWeight <= 0.01f) {
        return snappedTarget;
    }

    int32_t rawInterval = snappedTarget - previousPitch;
    int32_t absInterval = std::abs(rawInterval);

    if (absInterval >= 12 && parsimoniousWeight > 0.4f) {
        int32_t octaveShift = (rawInterval > 0) ? -12 : 12;
        int32_t closerPitch = snappedTarget + octaveShift;
        if (closerPitch >= 0 && closerPitch <= 127 && isPitchInScale(closerPitch, rootKey, scaleMask)) {
            snappedTarget = closerPitch;
        }
    }

    return std::clamp(snappedTarget, 0, 127);
}

inline int32_t shiftDiatonicDegree(int32_t basePitch, int32_t degreeShift, int32_t rootKey, uint32_t scaleMask) noexcept {
    int32_t pitch = snapPitchToScale(basePitch, rootKey, scaleMask);
    if (degreeShift == 0) return pitch;

    int32_t direction = (degreeShift > 0) ? 1 : -1;
    int32_t remainingSteps = std::abs(degreeShift);

    while (remainingSteps > 0) {
        pitch += direction;
        if (pitch < 0 || pitch > 127) break;
        if (isPitchInScale(pitch, rootKey, scaleMask)) {
            remainingSteps--;
        }
    }

    return std::clamp(pitch, 0, 127);
}

inline void getEnclosureTones(int32_t targetPitch, int32_t rootKey, uint32_t scaleMask, int32_t& upperDiatonic, int32_t& lowerChromatic) noexcept {
    int32_t snappedTarget = snapPitchToScale(targetPitch, rootKey, scaleMask);
    upperDiatonic = shiftDiatonicDegree(snappedTarget, 1, rootKey, scaleMask);
    lowerChromatic = std::max(0, snappedTarget - 1);
}

inline int32_t resolveTendencyTone(int32_t pitch, int32_t rootKey, uint32_t scaleMask) noexcept {
    int32_t chroma = (pitch - rootKey) % 12;
    if (chroma < 0) chroma += 12;

    int32_t resolvedPitch = pitch;
    if (chroma == 11) {
        resolvedPitch = pitch + 1;
    } else if (chroma == 5) {
        resolvedPitch = pitch - 1;
    } else if (chroma == 9) {
        resolvedPitch = pitch - 2;
    }

    return snapPitchToScale(resolvedPitch, rootKey, scaleMask);
}

inline int32_t calculateBartokStep(int32_t axisPitch, int32_t index, bool expanding, int32_t rootKey, uint32_t scaleMask) noexcept {
    int32_t stepMagnitude = (index + 1) / 2;
    int32_t sign = (index % 2 == 1) ? 1 : -1;
    if (!expanding) sign = -sign;

    int32_t degreeShift = sign * stepMagnitude;
    return shiftDiatonicDegree(axisPitch, degreeShift, rootKey, scaleMask);
}

// Contrary Motion Counterpoint Solver (Moves opposite to lead melody into consonant 3rds/6ths)
inline int32_t solveContraryMotion(int32_t leadDelta, int32_t prevCounterPitch, int32_t currentLeadPitch, int32_t rootKey, uint32_t scaleMask) noexcept {
    int32_t contraryDirection = (leadDelta >= 0) ? -1 : 1;
    int32_t targetDegreeShift = contraryDirection * (std::abs(leadDelta) >= 3 ? 2 : 1);

    int32_t candidatePitch = shiftDiatonicDegree(prevCounterPitch > 0 ? prevCounterPitch : (currentLeadPitch - 12), targetDegreeShift, rootKey, scaleMask);

    // Prevent octave unisons and semitone collisions
    int32_t interval = std::abs(currentLeadPitch - candidatePitch) % 12;
    if (interval == 0 || interval == 1 || interval == 2) {
        candidatePitch = shiftDiatonicDegree(candidatePitch, contraryDirection, rootKey, scaleMask);
    }

    return std::clamp(candidatePitch, 24, 108);
}

} // namespace Cobass::Music
