#!/bin/bash

# Set up the necessary paths
export JAVA_HOME=/usr/lib/jvm/java-6-openjdk
export HOME=/home/pablo
export experiment_root=/home/pablo/matt
export case_example_root=$experiment_root/case_examples
export sofya_dir=$experiment_root/tools/sofya

# Set the case example we want to analyze
export ce=a3

# Update the classpath
unset CLASSPATH
CLASSPATH=${experiment_root}/tools
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/sofya.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/commons-collections.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/trove/lib/trove.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/bcel-5.2/bcel-5.2.jar
CLASSPATH=$CLASSPATH:${experiment_root}/case_examples/${ce}
CLASSPATH=$CLASSPATH:${JAVA_HOME}/lib/tools.jar:.
export CLASSPATH

# Generate a folder for the program to store the data in the sofyadb
if [ ! -d $HOME/.sofyadb/${ce} ]
then
    mkdir -p $HOME/.sofyadb/${ce}
else
    rm -rf $HOME/.sofyadb/${ce}/*
fi

# Generate a folder for the program to store the data in the sofyadb
if [ ! -d ${experiment_root}/instrumented/${ce} ]
then
    mkdir -p ${experiment_root}/instrumented/${ce}
else
    rm -rf ${experiment_root}/instrumented/${ce}/*
fi

# Copy the file containing the list of clases to the respective directory inside sofyadb
cp ${experiment_root}/case_examples_data/${ce}.prog.lst $HOME/.sofyadb/${ce}/${ce}.prog.lst
cp ${experiment_root}/case_examples_data/${ce}_path.prog.lst $experiment_root/instrumented/$ce

# Generate a CFG from the list of classes. A .java.cf and a .map file will be created and stored in .sofyadb/${ce}
${JAVA_HOME}/bin/java -mx992m sofya.graphs.cfg.jCFG -tag ${ce} ${ce}.prog

# Instrument the classes
unset CLASSPATH
CLASSPATH=${experiment_root}/tools
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/sofya.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/commons-collections.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/trove/lib/trove.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/bcel-5.2/bcel-5.2.jar
CLASSPATH=$CLASSPATH:${JAVA_HOME}/lib/tools.jar:.
export CLASSPATH

cd $experiment_root/instrumented/$ce

cat ${ce}_path.prog.lst |while read line; do cp ${experiment_root}/case_examples/${ce}/${line} ${experiment_root}/case_examples/${ce}/${line}.bkp && ${JAVA_HOME}/bin/java -mx992m sofya.ed.cfInstrumentor -tag ${ce} -t junit -BC ${experiment_root}/case_examples/${ce}/${line} && mv ${experiment_root}/case_examples/${ce}/${line} $experiment_root/instrumented/$ce/. && mv ${experiment_root}/case_examples/${ce}/${line}.bkp ${experiment_root}/case_examples/${ce}/${line}; done



