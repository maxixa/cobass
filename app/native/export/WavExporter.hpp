#pragma once
#include <string>
#include <vector>
#include <fstream>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include "../dsp/Mixer.hpp"
#include "../sequencer/Sequencer.hpp"

class WavExporter {
public:
    static bool exportToWav(
        const std::string& outputPath,
        Mixer& mixer,
        Sequencer& sequencer,
        float sampleRate,
        int64_t totalTicks,
        std::atomic<float>& progressOut,
        std::atomic<bool>& cancelFlag) {

        if (totalTicks <= 0 || sampleRate <= 0.0f) return false;

        std::ofstream wavFile(outputPath, std::ios::binary);
        if (!wavFile.is_open()) return false;

        // Save original transport state
        Transport& transport = sequencer.getTransport();
        const float originalBpm = transport.getBpm();
        const int64_t originalTick = transport.getCurrentTick();
        const bool originalPlaying = transport.isPlaying();

        // Prepare sequencer for non-realtime bounce
        transport.stop();
        transport.seekToTick(0);
        transport.setSampleRate(sampleRate);
        mixer.setSampleRate(sampleRate);
        transport.play();

        const double bpm = transport.getBpm();
        const double totalSeconds = (totalTicks / (double)Transport::PPQ) * (60.0 / bpm);
        const int64_t totalFrames = static_cast<int64_t>(totalSeconds * sampleRate);

        const int32_t numChannels = 2;
        const int32_t bitsPerSample = 16;
        const int32_t bytesPerSample = bitsPerSample / 8;
        const int32_t blockAlign = numChannels * bytesPerSample;
        const int32_t byteRate = static_cast<int32_t>(sampleRate) * blockAlign;
        const int32_t dataSize = static_cast<int32_t>(totalFrames * blockAlign);
        const int32_t chunkSize = 36 + dataSize;

        // Write 44-byte RIFF/WAVE header
        wavFile.write("RIFF", 4);
        wavFile.write(reinterpret_cast<const char*>(&chunkSize), 4);
        wavFile.write("WAVE", 4);

        wavFile.write("fmt ", 4);
        const int32_t subChunk1Size = 16;
        const int16_t audioFormat = 1; // PCM
        const int16_t channels = numChannels;
        const int32_t sRate = static_cast<int32_t>(sampleRate);

        wavFile.write(reinterpret_cast<const char*>(&subChunk1Size), 4);
        wavFile.write(reinterpret_cast<const char*>(&audioFormat), 2);
        wavFile.write(reinterpret_cast<const char*>(&channels), 2);
        wavFile.write(reinterpret_cast<const char*>(&sRate), 4);
        wavFile.write(reinterpret_cast<const char*>(&byteRate), 4);
        const int16_t bAlign = static_cast<int16_t>(blockAlign);
        const int16_t bPerSample = static_cast<int16_t>(bitsPerSample);
        wavFile.write(reinterpret_cast<const char*>(&bAlign), 2);
        wavFile.write(reinterpret_cast<const char*>(&bPerSample), 2);

        wavFile.write("data", 4);
        wavFile.write(reinterpret_cast<const char*>(&dataSize), 4);

        // Fast Offline Block Rendering
        constexpr int32_t BLOCK_FRAMES = 1024;
        std::vector<float> floatBuffer(BLOCK_FRAMES * numChannels, 0.0f);
        std::vector<int16_t> pcmBuffer(BLOCK_FRAMES * numChannels, 0);

        int64_t framesRendered = 0;
        while (framesRendered < totalFrames) {
            if (cancelFlag.load(std::memory_order_relaxed)) {
                wavFile.close();
                return false;
            }

            int32_t currentBlock = static_cast<int32_t>(std::min<int64_t>(BLOCK_FRAMES, totalFrames - framesRendered));

            sequencer.processAudioBlock(mixer, currentBlock);
            mixer.renderMix(floatBuffer.data(), currentBlock);

            for (int32_t i = 0; i < currentBlock * numChannels; ++i) {
                float sample = std::clamp(floatBuffer[i], -1.0f, 1.0f);
                pcmBuffer[i] = static_cast<int16_t>(sample * 32767.0f);
            }

            wavFile.write(reinterpret_cast<const char*>(pcmBuffer.data()), currentBlock * blockAlign);
            framesRendered += currentBlock;

            progressOut.store(static_cast<float>(framesRendered) / static_cast<float>(totalFrames), std::memory_order_relaxed);
        }

        wavFile.close();

        // Restore original real-time transport state
        transport.stop();
        transport.setBpm(originalBpm);
        transport.seekToTick(originalTick);
        if (originalPlaying) transport.play();

        progressOut.store(1.0f, std::memory_order_release);
        return true;
    }
};
