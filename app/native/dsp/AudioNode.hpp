#pragma once
#include <cstdint>

class AudioNode {
public:
    virtual ~AudioNode() = default;
    virtual void reset(float sampleRate) = 0;
    virtual void process(const float* inBuffer, float* outBuffer, int32_t numFrames) = 0;
    virtual void setParameter(uint32_t paramId, float value) = 0;
    virtual float getParameter(uint32_t paramId) const = 0;
};
