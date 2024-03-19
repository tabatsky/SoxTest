#include <jni.h>
#include <string>
#include <cstring>
#include <iostream>
#include <assert.h>
#include <android/log.h>
#include "sox.h"

#define APPNAME "SoxTest"

void sox_convert(char* inPathCStr, char* outPathCStr);
void sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr);
void sox_reverse(char* inPathCStr, char* outPathCStr);

extern "C" JNIEXPORT jstring JNICALL
Java_jatx_soxtest_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_jatx_soxtest_MainActivity_convertAudioFileJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath) {
    char* inPathCStr;
    char* outPathCStr;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    sox_convert(inPathCStr, outPathCStr);
}

extern "C" JNIEXPORT void JNICALL
Java_jatx_soxtest_MainActivity_applyTempoJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath,
        jstring tempo
        ) {
    char* inPathCStr;
    char* outPathCStr;
    char* tempoCStr;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    tempoCStr = (char*) env->GetStringUTFChars(tempo, NULL);
    sox_tempo(inPathCStr, outPathCStr, tempoCStr);
}

extern "C" JNIEXPORT void JNICALL
Java_jatx_soxtest_MainActivity_applyReverseJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath
        ) {
    char* inPathCStr;
    char* outPathCStr;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    sox_reverse(inPathCStr, outPathCStr);
}

void sox_convert(char* inPathCStr, char* outPathCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    assert(sox_init() == SOX_SUCCESS);

    /* Open the input file (with default parameters) */
    assert(in = sox_open_read(inPathCStr, NULL, NULL, NULL));

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    assert(out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL));

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    /* This becomes the first `effect' in the chain */
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Convert done: %s; %s", inPathCStr, outPathCStr);
}

void sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    assert(sox_init() == SOX_SUCCESS);

    /* Open the input file (with default parameters) */
    assert(in = sox_open_read(inPathCStr, NULL, NULL, NULL));

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    assert(out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL));

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    /* This becomes the first `effect' in the chain */
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* Create the `tempo' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("tempo"));
    args[0] = tempoCStr, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    /* Add the effect to the end of the effects processing chain: */
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Tempo done: %s", tempoCStr);
}

void sox_reverse(char* inPathCStr, char* outPathCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    sox_signalinfo_t interm_signal_2;
    char * args[10];

    char * tmp_path = "/sdcard/Android/data/jatx.soxtest/files";
    sox_globals.tmp_path = tmp_path;

    /* All libSoX applications must start by initialising the SoX library    */
    assert(sox_init() == SOX_SUCCESS);

    /* Open the input file (with default parameters) */
    assert(in = sox_open_read(inPathCStr, NULL, NULL, NULL));

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    assert(out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL));

    interm_signal_2 = out->signal;

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    /* This becomes the first `effect' in the chain */
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* Create the `reverse' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("reverse"));
    assert(sox_effect_options(e, 0, args) == SOX_SUCCESS);
    /* Add the effect to the end of the effects processing chain: */
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out, assert(sox_effect_options(e, 1, args) == SOX_SUCCESS);
    assert(sox_add_effect(chain, e, &interm_signal, &interm_signal) == SOX_SUCCESS);
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Reverse done");
}