# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
ROOT := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PATH			:= $(ROOT)/celt/libcelt
LOCAL_MODULE		:= libcelt
LOCAL_SRC_FILES		:= bands.c			celt.c				cwrs.c				dump_modes.c \
					   entcode.c		entdec.c			entenc.c			header.c \
					   kiss_fft.c		laplace.c			mdct.c				modes.c \
					   pitch.c			quant_bands.c		rangedec.c			rangeenc.c \
					   rate.c			testcelt.c			vq.c
LOCAL_CFLAGS		:= -I$(LOCAL_PATH) -I$(ROOT)/celt_wrapper -DHAVE_CONFIG_H -fvisibility=hidden
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH 			:= $(ROOT)/speex/libspeex
LOCAL_MODULE		:= libspeex
LOCAL_SRC_FILES		:= cb_search.c		exc_10_32_table.c 	exc_8_128_table.c 	filters.c \
					   gain_table.c 	hexc_table.c 		high_lsp_tables.c 	lsp.c \
					   ltp.c			speex.c 			stereo.c 			vbr.c \
					   vq.c bits.c 		exc_10_16_table.c	exc_20_32_table.c 	exc_5_256_table.c \
					   exc_5_64_table.c	gain_table_lbr.c 	hexc_10_32_table.c	lpc.c \
					   lsp_tables_nb.c 	modes.c 			modes_wb.c 			nb_celp.c \
					   quant_lsp.c		sb_celp.c			speex_callbacks.c 	speex_header.c \
					   window.c			resample.c			jitter.c
LOCAL_CFLAGS		:= -I$(LOCAL_PATH)/../include -D__EMX__ -DFIXED_POINT -DEXPORT=''
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH				:= $(ROOT)/new
LOCAL_MODULE			:= libnative
LOCAL_SRC_FILES			:= hawtjni.c hawtjni_native.c hawtjni_native_stats.c hawtjni_native_structs.c
LOCAL_CFLAGS			:= -I$(ROOT)/celt/libcelt -fvisibility=hidden
LOCAL_STATIC_LIBRARIES	:= libcelt libspeex

include $(BUILD_SHARED_LIBRARY)
