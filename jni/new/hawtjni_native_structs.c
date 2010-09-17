
#include "hawtjni_native.h"
#include "hawtjni.h"
#include "hawtjni_native_structs.h"

typedef struct JitterBufferPacket_FID_CACHE {
	int cached;
	jclass clazz;
	jfieldID data, len, timestamp, span, sequence, user_data;
} JitterBufferPacket_FID_CACHE;

JitterBufferPacket_FID_CACHE JitterBufferPacketFc;

void cacheJitterBufferPacketFields(JNIEnv *env, jobject lpObject)
{
	if (JitterBufferPacketFc.cached) return;
	JitterBufferPacketFc.clazz = (*env)->GetObjectClass(env, lpObject);
	JitterBufferPacketFc.data = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "data", "[B");
	JitterBufferPacketFc.len = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "len", "I");
	JitterBufferPacketFc.timestamp = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "timestamp", "I");
	JitterBufferPacketFc.span = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "span", "I");
	JitterBufferPacketFc.sequence = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "sequence", "S");
	JitterBufferPacketFc.user_data = (*env)->GetFieldID(env, JitterBufferPacketFc.clazz, "user_data", "I");
	JitterBufferPacketFc.cached = 1;
}

JitterBufferPacket *getJitterBufferPacketFields(JNIEnv *env, jobject lpObject, JitterBufferPacket *lpStruct)
{
	if (!JitterBufferPacketFc.cached) cacheJitterBufferPacketFields(env, lpObject);
	lpStruct->len = (*env)->GetIntField(env, lpObject, JitterBufferPacketFc.len);
	{
	lpStruct->data = malloc(lpStruct->len);
	jbyteArray lpObject1 = (jbyteArray)(*env)->GetObjectField(env, lpObject, JitterBufferPacketFc.data);
	(*env)->GetByteArrayRegion(env, lpObject1, 0, lpStruct->len, (jbyte *)lpStruct->data);
	}
	lpStruct->timestamp = (*env)->GetIntField(env, lpObject, JitterBufferPacketFc.timestamp);
	lpStruct->span = (*env)->GetIntField(env, lpObject, JitterBufferPacketFc.span);
	lpStruct->sequence = (*env)->GetShortField(env, lpObject, JitterBufferPacketFc.sequence);
	lpStruct->user_data = (*env)->GetIntField(env, lpObject, JitterBufferPacketFc.user_data);
	return lpStruct;
}

void setJitterBufferPacketFields(JNIEnv *env, jobject lpObject, JitterBufferPacket *lpStruct)
{
	if (!JitterBufferPacketFc.cached) cacheJitterBufferPacketFields(env, lpObject);
	{
	jbyteArray lpObject1 = (jbyteArray)(*env)->GetObjectField(env, lpObject, JitterBufferPacketFc.data);
	(*env)->SetByteArrayRegion(env, lpObject1, 0, lpStruct->len, (jbyte *)lpStruct->data);
	free(lpStruct->data);
	}
	(*env)->SetIntField(env, lpObject, JitterBufferPacketFc.len, (jint)lpStruct->len);
	(*env)->SetIntField(env, lpObject, JitterBufferPacketFc.timestamp, (jint)lpStruct->timestamp);
	(*env)->SetIntField(env, lpObject, JitterBufferPacketFc.span, (jint)lpStruct->span);
	(*env)->SetShortField(env, lpObject, JitterBufferPacketFc.sequence, (jshort)lpStruct->sequence);
	(*env)->SetIntField(env, lpObject, JitterBufferPacketFc.user_data, (jint)lpStruct->user_data);
}

