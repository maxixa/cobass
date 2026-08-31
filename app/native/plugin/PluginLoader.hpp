#pragma once
#include <dlfcn.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <filesystem>
#include <android/log.h>
#include "PluginDescriptor.hpp"
#include "PluginInstance.hpp"

#define PLUGIN_TAG "CobassPluginLoader"
#define LOGP_I(...) __android_log_print(ANDROID_LOG_INFO, PLUGIN_TAG, __VA_ARGS__)
#define LOGP_E(...) __android_log_print(ANDROID_LOG_ERROR, PLUGIN_TAG, __VA_ARGS__)

class PluginLoader {
public:
    static PluginLoader& getInstance() {
        static PluginLoader sInstance;
        return sInstance;
    }

    void scanDirectory(const std::string& directoryPath) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!std::filesystem::exists(directoryPath)) return;

        try {
            for (const auto& entry : std::filesystem::directory_iterator(directoryPath)) {
                if (entry.is_regular_file() && entry.path().extension() == ".so") {
                    const std::string filename = entry.path().filename().string();
                    if (filename.rfind("libcobass_plugin_", 0) == 0 || filename.rfind("libplugin_", 0) == 0) {
                        inspectAndRegister(entry.path().string());
                    }
                }
            }
        } catch (const std::exception& e) {
            LOGP_E("Error scanning plugin directory %s: %s", directoryPath.c_str(), e.what());
        }
    }

    bool registerPluginLibrary(const std::string& soPath) {
        std::lock_guard<std::mutex> lock(mutex_);
        return inspectAndRegister(soPath);
    }

    std::vector<PluginDescriptor> getAvailablePlugins() const {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<PluginDescriptor> list;
        list.reserve(catalog_.size());
        for (const auto& [id, desc] : catalog_) {
            list.push_back(desc);
        }
        return list;
    }

    const PluginDescriptor* findDescriptor(const std::string& pluginId) const {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = catalog_.find(pluginId);
        return it != catalog_.end() ? &it->second : nullptr;
    }

    std::unique_ptr<PluginInstance> instantiatePlugin(const std::string& pluginId, float sampleRate) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = catalog_.find(pluginId);
        if (it == catalog_.end()) {
            LOGP_E("Plugin not found in catalog: %s", pluginId.c_str());
            return nullptr;
        }

        const PluginDescriptor& desc = it->second;
        void* handle = dlopen(desc.libraryPath.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGP_E("dlopen failed for %s: %s", desc.libraryPath.c_str(), dlerror());
            return nullptr;
        }

        auto createFunc = reinterpret_cast<CobassCreateInstanceFunc>(dlsym(handle, "cobass_plugin_create_instance"));
        auto destroyFunc = reinterpret_cast<CobassDestroyInstanceFunc>(dlsym(handle, "cobass_plugin_destroy_instance"));
        auto resetFunc = reinterpret_cast<CobassResetFunc>(dlsym(handle, "cobass_plugin_reset"));
        auto processFunc = reinterpret_cast<CobassProcessFunc>(dlsym(handle, "cobass_plugin_process"));
        auto noteOnFunc = reinterpret_cast<CobassNoteOnFunc>(dlsym(handle, "cobass_plugin_note_on"));
        auto noteOffFunc = reinterpret_cast<CobassNoteOffFunc>(dlsym(handle, "cobass_plugin_note_off"));
        auto allNotesOffFunc = reinterpret_cast<CobassAllNotesOffFunc>(dlsym(handle, "cobass_plugin_all_notes_off"));
        auto setParamFunc = reinterpret_cast<CobassSetParamFunc>(dlsym(handle, "cobass_plugin_set_param"));
        auto getParamFunc = reinterpret_cast<CobassGetParamFunc>(dlsym(handle, "cobass_plugin_get_param"));
        auto getStateFunc = reinterpret_cast<CobassGetStateFunc>(dlsym(handle, "cobass_plugin_get_state"));
        auto setStateFunc = reinterpret_cast<CobassSetStateFunc>(dlsym(handle, "cobass_plugin_set_state"));

        if (!createFunc || !destroyFunc || !processFunc) {
            LOGP_E("Missing required plugin exports in %s", desc.libraryPath.c_str());
            dlclose(handle);
            return nullptr;
        }

        CobassHandle instanceHandle = createFunc(sampleRate);
        if (!instanceHandle) {
            LOGP_E("createFunc returned null for %s", desc.pluginId.c_str());
            dlclose(handle);
            return nullptr;
        }

        LOGP_I("Instantiated plugin: %s (%s)", desc.name.c_str(), desc.pluginId.c_str());

        return std::make_unique<PluginInstance>(
            instanceHandle,
            desc,
            destroyFunc,
            resetFunc,
            processFunc,
            noteOnFunc,
            noteOffFunc,
            allNotesOffFunc,
            setParamFunc,
            getParamFunc,
            getStateFunc,
            setStateFunc
        );
    }

private:
    PluginLoader() = default;

    bool inspectAndRegister(const std::string& soPath) {
        void* handle = dlopen(soPath.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGP_E("Failed to dlopen %s: %s", soPath.c_str(), dlerror());
            return false;
        }

        auto manifestFunc = reinterpret_cast<CobassGetManifestFunc>(dlsym(handle, "cobass_plugin_get_manifest"));
        if (!manifestFunc) {
            dlclose(handle);
            return false;
        }

        const CobassPluginManifest* manifest = manifestFunc();
        if (!manifest || manifest->apiVersion != COBASS_PLUGIN_API_VERSION) {
            LOGP_E("Invalid manifest or API version mismatch in %s", soPath.c_str());
            dlclose(handle);
            return false;
        }

        PluginDescriptor desc = PluginDescriptor::fromManifest(manifest, soPath);
        catalog_[desc.pluginId] = desc;
        LOGP_I("Registered plugin: %s v%s [%s]", desc.name.c_str(), desc.version.c_str(), desc.pluginId.c_str());

        dlclose(handle);
        return true;
    }

    mutable std::mutex mutex_;
    std::unordered_map<std::string, PluginDescriptor> catalog_;
};
