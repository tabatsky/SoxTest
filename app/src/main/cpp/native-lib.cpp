#include <jni.h>
#include <string>
#include <cstring>
#include <iostream>
#include <assert.h>
#include <android/log.h>
#include "sox.h"

#define APPNAME "SoxTest"

#define RESULT_SUCCESS 0
#define RESULT_ERROR -1

int sox_convert(char* inPathCStr, char* outPathCStr);
int sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr);
int sox_pitch(char* inPathCStr, char* outPathCStr, char* pitchCStr);
int sox_reverse(char* inPathCStr, char* outPathCStr);

extern "C" JNIEXPORT jstring JNICALL
Java_jatx_soxtest_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT int JNICALL
Java_jatx_soxtest_MainActivity_convertAudioFileJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath) {
    char* inPathCStr;
    char* outPathCStr;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    return sox_convert(inPathCStr, outPathCStr);
}

extern "C" JNIEXPORT int JNICALL
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
    return sox_tempo(inPathCStr, outPathCStr, tempoCStr);
}

extern "C" JNIEXPORT int JNICALL
Java_jatx_soxtest_MainActivity_applyPitchJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath,
        jstring pitch
        ) {
    char* inPathCStr;
    char* outPathCStr;
    char* pitchCStr;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    pitchCStr = (char*) env->GetStringUTFChars(pitch, NULL);
    return sox_pitch(inPathCStr, outPathCStr, pitchCStr);
}

extern "C" JNIEXPORT int JNICALL
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
    return sox_reverse(inPathCStr, outPathCStr);
}

int sox_convert(char* inPathCStr, char* outPathCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    if(sox_init() != SOX_SUCCESS) {
        return RESULT_ERROR;
    }

    /* Open the input file (with default parameters) */
    in = sox_open_read(inPathCStr, NULL, NULL, NULL);
    if (!in) {
        return RESULT_ERROR;
    }

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL);
    if (!out) {
        return RESULT_ERROR;
    }

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* This becomes the first `effect' in the chain */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Convert done: %s; %s", inPathCStr, outPathCStr);

    return RESULT_SUCCESS;
}

int sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    if(sox_init() != SOX_SUCCESS) {
        return RESULT_ERROR;
    }

    /* Open the input file (with default parameters) */
    in = sox_open_read(inPathCStr, NULL, NULL, NULL);
    if (!in) {
        return RESULT_ERROR;
    }

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL);
    if (!out) {
        return RESULT_ERROR;
    }

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* This becomes the first `effect' in the chain */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Create the `tempo' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("tempo"));
    args[0] = tempoCStr;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* Add the effect to the end of the effects processing chain: */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Tempo done: %s", tempoCStr);

    return RESULT_SUCCESS;
}

int sox_pitch(char* inPathCStr, char* outPathCStr, char* pitchCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    if(sox_init() != SOX_SUCCESS) {
        return RESULT_ERROR;
    }

    /* Open the input file (with default parameters) */
    in = sox_open_read(inPathCStr, NULL, NULL, NULL);
    if (!in) {
        return RESULT_ERROR;
    }

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL);
    if (!out) {
        return RESULT_ERROR;
    }

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* This becomes the first `effect' in the chain */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Create the `tempo' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("pitch"));
    args[0] = pitchCStr;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* Add the effect to the end of the effects processing chain: */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Pitch done: %s", pitchCStr);

    return RESULT_SUCCESS;
}

int sox_reverse(char* inPathCStr, char* outPathCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    char * tmp_path = "/sdcard/Android/data/jatx.soxtest/files";
    sox_globals.tmp_path = tmp_path;

    /* All libSoX applications must start by initialising the SoX library    */
    if(sox_init() != SOX_SUCCESS) {
        return RESULT_ERROR;
    }

    /* Open the input file (with default parameters) */
    in = sox_open_read(inPathCStr, NULL, NULL, NULL);
    if (!in) {
        return RESULT_ERROR;
    }

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL);
    if (!out) {
        return RESULT_ERROR;
    }

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* This becomes the first `effect' in the chain */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Create the `reverse' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("reverse"));
    if(sox_effect_options(e, 0, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* Add the effect to the end of the effects processing chain: */
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    if(sox_add_effect(chain, e, &interm_signal, &interm_signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Reverse done");

    return RESULT_SUCCESS;
}