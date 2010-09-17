#ifndef NATIVE_H_
#define NATIVE_H_

#include "celt.h"
#include <stdint.h>
#include <stdlib.h>
#include <jni.h>

typedef struct SpeexResamplerState SpeexResamplerState;
extern SpeexResamplerState *speex_resampler_init(unsigned int nb_channels, unsigned int in_rate, unsigned int out_rate, int quality, int *err);

typedef struct JitterBuffer JitterBuffer;
typedef struct _JitterBufferPacket JitterBufferPacket;

struct _JitterBufferPacket {
   char        *data;
   unsigned int len;
   unsigned int timestamp;
   unsigned int span;
   unsigned short sequence;
   unsigned int user_data;
};


static CELTMode *wrap_celt_mode_create(int Fs, int frame_size) {
	return celt_mode_create(Fs, frame_size, NULL);
}

static CELTEncoder *wrap_celt_encoder_create(const CELTMode *mode, int channels) {
	return celt_encoder_create(mode, channels, NULL);
}

static int wrap_celt_encode(CELTEncoder *st, jshort *pcm, unsigned char *compressed, int nbCompressedBytes) {
	return celt_encode(st, pcm, NULL, compressed, nbCompressedBytes);
}

static CELTDecoder *wrap_celt_decoder_create(const CELTMode *mode, int channels) {
	return celt_decoder_create(mode, channels, NULL);
}

static int wrap_celt_decode(CELTDecoder *st, unsigned char *data, int len, short *pcm) {
	int i, res;
//	unsigned char *tmp_data = (unsigned char *)calloc(len, sizeof(unsigned char));

//	for (i = 0; i < len; ++i) {
//		tmp_data[i] = (unsigned char)data[i];
//	}

	res = celt_decode(st, data, len, pcm);
//	free(tmp_data);
	return res;
}

static int wrap_celt_decode_float(CELTDecoder *st, unsigned char *data, int len, float *pcm) {
	return celt_decode_float(st, data, len, pcm);
}

static SpeexResamplerState *wrap_speex_resampler_init(unsigned int nb_channels, unsigned int in_rate, unsigned int out_rate, int quality) {
	return speex_resampler_init(nb_channels, in_rate, out_rate, quality, NULL);
}

#endif  // NATIVE_H_
