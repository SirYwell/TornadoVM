/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class tornado_drivers_opencl_OCLEvent */

#ifndef _Included_tornado_drivers_opencl_OCLEvent
#define _Included_tornado_drivers_opencl_OCLEvent
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventInfo
  (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clGetEventProfilingInfo
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clGetEventProfilingInfo
  (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     tornado_drivers_opencl_OCLEvent
 * Method:    clWaitForEvents
 * Signature: ([J)V
 */
JNIEXPORT void JNICALL Java_tornado_drivers_opencl_OCLEvent_clWaitForEvents
  (JNIEnv *, jclass, jlongArray);

#ifdef __cplusplus
}
#endif
#endif