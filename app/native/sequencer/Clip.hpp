#pragma once
#include <string>
#include <vector>
#include <cstdint>
#include <algorithm>

struct MidiNoteEvent {
    int32_t note;
    float velocity;
    int64_t startOffsetTicks;
    int64_t lengthTicks;
};

class Clip {
public:
    Clip(int32_t id, int32_t trackId, int64_t startTick, int64_t lengthTicks, const std::string& name)
        : id_(id), trackId_(trackId), startTick_(startTick), lengthTicks_(lengthTicks), name_(name) {}

    int32_t getId() const noexcept { return id_; }
    int32_t getTrackId() const noexcept { return trackId_; }
    void setTrackId(int32_t trackId) noexcept { trackId_ = trackId; }

    int64_t getStartTick() const noexcept { return startTick_; }
    void setStartTick(int64_t tick) noexcept { startTick_ = tick; }

    int64_t getLengthTicks() const noexcept { return lengthTicks_; }
    void setLengthTicks(int64_t len) noexcept { lengthTicks_ = len; }

    int64_t getEndTick() const noexcept { return startTick_ + lengthTicks_; }

    const std::string& getName() const noexcept { return name_; }
    void setName(const std::string& name) { name_ = name; }

    void addNote(int32_t note, float velocity, int64_t startOffset, int64_t length) {
        notes_.push_back({note, velocity, startOffset, length});
    }

    void clearNotes() noexcept { notes_.clear(); }
    const std::vector<MidiNoteEvent>& getNotes() const noexcept { return notes_; }

private:
    int32_t id_;
    int32_t trackId_;
    int64_t startTick_;
    int64_t lengthTicks_;
    std::string name_;
    std::vector<MidiNoteEvent> notes_;
};
