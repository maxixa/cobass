#include "sequencer/MusicTheory.hpp"
#include "sequencer/NoteTransformEngine.hpp"
#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include "AudioEngine.hpp"
#include "plugin/PluginLoader.hpp"

static std::unique_ptr<AudioEngine> gAudioEngine;

static jobject createJavaPluginDescriptor(JNIEnv* env, const PluginDescriptor& desc) {
    jclass descClass = env->FindClass("com/maxica/cobass/model/PluginDescriptorItem");
    if (!descClass) return nullptr;

    jmethodID descCtor = env->GetMethodID(descClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZZ)V");
    jmethodID addParamMethod = env->GetMethodID(descClass, "addParameter", "(Lcom/maxica/cobass/model/PluginParamItem;)V");

    jclass paramClass = env->FindClass("com/maxica/cobass/model/PluginParamItem");
    jmethodID paramCtor = paramClass ? env->GetMethodID(paramClass, "<init>", "(ILjava/lang/String;Ljava/lang/String;IFFFFZ)V") : nullptr;
    jmethodID addChoiceMethod = paramClass ? env->GetMethodID(paramClass, "addChoice", "(Ljava/lang/String;)V") : nullptr;

    jstring jId = env->NewStringUTF(desc.pluginId.c_str());
    jstring jName = env->NewStringUTF(desc.name.c_str());
    jstring jVendor = env->NewStringUTF(desc.vendor.c_str());
    jstring jVer = env->NewStringUTF(desc.version.c_str());
    jstring jPath = env->NewStringUTF(desc.libraryPath.c_str());
    jint jType = static_cast<jint>(desc.type);

    jobject jDescObj = env->NewObject(descClass, descCtor, jId, jName, jVendor, jVer, jPath, jType, (jboolean)desc.supportsMidi, (jboolean)desc.supportsSidechain);

    env->DeleteLocalRef(jId);
    env->DeleteLocalRef(jName);
    env->DeleteLocalRef(jVendor);
    env->DeleteLocalRef(jVer);
    env->DeleteLocalRef(jPath);

    if (paramClass && paramCtor && addParamMethod) {
        for (const auto& p : desc.parameters) {
            jstring jpName = env->NewStringUTF(p.name.c_str());
            jstring jpLabel = env->NewStringUTF(p.label.c_str());
            jobject jParamObj = env->NewObject(paramClass, paramCtor,
                static_cast<jint>(p.id), jpName, jpLabel, static_cast<jint>(p.type),
                p.minValue, p.maxValue, p.defaultValue, p.step, static_cast<jboolean>(p.isLogarithmic));

            env->DeleteLocalRef(jpName);
            env->DeleteLocalRef(jpLabel);

            if (addChoiceMethod) {
                for (const auto& ch : p.choices) {
                    jstring jCh = env->NewStringUTF(ch.c_str());
                    env->CallVoidMethod(jParamObj, addChoiceMethod, jCh);
                    env->DeleteLocalRef(jCh);
                }
            }

            env->CallVoidMethod(jDescObj, addParamMethod, jParamObj);
            env->DeleteLocalRef(jParamObj);
        }
    }

    env->DeleteLocalRef(descClass);
    if (paramClass) env->DeleteLocalRef(paramClass);

    return jDescObj;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeInit(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (!gAudioEngine) gAudioEngine = std::make_unique<AudioEngine>();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeStart(JNIEnv* /*env*/, jclass /*clazz*/) {
    return (gAudioEngine && gAudioEngine->start()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeStop(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->stop();
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeResetProject(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->resetProject();
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddSynthTrack(JNIEnv* env, jclass /*clazz*/, jstring name) {
    if (!gAudioEngine) return -1;
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    int32_t id = gAudioEngine->addSynthTrack(nativeName ? nativeName : "Synth");
    if (nativeName) env->ReleaseStringUTFChars(name, nativeName);
    return id;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddStepSequencerTrack(JNIEnv* env, jclass /*clazz*/, jstring name) {
    if (!gAudioEngine) return -1;
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    int32_t id = gAudioEngine->addStepSequencerTrack(nativeName ? nativeName : "Step Drum");
    if (nativeName) env->ReleaseStringUTFChars(name, nativeName);
    return id;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetStepSequencerStep(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex, jint stepIndex, jboolean active, jfloat velocity, jint pitch, jfloat gate, jfloat nudge, jint ratchets, jfloat prob) {
    if (gAudioEngine) gAudioEngine->setStepSequencerStep(trackId, laneIndex, stepIndex, active == JNI_TRUE, velocity, pitch, gate, nudge, ratchets, prob);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeLoadStepSequencerSample(JNIEnv* env, jclass /*clazz*/, jint trackId, jint laneIndex, jfloatArray data, jint length, jint channels) {
    if (!gAudioEngine || !data) return;
    jfloat* pcm = env->GetFloatArrayElements(data, nullptr);
    if (pcm) {
        gAudioEngine->loadStepSequencerSample(trackId, laneIndex, pcm, length, channels);
        env->ReleaseFloatArrayElements(data, pcm, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeClearStepSequencerLane(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex) {
    if (gAudioEngine) gAudioEngine->clearStepSequencerLane(trackId, laneIndex);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetStepSequencerLaneParams(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint laneIndex, jint midiNote, jint stepCount, jint stepTicks, jfloat volume, jfloat pan, jboolean mute, jboolean solo) {
    if (gAudioEngine) gAudioEngine->setStepSequencerLaneParams(trackId, laneIndex, midiNote, stepCount, stepTicks, volume, pan, mute == JNI_TRUE, solo == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddAudioTrack(JNIEnv* env, jclass /*clazz*/, jstring name) {
    if (!gAudioEngine) return -1;
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    int32_t id = gAudioEngine->addAudioTrack(nativeName ? nativeName : "Audio");
    if (nativeName) env->ReleaseStringUTFChars(name, nativeName);
    return id;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeRemoveTrack(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId) {
    if (gAudioEngine) gAudioEngine->removeTrack(trackId);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeNoteOn(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint note, jfloat velocity) {
    if (gAudioEngine) gAudioEngine->noteOn(trackId, note, velocity);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeNoteOff(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint note) {
    if (gAudioEngine) gAudioEngine->noteOff(trackId, note);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackVolume(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jfloat volume) {
    if (gAudioEngine) gAudioEngine->setTrackVolume(trackId, volume);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackPan(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jfloat pan) {
    if (gAudioEngine) gAudioEngine->setTrackPan(trackId, pan);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackMute(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jboolean mute) {
    if (gAudioEngine) gAudioEngine->setTrackMute(trackId, mute == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackSolo(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jboolean solo) {
    if (gAudioEngine) gAudioEngine->setTrackSolo(trackId, solo == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackPhaseInvert(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jboolean invert) {
    if (gAudioEngine) gAudioEngine->setTrackPhaseInvert(trackId, invert == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackParam(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint paramId, jfloat value) {
    if (gAudioEngine) gAudioEngine->setTrackParam(trackId, static_cast<uint32_t>(paramId), value);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackFxParam(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint fxSlot, jint paramId, jfloat value) {
    if (gAudioEngine) gAudioEngine->setTrackFxParam(trackId, fxSlot, static_cast<uint32_t>(paramId), value);
}

// Modular Plugin System JNI Implementations
JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeScanPlugins(JNIEnv* env, jclass /*clazz*/, jstring searchDirectory) {
    if (!gAudioEngine || !searchDirectory) return 0;
    const char* path = env->GetStringUTFChars(searchDirectory, nullptr);
    int32_t count = gAudioEngine->scanPlugins(path ? path : "");
    if (path) env->ReleaseStringUTFChars(searchDirectory, path);
    return count;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetPluginCount(JNIEnv* /*env*/, jclass /*clazz*/) {
    return static_cast<jint>(PluginLoader::getInstance().getAvailablePlugins().size());
}

JNIEXPORT jobject JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetPluginDescriptor(JNIEnv* env, jclass /*clazz*/, jint index) {
    auto list = PluginLoader::getInstance().getAvailablePlugins();
    if (index < 0 || static_cast<size_t>(index) >= list.size()) return nullptr;
    return createJavaPluginDescriptor(env, list[static_cast<size_t>(index)]);
}

JNIEXPORT jobject JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetPluginDescriptorById(JNIEnv* env, jclass /*clazz*/, jstring pluginId) {
    if (!pluginId) return nullptr;
    const char* idStr = env->GetStringUTFChars(pluginId, nullptr);
    const PluginDescriptor* desc = PluginLoader::getInstance().findDescriptor(idStr ? idStr : "");
    if (idStr) env->ReleaseStringUTFChars(pluginId, idStr);
    return desc ? createJavaPluginDescriptor(env, *desc) : nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackSynthPlugin(JNIEnv* env, jclass /*clazz*/, jint trackId, jstring pluginId) {
    if (!gAudioEngine || !pluginId) return JNI_FALSE;
    const char* idStr = env->GetStringUTFChars(pluginId, nullptr);
    bool ok = gAudioEngine->setTrackSynthPlugin(trackId, idStr ? idStr : "");
    if (idStr) env->ReleaseStringUTFChars(pluginId, idStr);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeRemoveTrackSynthPlugin(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId) {
    if (gAudioEngine) gAudioEngine->removeTrackSynthPlugin(trackId);
}

JNIEXPORT jstring JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackSynthPluginId(JNIEnv* env, jclass /*clazz*/, jint trackId) {
    if (!gAudioEngine) return env->NewStringUTF("");
    std::string id = gAudioEngine->getTrackSynthPluginId(trackId);
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddTrackFxPlugin(JNIEnv* env, jclass /*clazz*/, jint trackId, jint slotIndex, jstring pluginId) {
    if (!gAudioEngine || !pluginId) return JNI_FALSE;
    const char* idStr = env->GetStringUTFChars(pluginId, nullptr);
    bool ok = gAudioEngine->addTrackFxPlugin(trackId, slotIndex, idStr ? idStr : "");
    if (idStr) env->ReleaseStringUTFChars(pluginId, idStr);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeRemoveTrackFxPlugin(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint slotIndex) {
    if (gAudioEngine) gAudioEngine->removeTrackFxPlugin(trackId, slotIndex);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackFxBypass(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint slotIndex, jboolean bypass) {
    if (gAudioEngine) gAudioEngine->setTrackFxBypass(trackId, slotIndex, bypass == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeIsTrackFxBypassed(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint slotIndex) {
    return (gAudioEngine && gAudioEngine->isTrackFxBypassed(trackId, slotIndex)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeMoveTrackFxSlot(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint fromSlot, jint toSlot) {
    if (gAudioEngine) gAudioEngine->moveTrackFxSlot(trackId, fromSlot, toSlot);
}

JNIEXPORT jstring JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackFxPluginId(JNIEnv* env, jclass /*clazz*/, jint trackId, jint slotIndex) {
    if (!gAudioEngine) return env->NewStringUTF("");
    std::string id = gAudioEngine->getTrackFxPluginId(trackId, slotIndex);
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetPluginParameter(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint slotIndex, jint paramId, jfloat value) {
    if (gAudioEngine) gAudioEngine->setPluginParameter(trackId, slotIndex, static_cast<uint32_t>(paramId), value);
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetPluginParameter(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jint slotIndex, jint paramId) {
    return gAudioEngine ? gAudioEngine->getPluginParameter(trackId, slotIndex, static_cast<uint32_t>(paramId)) : 0.0f;
}

JNIEXPORT jstring JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetPluginStateJson(JNIEnv* env, jclass /*clazz*/, jint trackId, jint slotIndex) {
    if (!gAudioEngine) return env->NewStringUTF("{}");
    std::string json = gAudioEngine->getPluginStateJson(trackId, slotIndex);
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetPluginStateJson(JNIEnv* env, jclass /*clazz*/, jint trackId, jint slotIndex, jstring jsonState) {
    if (!gAudioEngine || !jsonState) return JNI_FALSE;
    const char* jsonStr = env->GetStringUTFChars(jsonState, nullptr);
    bool ok = gAudioEngine->setPluginStateJson(trackId, slotIndex, jsonStr ? jsonStr : "{}");
    if (jsonStr) env->ReleaseStringUTFChars(jsonState, jsonStr);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetMasterVolume(JNIEnv* /*env*/, jclass /*clazz*/, jfloat volume) {
    if (gAudioEngine) gAudioEngine->setMasterVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetMasterLimiter(JNIEnv* /*env*/, jclass /*clazz*/, jboolean enabled) {
    if (gAudioEngine) gAudioEngine->setMasterLimiter(enabled == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackPeakL(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId) {
    return gAudioEngine ? gAudioEngine->getTrackPeakL(trackId) : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackPeakR(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId) {
    return gAudioEngine ? gAudioEngine->getTrackPeakR(trackId) : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetMasterPeakL(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getMasterPeakL() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetMasterPeakR(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getMasterPeakR() : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetTrackTrimAndFade(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId, jfloat trimStart, jfloat trimEnd, jfloat fadeIn, jfloat fadeOut) {
    if (gAudioEngine) gAudioEngine->setTrackTrimAndFade(trackId, trimStart, trimEnd, fadeIn, fadeOut);
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackPlaybackPosition(JNIEnv* /*env*/, jclass /*clazz*/, jint trackId) {
    return gAudioEngine ? gAudioEngine->getTrackPlaybackPosition(trackId) : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeLoadSample(JNIEnv* env, jclass /*clazz*/, jint trackId, jfloatArray data, jint length, jint channels) {
    if (!gAudioEngine || !data) return;
    jfloat* pcm = env->GetFloatArrayElements(data, nullptr);
    if (pcm) {
        gAudioEngine->loadTrackSample(trackId, pcm, length, channels);
        env->ReleaseFloatArrayElements(data, pcm, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeTransportPlayFromStart(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->transportPlayFromStart();
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeTransportPlay(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->transportPlay();
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeTransportPause(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->transportPause();
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeTransportStop(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->transportStop();
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeTransportSeek(JNIEnv* /*env*/, jclass /*clazz*/, jlong tick) {
    if (gAudioEngine) gAudioEngine->transportSeek(tick);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetBpm(JNIEnv* /*env*/, jclass /*clazz*/, jfloat bpm) {
    if (gAudioEngine) gAudioEngine->setBpm(bpm);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSetLoop(JNIEnv* /*env*/, jclass /*clazz*/, jlong startTick, jlong endTick, jboolean enabled) {
    if (gAudioEngine) gAudioEngine->setLoop(startTick, endTick, enabled == JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetLoopStart(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getLoopStart() : 0;
}

JNIEXPORT jlong JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetLoopEnd(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getLoopEnd() : 7680;
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeIsLoopEnabled(JNIEnv* /*env*/, jclass /*clazz*/) {
    return (gAudioEngine && gAudioEngine->isLoopEnabled()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddClip(JNIEnv* env, jclass /*clazz*/, jint trackId, jlong startTick, jlong lengthTicks, jstring name) {
    if (!gAudioEngine) return -1;
    const char* nativeName = env->GetStringUTFChars(name, nullptr);
    int32_t id = gAudioEngine->addClip(trackId, startTick, lengthTicks, nativeName ? nativeName : "Clip");
    if (nativeName) env->ReleaseStringUTFChars(name, nativeName);
    return id;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeRemoveClip(JNIEnv* /*env*/, jclass /*clazz*/, jint clipId) {
    if (gAudioEngine) gAudioEngine->removeClip(clipId);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeMoveClip(JNIEnv* /*env*/, jclass /*clazz*/, jint clipId, jint newTrackId, jlong newStartTick) {
    if (gAudioEngine) gAudioEngine->moveClip(clipId, newTrackId, newStartTick);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeResizeClip(JNIEnv* /*env*/, jclass /*clazz*/, jint clipId, jlong newLengthTicks) {
    if (gAudioEngine) gAudioEngine->resizeClip(clipId, newLengthTicks);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeClearClipNotes(JNIEnv* /*env*/, jclass /*clazz*/, jint clipId) {
    if (gAudioEngine) gAudioEngine->clearClipNotes(clipId);
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeAddNoteToClip(JNIEnv* /*env*/, jclass /*clazz*/, jint clipId, jint note, jfloat vel, jlong startOffset, jlong len) {
    if (gAudioEngine) gAudioEngine->addNoteToClip(clipId, note, vel, startOffset, len);
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeExportWav(JNIEnv* env, jclass /*clazz*/, jstring path, jfloat sampleRate, jlong totalTicks) {
    if (!gAudioEngine) return JNI_FALSE;
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    bool ok = gAudioEngine->exportWav(nativePath, sampleRate, totalTicks);
    env->ReleaseStringUTFChars(path, nativePath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeCancelExport(JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gAudioEngine) gAudioEngine->cancelExport();
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetExportProgress(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getExportProgress() : 0.0f;
}

JNIEXPORT jlong JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetCurrentTick(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getCurrentTick() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeIsPlaying(JNIEnv* /*env*/, jclass /*clazz*/) {
    return (gAudioEngine && gAudioEngine->isPlaying()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloat JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetBpm(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getBpm() : 120.0f;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetSampleRate(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getSampleRate() : 48000;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetFramesPerBurst(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getFramesPerBurst() : 192;
}

JNIEXPORT jboolean JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeIsLowLatency(JNIEnv* /*env*/, jclass /*clazz*/) {
    return (gAudioEngine && gAudioEngine->isLowLatency()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeGetTrackCount(JNIEnv* /*env*/, jclass /*clazz*/) {
    return gAudioEngine ? gAudioEngine->getTrackCount() : 0;
}


JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSnapPitchToScale(JNIEnv* /*env*/, jclass /*clazz*/, jint rawPitch, jint rootKey, jint scaleOrdinal) {
    using namespace Cobass::Music;
    const auto& desc = getScaleDescriptor(static_cast<ScaleType>(scaleOrdinal));
    return snapPitchToScale(rawPitch, rootKey, desc.intervalMask);
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeInvertModalPitch(JNIEnv* /*env*/, jclass /*clazz*/, jint rawPitch, jint axisPitch, jint rootKey, jint scaleOrdinal) {
    using namespace Cobass::Music;
    const auto& desc = getScaleDescriptor(static_cast<ScaleType>(scaleOrdinal));
    return invertModalPitch(rawPitch, axisPitch, rootKey, desc.intervalMask);
}

JNIEXPORT jint JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeSolveVoiceLeading(JNIEnv* /*env*/, jclass /*clazz*/, jint previousPitch, jint targetPitch, jint rootKey, jint scaleOrdinal, jfloat parsimoniousWeight) {
    using namespace Cobass::Music;
    const auto& desc = getScaleDescriptor(static_cast<ScaleType>(scaleOrdinal));
    return solveVoiceLeading(previousPitch, targetPitch, rootKey, desc.intervalMask, parsimoniousWeight);
}


JNIEXPORT jlongArray JNICALL
Java_com_maxica_cobass_audio_AudioEngineNative_nativeExecuteTransformPipeline(
    JNIEnv* env, jclass /*clazz*/,
    jlongArray packedInputNotes,
    jint rootKey, jint scaleMask,
    jint ticksPerBeat, jint beatsPerBar,
    jint operatorType, jfloat intensity, jint seed,
    jfloat param1, jfloat param2,
    jboolean lockDownbeats, jboolean lockPitches, jboolean lockRhythm,
    jboolean lockVelocities, jboolean lockBassNotes,
    jfloat dryWetRatio
) {
    using namespace Cobass::Transform;

    if (!packedInputNotes) return nullptr;
    jsize len = env->GetArrayLength(packedInputNotes);
    if (len % 6 != 0) return nullptr;

    jlong* rawData = env->GetLongArrayElements(packedInputNotes, nullptr);
    if (!rawData) return nullptr;

    size_t noteCount = len / 6;
    std::vector<NoteEvent> inputEvents;
    inputEvents.reserve(noteCount);

    for (size_t i = 0; i < noteCount; ++i) {
        NoteEvent ev;
        ev.pitch = static_cast<int32_t>(rawData[i * 6]);
        uint32_t velBits = static_cast<uint32_t>(rawData[i * 6 + 1]);
        std::memcpy(&ev.velocity, &velBits, sizeof(float));
        ev.startOffsetTicks = rawData[i * 6 + 2];
        ev.lengthTicks = rawData[i * 6 + 3];
        ev.isMuted = (rawData[i * 6 + 4] != 0);
        ev.isSelected = (rawData[i * 6 + 5] != 0);
        inputEvents.push_back(ev);
    }
    env->ReleaseLongArrayElements(packedInputNotes, rawData, JNI_ABORT);

    MusicalContext ctx;
    ctx.rootKey = rootKey;
    ctx.scaleIntervalMask = static_cast<uint32_t>(scaleMask);
    ctx.ticksPerBeat = ticksPerBeat;
    ctx.beatsPerBar = beatsPerBar;

    TransformRecipe recipe;
    recipe.type = static_cast<TransformOperatorType>(operatorType);
    recipe.intensity = intensity;
    recipe.seed = static_cast<uint32_t>(seed);
    recipe.param1 = param1;
    recipe.param2 = param2;
    recipe.enabled = true;

    LockMasks masks;
    masks.lockDownbeats = (lockDownbeats == JNI_TRUE);
    masks.lockPitches = (lockPitches == JNI_TRUE);
    masks.lockRhythm = (lockRhythm == JNI_TRUE);
    masks.lockVelocities = (lockVelocities == JNI_TRUE);
    masks.lockBassNotes = (lockBassNotes == JNI_TRUE);

    std::vector<NoteEvent> outputEvents = NoteTransformEngine::process(
        inputEvents, ctx, {recipe}, masks, dryWetRatio
    );

    jsize outSize = static_cast<jsize>(outputEvents.size() * 6);
    jlongArray outArray = env->NewLongArray(outSize);
    if (!outArray) return nullptr;

    std::vector<jlong> outBuffer(outSize);
    for (size_t i = 0; i < outputEvents.size(); ++i) {
        const auto& ev = outputEvents[i];
        outBuffer[i * 6] = ev.pitch;
        uint32_t velBits = 0;
        std::memcpy(&velBits, &ev.velocity, sizeof(float));
        outBuffer[i * 6 + 1] = static_cast<jlong>(velBits);
        outBuffer[i * 6 + 2] = ev.startOffsetTicks;
        outBuffer[i * 6 + 3] = ev.lengthTicks;
        outBuffer[i * 6 + 4] = ev.isMuted ? 1 : 0;
        outBuffer[i * 6 + 5] = ev.isSelected ? 1 : 0;
    }

    env->SetLongArrayRegion(outArray, 0, outSize, outBuffer.data());
    return outArray;
}

} // extern "C"
