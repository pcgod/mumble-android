
#include "hawtjni_native.h"
#include "hawtjni.h"
#include "hawtjni_native_structs.h"
#include "hawtjni_native_stats.h"

#undef JNIEXPORT
#define JNIEXPORT __attribute__ ((visibility("default")))
#define Native_NATIVE(func) Java_org_pcgod_mumbleclient_jni_Native_##func

JNIEXPORT jint JNICALL Native_NATIVE(celt_1decode)
	(JNIEnv *env, jclass that, jlong arg0, jbyteArray arg1, jint arg2, jshortArray arg3)
{
	jbyte *lparg1=NULL;
	jshort *lparg3=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1decode_FUNC);
	if (arg1) if ((lparg1 = (*env)->GetByteArrayElements(env, arg1, NULL)) == NULL) goto fail;
	if (arg3) if ((lparg3 = (*env)->GetShortArrayElements(env, arg3, NULL)) == NULL) goto fail;
	rc = wrap_celt_decode((CELTDecoder *)(intptr_t)arg0, (unsigned char *)lparg1, arg2, lparg3);
fail:
	if (arg3 && lparg3) (*env)->ReleaseShortArrayElements(env, arg3, lparg3, 0);
	if (arg1 && lparg1) (*env)->ReleaseByteArrayElements(env, arg1, lparg1, JNI_ABORT);
	Native_NATIVE_EXIT(env, that, Native_celt_1decode_FUNC);
	return rc;
}

JNIEXPORT jint JNICALL Native_NATIVE(celt_1decode_1float)
	(JNIEnv *env, jclass that, jlong arg0, jbyteArray arg1, jint arg2, jfloatArray arg3)
{
	jbyte *lparg1=NULL;
	jfloat *lparg3=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1decode_1float_FUNC);
	if (arg1) if ((lparg1 = (*env)->GetByteArrayElements(env, arg1, NULL)) == NULL) goto fail;
	if (arg3) if ((lparg3 = (*env)->GetFloatArrayElements(env, arg3, NULL)) == NULL) goto fail;
	rc = wrap_celt_decode_float((CELTDecoder *)(intptr_t)arg0, (unsigned char *)lparg1, arg2, lparg3);
fail:
	if (arg3 && lparg3) (*env)->ReleaseFloatArrayElements(env, arg3, lparg3, 0);
	if (arg1 && lparg1) (*env)->ReleaseByteArrayElements(env, arg1, lparg1, JNI_ABORT);
	Native_NATIVE_EXIT(env, that, Native_celt_1decode_1float_FUNC);
	return rc;
}

JNIEXPORT jlong JNICALL Native_NATIVE(celt_1decoder_1create)
	(JNIEnv *env, jclass that, jlong arg0, jint arg1)
{
	jlong rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1decoder_1create_FUNC);
	rc = (intptr_t)(CELTDecoder *)wrap_celt_decoder_create((CELTMode *)(intptr_t)arg0, arg1);
	Native_NATIVE_EXIT(env, that, Native_celt_1decoder_1create_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(celt_1decoder_1destroy)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_celt_1decoder_1destroy_FUNC);
	celt_decoder_destroy((CELTDecoder *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_celt_1decoder_1destroy_FUNC);
}

JNIEXPORT jint JNICALL Native_NATIVE(celt_1encode)
	(JNIEnv *env, jclass that, jlong arg0, jshortArray arg1, jbyteArray arg2, jint arg3)
{
	jshort *lparg1=NULL;
	jbyte *lparg2=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1encode_FUNC);
	if (arg1) if ((lparg1 = (*env)->GetShortArrayElements(env, arg1, NULL)) == NULL) goto fail;
	if (arg2) if ((lparg2 = (*env)->GetByteArrayElements(env, arg2, NULL)) == NULL) goto fail;
	rc = wrap_celt_encode((CELTEncoder *)(intptr_t)arg0, lparg1, (unsigned char *)lparg2, arg3);
fail:
	if (arg2 && lparg2) (*env)->ReleaseByteArrayElements(env, arg2, lparg2, 0);
	if (arg1 && lparg1) (*env)->ReleaseShortArrayElements(env, arg1, lparg1, JNI_ABORT);
	Native_NATIVE_EXIT(env, that, Native_celt_1encode_FUNC);
	return rc;
}

JNIEXPORT jlong JNICALL Native_NATIVE(celt_1encoder_1create)
	(JNIEnv *env, jclass that, jlong arg0, jint arg1)
{
	jlong rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1encoder_1create_FUNC);
	rc = (intptr_t)(CELTEncoder *)wrap_celt_encoder_create((CELTMode *)(intptr_t)arg0, arg1);
	Native_NATIVE_EXIT(env, that, Native_celt_1encoder_1create_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(celt_1encoder_1ctl)
	(JNIEnv *env, jclass that, jlong arg0, jint arg1, jint arg2)
{
	Native_NATIVE_ENTER(env, that, Native_celt_1encoder_1ctl_FUNC);
	celt_encoder_ctl((CELTEncoder *)(intptr_t)arg0, arg1, arg2);
	Native_NATIVE_EXIT(env, that, Native_celt_1encoder_1ctl_FUNC);
}

JNIEXPORT void JNICALL Native_NATIVE(celt_1encoder_1destroy)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_celt_1encoder_1destroy_FUNC);
	celt_encoder_destroy((CELTEncoder *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_celt_1encoder_1destroy_FUNC);
}

JNIEXPORT jlong JNICALL Native_NATIVE(celt_1mode_1create)
	(JNIEnv *env, jclass that, jint arg0, jint arg1)
{
	jlong rc = 0;
	Native_NATIVE_ENTER(env, that, Native_celt_1mode_1create_FUNC);
	rc = (intptr_t)(CELTMode *)wrap_celt_mode_create(arg0, arg1);
	Native_NATIVE_EXIT(env, that, Native_celt_1mode_1create_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(celt_1mode_1destroy)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_celt_1mode_1destroy_FUNC);
	celt_mode_destroy((CELTMode *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_celt_1mode_1destroy_FUNC);
}

JNIEXPORT jint JNICALL Native_NATIVE(jitter_1buffer_1ctl)
	(JNIEnv *env, jclass that, jlong arg0, jint arg1, jintArray arg2)
{
	jint *lparg2=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1ctl_FUNC);
	if (arg2) if ((lparg2 = (*env)->GetIntArrayElements(env, arg2, NULL)) == NULL) goto fail;
	rc = jitter_buffer_ctl((JitterBuffer *)(intptr_t)arg0, arg1, lparg2);
fail:
	if (arg2 && lparg2) (*env)->ReleaseIntArrayElements(env, arg2, lparg2, 0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1ctl_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(jitter_1buffer_1destroy)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1destroy_FUNC);
	jitter_buffer_destroy((JitterBuffer *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1destroy_FUNC);
}

JNIEXPORT jint JNICALL Native_NATIVE(jitter_1buffer_1get)
	(JNIEnv *env, jclass that, jlong arg0, jobject arg1, jint arg2, jintArray arg3)
{
	JitterBufferPacket _arg1, *lparg1=NULL;
	jint *lparg3=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1get_FUNC);
	if (arg1) if ((lparg1 = getJitterBufferPacketFields(env, arg1, &_arg1)) == NULL) goto fail;
	if (arg3) if ((lparg3 = (*env)->GetIntArrayElements(env, arg3, NULL)) == NULL) goto fail;
	rc = jitter_buffer_get((JitterBuffer *)(intptr_t)arg0, lparg1, arg2, lparg3);
fail:
	if (arg3 && lparg3) (*env)->ReleaseIntArrayElements(env, arg3, lparg3, 0);
	if (arg1 && lparg1) setJitterBufferPacketFields(env, arg1, lparg1);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1get_FUNC);
	return rc;
}

JNIEXPORT jint JNICALL Native_NATIVE(jitter_1buffer_1get_1pointer_1timestamp)
	(JNIEnv *env, jclass that, jlong arg0)
{
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1get_1pointer_1timestamp_FUNC);
	rc = jitter_buffer_get_pointer_timestamp((JitterBuffer *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1get_1pointer_1timestamp_FUNC);
	return rc;
}

JNIEXPORT jlong JNICALL Native_NATIVE(jitter_1buffer_1init)
	(JNIEnv *env, jclass that, jint arg0)
{
	jlong rc = 0;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1init_FUNC);
	rc = (intptr_t)(JitterBuffer *)jitter_buffer_init(arg0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1init_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(jitter_1buffer_1put)
	(JNIEnv *env, jclass that, jlong arg0, jobject arg1)
{
	JitterBufferPacket _arg1, *lparg1=NULL;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1put_FUNC);
	if (arg1) if ((lparg1 = getJitterBufferPacketFields(env, arg1, &_arg1)) == NULL) goto fail;
	jitter_buffer_put((JitterBuffer *)(intptr_t)arg0, lparg1);
fail:
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1put_FUNC);
}

JNIEXPORT void JNICALL Native_NATIVE(jitter_1buffer_1tick)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1tick_FUNC);
	jitter_buffer_tick((JitterBuffer *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1tick_FUNC);
}

JNIEXPORT jint JNICALL Native_NATIVE(jitter_1buffer_1update_1delay)
	(JNIEnv *env, jclass that, jlong arg0, jobject arg1, jintArray arg2)
{
	JitterBufferPacket _arg1, *lparg1=NULL;
	jint *lparg2=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_jitter_1buffer_1update_1delay_FUNC);
	if (arg1) if ((lparg1 = getJitterBufferPacketFields(env, arg1, &_arg1)) == NULL) goto fail;
	if (arg2) if ((lparg2 = (*env)->GetIntArrayElements(env, arg2, NULL)) == NULL) goto fail;
	rc = jitter_buffer_update_delay((JitterBuffer *)(intptr_t)arg0, lparg1, lparg2);
fail:
	if (arg2 && lparg2) (*env)->ReleaseIntArrayElements(env, arg2, lparg2, 0);
	Native_NATIVE_EXIT(env, that, Native_jitter_1buffer_1update_1delay_FUNC);
	return rc;
}

JNIEXPORT void JNICALL Native_NATIVE(speex_1resampler_1destroy)
	(JNIEnv *env, jclass that, jlong arg0)
{
	Native_NATIVE_ENTER(env, that, Native_speex_1resampler_1destroy_FUNC);
	speex_resampler_destroy((SpeexResamplerState *)(intptr_t)arg0);
	Native_NATIVE_EXIT(env, that, Native_speex_1resampler_1destroy_FUNC);
}

JNIEXPORT jlong JNICALL Native_NATIVE(speex_1resampler_1init)
	(JNIEnv *env, jclass that, jlong arg0, jlong arg1, jlong arg2, jint arg3)
{
	jlong rc = 0;
	Native_NATIVE_ENTER(env, that, Native_speex_1resampler_1init_FUNC);
	rc = (intptr_t)(SpeexResamplerState *)wrap_speex_resampler_init(arg0, arg1, arg2, arg3);
	Native_NATIVE_EXIT(env, that, Native_speex_1resampler_1init_FUNC);
	return rc;
}

JNIEXPORT jint JNICALL Native_NATIVE(speex_1resampler_1process_1int)
	(JNIEnv *env, jclass that, jlong arg0, jint arg1, jshortArray arg2, jintArray arg3, jshortArray arg4, jintArray arg5)
{
	jshort *lparg2=NULL;
	jint *lparg3=NULL;
	jshort *lparg4=NULL;
	jint *lparg5=NULL;
	jint rc = 0;
	Native_NATIVE_ENTER(env, that, Native_speex_1resampler_1process_1int_FUNC);
	if (arg2) if ((lparg2 = (*env)->GetShortArrayElements(env, arg2, NULL)) == NULL) goto fail;
	if (arg3) if ((lparg3 = (*env)->GetIntArrayElements(env, arg3, NULL)) == NULL) goto fail;
	if (arg4) if ((lparg4 = (*env)->GetShortArrayElements(env, arg4, NULL)) == NULL) goto fail;
	if (arg5) if ((lparg5 = (*env)->GetIntArrayElements(env, arg5, NULL)) == NULL) goto fail;
	rc = speex_resampler_process_int((SpeexResamplerState *)(intptr_t)arg0, arg1, lparg2, lparg3, lparg4, lparg5);
fail:
	if (arg5 && lparg5) (*env)->ReleaseIntArrayElements(env, arg5, lparg5, 0);
	if (arg4 && lparg4) (*env)->ReleaseShortArrayElements(env, arg4, lparg4, 0);
	if (arg3 && lparg3) (*env)->ReleaseIntArrayElements(env, arg3, lparg3, 0);
	if (arg2 && lparg2) (*env)->ReleaseShortArrayElements(env, arg2, lparg2, JNI_ABORT);
	Native_NATIVE_EXIT(env, that, Native_speex_1resampler_1process_1int_FUNC);
	return rc;
}

