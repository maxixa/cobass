#pragma once
#include <vector>
#include <memory>
#include <algorithm>
#include "Transport.hpp"
#include "Clip.hpp"
#include "../dsp/Mixer.hpp"

class Sequencer {
public:
    static constexpr size_t MAX_CLIPS = 128;

    Sequencer() {
        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            clips_[i].store(nullptr, std::memory_order_relaxed);
        }
    }

    ~Sequencer() {
        clearAllClips();
    }

    void clearAllClips() {
        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            Clip* c = clips_[i].exchange(nullptr);
            delete c;
        }
        nextClipId_.store(1);
        lastTick_ = 0;
    }

    Transport& getTransport() noexcept { return transport_; }
    const Transport& getTransport() const noexcept { return transport_; }

    int32_t addClip(int32_t trackId, int64_t startTick, int64_t lengthTicks, const std::string& name) {
        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            Clip* expected = nullptr;
            if (clips_[i].load(std::memory_order_relaxed) == nullptr) {
                const int32_t id = nextClipId_++;
                auto* clip = new Clip(id, trackId, startTick, lengthTicks, name);
                if (clips_[i].compare_exchange_strong(expected, clip, std::memory_order_release)) {
                    return id;
                }
                delete clip;
            }
        }
        return -1;
    }

    bool removeClip(int32_t clipId) {
        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            Clip* c = clips_[i].load(std::memory_order_acquire);
            if (c && c->getId() == clipId) {
                clips_[i].store(nullptr, std::memory_order_release);
                delete c;
                return true;
            }
        }
        return false;
    }

    Clip* getClip(int32_t clipId) {
        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            Clip* c = clips_[i].load(std::memory_order_acquire);
            if (c && c->getId() == clipId) return c;
        }
        return nullptr;
    }

    void moveClip(int32_t clipId, int32_t newTrackId, int64_t newStartTick) {
        Clip* c = getClip(clipId);
        if (c) {
            c->setTrackId(newTrackId);
            c->setStartTick(newStartTick);
        }
    }

    void resizeClip(int32_t clipId, int64_t newLengthTicks) {
        Clip* c = getClip(clipId);
        if (c) {
            c->setLengthTicks(std::max<int64_t>(Transport::PPQ / 4, newLengthTicks));
        }
    }

    void clearClipNotes(int32_t clipId) {
        Clip* c = getClip(clipId);
        if (c) c->clearNotes();
    }

    void addNoteToClip(int32_t clipId, int32_t note, float vel, int64_t startOffset, int64_t len) {
        Clip* c = getClip(clipId);
        if (c) c->addNote(note, vel, startOffset, len);
    }

    void processAudioBlock(Mixer& mixer, int32_t numFrames) {
        if (!transport_.isPlaying()) {
            lastTick_ = transport_.getCurrentTick();
            return;
        }

        const int64_t startTick = lastTick_;
        const int64_t endTick = transport_.advance(numFrames);
        lastTick_ = endTick;

        const bool loopWrapped = (endTick < startTick);
        // Advance Step Sequencer Tracks
        for (size_t t = 0; t < Mixer::MAX_TRACKS; ++t) {
            Track* track = mixer.getTrack(static_cast<int32_t>(t));
            if (track && track->getType() == TrackType::StepSequencer) {
                static_cast<StepSequencerTrack*>(track)->advancePlayback(startTick, endTick, loopWrapped);
            }
        }

        if (loopWrapped) {
            for (size_t t = 0; t < Mixer::MAX_TRACKS; ++t) {
                Track* track = mixer.getTrack((int32_t)t);
                if (track) track->allNotesOff();
            }
        }

        for (size_t i = 0; i < MAX_CLIPS; ++i) {
            Clip* clip = clips_[i].load(std::memory_order_acquire);
            if (!clip) continue;

            Track* track = mixer.getTrack(clip->getTrackId());
            if (!track) continue;

            if (track->getType() == TrackType::Audio) {
                if (!loopWrapped) {
                    if (startTick <= clip->getStartTick() && endTick > clip->getStartTick()) {
                        track->noteOn(60, 1.0f);
                    }
                } else {
                    if (startTick <= clip->getStartTick() || endTick > clip->getStartTick()) {
                        track->noteOn(60, 1.0f);
                    }
                }
                continue;
            }

            const int64_t clipStart = clip->getStartTick();

            for (const auto& event : clip->getNotes()) {
                const int64_t noteAbsoluteStart = clipStart + event.startOffsetTicks;
                const int64_t noteAbsoluteEnd = noteAbsoluteStart + event.lengthTicks;

                if (!loopWrapped) {
                    if (startTick <= noteAbsoluteStart && endTick > noteAbsoluteStart) {
                        track->noteOn(event.note, event.velocity);
                    }
                    if (startTick <= noteAbsoluteEnd && endTick > noteAbsoluteEnd) {
                        track->noteOff(event.note);
                    }
                } else {
                    if (startTick <= noteAbsoluteEnd) {
                        track->noteOff(event.note);
                    }
                    if (endTick > noteAbsoluteStart) {
                        track->noteOn(event.note, event.velocity);
                    }
                }
            }
        }
    }

private:
    Transport transport_;
    std::atomic<int32_t> nextClipId_{1};
    std::array<std::atomic<Clip*>, MAX_CLIPS> clips_;
    int64_t lastTick_ = 0;
};
