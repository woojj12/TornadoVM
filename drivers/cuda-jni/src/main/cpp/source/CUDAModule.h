/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <cuda.h>
/* Header for class uk_ac_manchester_tornado_drivers_cuda_CUDAModule */

#ifndef _Included_uk_ac_manchester_tornado_drivers_cuda_CUDAModule
#define _Included_uk_ac_manchester_tornado_drivers_cuda_CUDAModule
#ifdef __cplusplus
extern "C" {
#endif

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper) {
    char *module = (char *) module_ptr;
    (*env)->GetByteArrayRegion(env, javaWrapper, 0, sizeof(CUmodule), module);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAModule_cuModuleLoadData
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAModule
 * Method:    cuFuncGetAttribute
 * Signature: (Ljava/lang/String;I[B)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAModule_cuFuncGetAttribute
  (JNIEnv *, jclass, jstring, jint, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif