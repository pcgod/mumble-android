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
LOCAL_CFLAGS		:= -I$(LOCAL_PATH) -Drestrict='' -D__EMX__ -DFIXED_POINT -DHAVE_LRINTF -DHAVE_LRINT -DDOUBLE_PRECISION -O3
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PATH				:= $(ROOT)/celt_wrapper
LOCAL_MODULE			:= libcelt_interface
LOCAL_SRC_FILES 		:= celt_wrap.c
LOCAL_CFLAGS			:= -I$(LIBPATH) -O3
LOCAL_STATIC_LIBRARIES	:= libcelt

include $(BUILD_SHARED_LIBRARY)
