#pragma once
#include <array>
#include <memory>
#include <mutex>
#include "PluginInstance.hpp"

class PluginChain {
public:
    static constexpr size_t MAX_SLOTS = 8;

    struct Slot {
        std::unique_ptr<PluginInstance> instance;
        bool bypassed = false;
        float mix = 1.0f; // Dry/Wet mix (0.0 to 1.0)
    };

    PluginChain() {
        dryBuffer_.assign(8192, 0.0f);
        wetBuffer_.assign(8192, 0.0f);
    }

    void setSampleRate(float sampleRate) {
        sampleRate_ = sampleRate;
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& slot : slots_) {
            if (slot.instance) slot.instance->reset(sampleRate);
        }
    }

    bool loadPlugin(size_t slotIndex, std::unique_ptr<PluginInstance> plugin) {
        if (slotIndex >= MAX_SLOTS) return false;
        std::lock_guard<std::mutex> lock(mutex_);
        if (plugin) plugin->reset(sampleRate_);
        slots_[slotIndex].instance = std::move(plugin);
        slots_[slotIndex].bypassed = false;
        slots_[slotIndex].mix = 1.0f;
        return true;
    }

    void removePlugin(size_t slotIndex) {
        if (slotIndex >= MAX_SLOTS) return;
        std::lock_guard<std::mutex> lock(mutex_);
        slots_[slotIndex].instance.reset();
        slots_[slotIndex].bypassed = false;
    }

    void setBypass(size_t slotIndex, bool bypass) {
        if (slotIndex >= MAX_SLOTS) return;
        std::lock_guard<std::mutex> lock(mutex_);
        slots_[slotIndex].bypassed = bypass;
    }

    bool isBypassed(size_t slotIndex) const {
        if (slotIndex >= MAX_SLOTS) return true;
        std::lock_guard<std::mutex> lock(mutex_);
        return slots_[slotIndex].bypassed;
    }

    PluginInstance* getPlugin(size_t slotIndex) {
        if (slotIndex >= MAX_SLOTS) return nullptr;
        std::lock_guard<std::mutex> lock(mutex_);
        return slots_[slotIndex].instance.get();
    }

    void moveSlot(size_t fromSlot, size_t toSlot) {
        if (fromSlot >= MAX_SLOTS || toSlot >= MAX_SLOTS || fromSlot == toSlot) return;
        std::lock_guard<std::mutex> lock(mutex_);
        std::swap(slots_[fromSlot], slots_[toSlot]);
    }

    void process(float* buffer, int32_t numFrames) {
        if (!buffer || numFrames <= 0) return;

        const size_t requiredSize = static_cast<size_t>(numFrames * 2);
        if (dryBuffer_.size() < requiredSize) {
            dryBuffer_.resize(requiredSize, 0.0f);
            wetBuffer_.resize(requiredSize, 0.0f);
        }

        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& slot : slots_) {
            if (!slot.instance || slot.bypassed) continue;

            if (slot.mix < 0.999f) {
                // Save dry signal for wet/dry blend
                std::copy_n(buffer, requiredSize, dryBuffer_.data());
            }

            slot.instance->process(buffer, buffer, numFrames);

            if (slot.mix < 0.999f) {
                const float wet = slot.mix;
                const float dry = 1.0f - wet;
                for (size_t i = 0; i < requiredSize; ++i) {
                    buffer[i] = dryBuffer_[i] * dry + buffer[i] * wet;
                }
            }
        }
    }

private:
    float sampleRate_ = 48000.0f;
    mutable std::mutex mutex_;
    std::array<Slot, MAX_SLOTS> slots_;
    std::vector<float> dryBuffer_;
    std::vector<float> wetBuffer_;
};
