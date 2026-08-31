#pragma once
#include <string>
#include <vector>
#include "../include/CobassPluginABI.h"

struct PluginParam {
    uint32_t id = 0;
    std::string name;
    std::string label;
    CobassParamType type = COBASS_PARAM_TYPE_FLOAT;
    float minValue = 0.0f;
    float maxValue = 1.0f;
    float defaultValue = 0.0f;
    float step = 0.01f;
    bool isLogarithmic = false;
    std::vector<std::string> choices;
};

struct PluginDescriptor {
    std::string pluginId;
    std::string name;
    std::string vendor;
    std::string version;
    std::string libraryPath;
    CobassPluginType type = COBASS_PLUGIN_TYPE_EFFECT;
    bool supportsMidi = false;
    bool supportsSidechain = false;
    std::vector<PluginParam> parameters;

    static PluginDescriptor fromManifest(const CobassPluginManifest* manifest, const std::string& libPath = "") {
        PluginDescriptor desc;
        if (!manifest) return desc;

        desc.pluginId = manifest->pluginId;
        desc.name = manifest->name;
        desc.vendor = manifest->vendor;
        desc.version = manifest->version;
        desc.libraryPath = libPath;
        desc.type = manifest->type;
        desc.supportsMidi = manifest->supportsMidi;
        desc.supportsSidechain = manifest->supportsSidechain;

        for (uint32_t i = 0; i < manifest->paramCount; ++i) {
            const auto& p = manifest->params[i];
            PluginParam param;
            param.id = p.id;
            param.name = p.name;
            param.label = p.label;
            param.type = p.type;
            param.minValue = p.minValue;
            param.maxValue = p.maxValue;
            param.defaultValue = p.defaultValue;
            param.step = p.step;
            param.isLogarithmic = p.isLogarithmic;
            for (uint32_t c = 0; c < p.choiceCount && c < COBASS_MAX_CHOICES; ++c) {
                param.choices.emplace_back(p.choices[c]);
            }
            desc.parameters.push_back(std::move(param));
        }
        return desc;
    }
};
