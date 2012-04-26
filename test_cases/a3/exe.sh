#!/bin/sh

unset  CLASSPATH;
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junit
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junit3.8.1.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/sofya.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/commons-collections.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/trove/lib/trove.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/bcel-5.2/bcel-5.2.jar
export JAVA_HOME=/usr/lib/jvm/java-6-openjdk

echo 'compiling test...'
$JAVA_HOME/bin/javac BinStringTest.java

echo 'compiling suite...'
$JAVA_HOME/bin/javac Suite.java

unset  CLASSPATH;
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junitSelection
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junitSelection/lib/junit.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junitSelection/build
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junitSelection/build/junit
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/sofya.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/commons-collections.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/trove/lib/trove.jar
export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/bcel-5.2/bcel-5.2.jar
export JAVA_HOME=/usr/lib/jvm/java-6-openjdk

#echo 'obtaining the names of the test cases '
#$JAVA_HOME/bin/java  junit.textui.SelectiveTestRunner -names Suite 

#echo 'executing 2 test cases '
#$JAVA_HOME/bin/java  junit.textui.SelectiveTestRunner -sID 1-3 -o /home/pablo/junit_pruebas Suite  -univ Uni.txt
