#!/bin/bash

# Set up the necessary paths
export JAVA_HOME=/usr/lib/jvm/java-6-openjdk
export HOME=/home/pablo
export experiment_root=/home/pablo/matt
export case_example_root=$experiment_root/case_examples
export sofya_dir=$experiment_root/tools/sofya

# Set the case example we want to analyze
export ce=a3

# Set the number of mutants that we want to generate
export num_mutants=5

# Update the classpath
unset CLASSPATH
CLASSPATH=${experiment_root}/tools
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/sofya.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/commons-collections.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/sofya/lib/trove/lib/trove.jar
CLASSPATH=$CLASSPATH:${experiment_root}/tools/bcel-5.2/bcel-5.2.jar
CLASSPATH=$CLASSPATH:${JAVA_HOME}/lib/tools.jar:.
export CLASSPATH

# Generate a folder for the program to store the data in the sofyadb
if [ ! -d $HOME/.sofyadb/${ce} ]
then
    mkdir -p $HOME/.sofyadb/${ce}
else
    rm -rf $HOME/.sofyadb/${ce}/*
fi

# Copy the file containing the list of clases to the respective directory inside sofyadb
cp ${experiment_root}/case_examples_data/${ce}.prog.lst /home/pablo/.sofyadb/${ce}/${ce}.prog.lst

# Generate a CFG from the list of classes. A .java.cf and a .map file will be created and stored in .sofyadb/${ce}
#${JAVA_HOME}/bin/java -mx992m sofya.graphs.cfg.jCFG -tag ${ce} ${ce}.prog

#With the data generated above we instrument the classes
#${JAVA_HOME}/bin/java -mx992m sofya.ed.cfInstrumentor -tag ${ce} -t junit -BC ${ce}.prog

#  Create (or clean) a folder for the program inside the mutants directory
if [ ! -d ${experiment_root}/mutants/${ce} ]
then
	mkdir -p ${experiment_root}/mutants/${ce}
else
    rm -rf ${experiment_root}/mutants/${ce}/*
fi


# Beginning of the while statement here...
n=1
while(test ${n} -le ${num_mutants})
do
	echo mutant $n

	#  Create (or clean) a folder for the mutant
	if [ ! -d ${experiment_root}/mutants/${ce}/${n} ]
	then
	mkdir -p ${experiment_root}/mutants/${ce}/${n}
	else
	rm -rf ${experiment_root}/mutants/${ce}/${n}/*
	fi

	#  Create (or clean) a folder for the outputs
	if [ ! -d ${experiment_root}/outputs/${ce}/${n} ]
	then
	mkdir -p ${experiment_root}/outputs/${ce}/${n}
	else
	rm -rf ${experiment_root}/outputs/${ce}/${n}/*
	fi

	cp -r $case_example_root/$ce/* ${experiment_root}/mutants/${ce}/${n}/.

	# Copy the mutation configuration file
	cp ${experiment_root}/mutator.config ${experiment_root}/mutants/${ce}/${n}/.

	# Copy a list of files in a raw format (only the name of the classes)
	cp ${experiment_root}/case_examples_data/${ce}_raw.prog.lst ${experiment_root}/mutants/${ce}/${n}/.
	cp ${experiment_root}/case_examples_data/${ce}_path.prog.lst ${experiment_root}/mutants/${ce}/${n}/.

	# Goto the the mutant directory
	cd ${experiment_root}/mutants/${ce}/${n}

	
	# Execute the MutationGenerator class inCLASSPATH=$CLASSPATH:/home/pablo/matt/tools/junitSelection/build/junit order to get all the available mutation for each class
	# A .mut file will be created for each class
	java sofya.mutator.MutationGenerator -tag ${ce} -c mutator.config ${ce}.prog

	# Genenate a table with the feasible changes given the class (it uses the .mut file)
	cat ${ce}_raw.prog.lst |while read line; do java sofya.viewers.MutationTableViewer ${line}.mut > ${line}.table_mut; done

	# Execute the mutator for each class  
	# A .mut.apl file will be created
	echo applying mutator
	#cat ${ce}_path.prog.lst |while read line; do java sofya.mutator.Mutator -all -suffix hola ${experiment_root}/mutants/${ce}/${line}; done
	cat ${ce}_raw.prog.lst |while read line; do java sofya.mutator.Mutator -random 1 ${line}; done

	echo the tables...
	cat ${ce}_raw.prog.lst |while read line; do java sofya.viewers.MutationTableViewer ${line}.mut.apl > ${line}.table_mutapl; done

	
	export CLASSPATH=$CLASSPATH:$experiment_root/tools/junitSelection
	export CLASSPATH=$CLASSPATH:$experiment_root/tools/junitSelection/lib/junit.jar
	export CLASSPATH=$CLASSPATH:$experiment_root/tools/junitSelection/build
	export CLASSPATH=$CLASSPATH:$experiment_root/tools/junitSelection/build/junit
	export CLASSPATH=$CLASSPATH:$experiment_root/test_cases/${ce}/
	#export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/sofya.jar
	#export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/commons-collections.jar
	#export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/sofya/lib/trove/lib/trove.jar
	#export CLASSPATH=$CLASSPATH:/home/pablo/matt/tools/bcel-5.2/bcel-5.2.jar
	#export JAVA_HOME=/usr/lib/jvm/java-6-openjdk

	#echo  executing 2 test cases
	$JAVA_HOME/bin/java  junit.textui.SelectiveTestRunner -sID 1-3 -o ${experiment_root}/outputs/${ce}/${n} Suite  -univ ${experiment_root}/outputs/${ce}/${n}/universal.txt

	
	n=`expr ${n} + 1`
done 