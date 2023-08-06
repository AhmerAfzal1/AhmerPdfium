//
// Created by Administrator on 2019/7/1.
//

#include <jni.h>
#include <string>
#include <android/log.h>
#include "java_env.h"

#define TAG "JavaEnv"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

JavaVM *g_jvm;

JNIEXPORT jint
JNI_OnLoad(JavaVM
*vm,
void *reserved
) {
JNIEnv *env;
if (vm->GetEnv((void **) (&env), JNI_VERSION_1_4) != JNI_OK) {
return
JNI_ERR;
}
g_jvm = vm;
return
JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved) {
    LOGD("JNI_OnUnload ------");
    g_jvm = NULL;
}

JNIEnv *getJNIEnv() {
    JNIEnv *env = NULL;
    if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGD("getJNIEnv failed ! ------");
        return NULL;
    }
    return env;
}

jclass getGlobalClass(JNIEnv *env, const char *classname) {
    auto cls = env->FindClass(classname);
    if (cls == nullptr) {
        LOGD("JNI >>> can't find class. name = %s", classname);
        return nullptr;
    }
    auto gref = static_cast<jclass>(env->NewGlobalRef(cls));
    env->DeleteLocalRef(cls);
    return gref;
}

/** if from sub thread . jni-env should attach first. */
JNIEnv *attachJNIEnv() {
    JNIEnv *env = NULL;
    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_4; // choose your JNI version
    args.name = "N_attachJNIEnv";   // you might want to give the java thread a name
    args.group = NULL;
    if (g_jvm->AttachCurrentThreadAsDaemon(&env, &args) != JNI_OK) {
        LOGD("attachJNIEnv failed ! ------");
        return NULL;
    }
    return env;
}

//for sub thread need call this.
void detachJNIEnv() {
    JNIEnv *pEnv = getJNIEnv();
    if (pEnv != nullptr) {
        g_jvm->DetachCurrentThread();
    }
}

void dumpReferenceTables(JNIEnv *env) {
    jclass vm_class = env->FindClass("dalvik/system/VMDebug");
    jmethodID dump_mid = env->GetStaticMethodID(vm_class, "dumpReferenceTables", "()V");
    env->CallStaticVoidMethod(vm_class, dump_mid);
    env->DeleteLocalRef(vm_class);
}

const char *stringReplace(const char *str, const char *src1, const char *dst1) {
    std::string strBig(str);
    const std::string src(src1);
    const std::string dst(dst1);

    std::string::size_type pos = 0;
    std::string::size_type srclen = src.size();
    std::string::size_type dstlen = dst.size();
    while ((pos = strBig.find(src, pos)) != std::string::npos) {
        strBig.replace(pos, srclen, dst);
        pos += dstlen;
    }

    return strBig.data();
}