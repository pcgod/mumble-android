#!/bin/sh

javac -cp ../lib/hawtjni-runtime-1.1-SNAPSHOT.jar ../../src/org/pcgod/mumbleclient/jni/Native.java
java -cp hawtjni-generator-1.1-SNAPSHOT.jar org.fusesource.hawtjni.generator.HawtJNI -o new ../../src
