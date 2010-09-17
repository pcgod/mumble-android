
#include "hawtjni.h"
#include "hawtjni_native_stats.h"

#ifdef NATIVE_STATS

int Native_nativeFunctionCount = 21;
int Native_nativeFunctionCallCount[21];
char * Native_nativeFunctionNames[] = {
	"celt_1decode",
	"celt_1decode_1float",
	"celt_1decoder_1create",
	"celt_1decoder_1destroy",
	"celt_1encode",
	"celt_1encoder_1create",
	"celt_1encoder_1ctl",
	"celt_1encoder_1destroy",
	"celt_1mode_1create",
	"celt_1mode_1destroy",
	"jitter_1buffer_1ctl",
	"jitter_1buffer_1destroy",
	"jitter_1buffer_1get",
	"jitter_1buffer_1get_1pointer_1timestamp",
	"jitter_1buffer_1init",
	"jitter_1buffer_1put",
	"jitter_1buffer_1tick",
	"jitter_1buffer_1update_1delay",
	"speex_1resampler_1destroy",
	"speex_1resampler_1init",
	"speex_1resampler_1process_1int",
};

#define STATS_NATIVE(func) Java_org_fusesource_hawtjni_runtime_NativeStats_##func

JNIEXPORT jint JNICALL STATS_NATIVE(Native_1GetFunctionCount)
	(JNIEnv *env, jclass that)
{
	return Native_nativeFunctionCount;
}

JNIEXPORT jstring JNICALL STATS_NATIVE(Native_1GetFunctionName)
	(JNIEnv *env, jclass that, jint index)
{
	return (*env)->NewStringUTF(env, Native_nativeFunctionNames[index]);
}

JNIEXPORT jint JNICALL STATS_NATIVE(Native_1GetFunctionCallCount)
	(JNIEnv *env, jclass that, jint index)
{
	return Native_nativeFunctionCallCount[index];
}

#endif
