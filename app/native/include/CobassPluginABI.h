#pragma once
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define COBASS_PLUGIN_API_VERSION 1
#define COBASS_MAX_PARAMS 64
#define COBASS_MAX_CHOICES 16

typedef enum {
    COBASS_PLUGIN_TYPE_SYNTH  = 0, // Instrument: Receives MIDI events, generates audio
    COBASS_PLUGIN_TYPE_EFFECT = 1  // Insert FX: Processes audio in-place
} CobassPluginType;

typedef enum {
    COBASS_PARAM_TYPE_FLOAT   = 0, // Continuous slider / rotary knob
    COBASS_PARAM_TYPE_INT     = 1, // Discrete integer steps
    COBASS_PARAM_TYPE_BOOL    = 2, // Toggle switch
    COBASS_PARAM_TYPE_CHOICE  = 3  // Enumerated string choices
} CobassParamType;

typedef struct {
    uint32_t id;
    char name[32];
    char label[16];          // Unit label: "Hz", "dB", "%", "ms", "st", etc.
    CobassParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    bool isLogarithmic;
    char choices[COBASS_MAX_CHOICES][24];
    uint32_t choiceCount;
} CobassParamDescriptor;

typedef struct {
    uint32_t apiVersion;
    char pluginId[64];       // Unique Reverse-DNS ID (e.g., "com.maxica.cobass.plugins.hyperion")
    char name[48];           // Human-readable name
    char vendor[32];         // Vendor name
    char version[16];        // Semantic version
    CobassPluginType type;
    uint32_t paramCount;
    const CobassParamDescriptor* params;
    bool supportsMidi;
    bool supportsSidechain;
} CobassPluginManifest;

typedef void* CobassHandle;

// Function Pointers for exported C-ABI symbols
typedef const CobassPluginManifest* (*CobassGetManifestFunc)(void);
typedef CobassHandle (*CobassCreateInstanceFunc)(float sampleRate);
typedef void (*CobassDestroyInstanceFunc)(CobassHandle handle);
typedef void (*CobassResetFunc)(CobassHandle handle, float sampleRate);
typedef void (*CobassProcessFunc)(CobassHandle handle, const float** inputs, float** outputs, uint32_t numChannels, uint32_t numFrames);
typedef void (*CobassNoteOnFunc)(CobassHandle handle, int32_t note, float velocity);
typedef void (*CobassNoteOffFunc)(CobassHandle handle, int32_t note);
typedef void (*CobassAllNotesOffFunc)(CobassHandle handle);
typedef void (*CobassSetParamFunc)(CobassHandle handle, uint32_t paramId, float value);
typedef float (*CobassGetParamFunc)(CobassHandle handle, uint32_t paramId);
typedef uint32_t (*CobassGetStateFunc)(CobassHandle handle, char* outJsonBuffer, uint32_t maxLen);
typedef bool (*CobassSetStateFunc)(CobassHandle handle, const char* jsonBuffer);

#ifdef __cplusplus
}
#endif
