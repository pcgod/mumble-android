#!/usr/bin/env python
# Copyright (C) 2008 The Android Open Source Project
# License at http://www.apache.org/licenses/LICENSE-2.0

import os, string

SYMBOLS_DIR = ""
NDK_ROOT = "~/android-ndk-r4b"

# returns a list containing the function name and the file/lineno
def CallAddr2Line(lib, addr):
	uname = os.uname()[0]
	if uname == "Darwin":
		proc = os.uname()[-1]
		if proc == "i386":
			uname = "darwin-x86"
		else:
			uname = "darwin-ppc"
	elif uname == "Linux":
		uname = "linux-x86"

	if lib != "":
		cmd = NDK_ROOT + "/build/prebuilt/" + uname\
			+ "/arm-eabi-4.4.0/bin/arm-eabi-addr2line"\
			+ " -f -e " + SYMBOLS_DIR + lib\
			+ " 0x" + addr
		stream = os.popen(cmd)
		lines = stream.readlines()
		list = map(string.strip, lines)
	else:
		list = []
		if list != []:
			# Name like "move_forward_type<JavaVMOption>" causes troubles
			mangled_name = re.sub('<', '\<', list[0]);
			mangled_name = re.sub('>', '\>', mangled_name);
			cmd = NDK_ROOT + "/build/prebuilt/" + uname + "/arm-eabi-4.2.1/bin/arm-eabi-c++filt "\
				+ mangled_name
			stream = os.popen(cmd)
			list[0] = stream.readline()
			stream.close()
			list = map(string.strip, list)
		else:
			list = [ "(unknown)", "(unknown)" ]
	return list

lib = '../obj/local/armeabi/libnative.so';
#addrs = ['0000346e', '00002eda']
addrs = ['00010b76', '00002ee4']

print "ADDR      FUNCTION                        FILE:LINE"
for x in addrs:
	func, file = CallAddr2Line(lib, x)
	print "{0:=08x}  {1:<30}  {2}".format(int(x, 16), func, file)
