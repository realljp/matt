#!/bin/sh
if test $# -ne 15
then echo "Usage: ${0} <subject_dir> <subject> <fault_start> \
<faults_file_name> <version start> <number of versions> \
<class path> <universe file name> <executable file> \
<universe multiple> <exec path> <fault_matrix prefix> <fault matrix name> \
<installation script> <line_adjust>"
     exit 1
fi

all_diffs=`gen_temp_file F`
subject_dir=${1}
subject=${2}
fault_start=${3}
faults_file_name=${4}
ver_start=${5}
vers=${6}
class_path=${7}
universe_name=${8}
executable=${9}
shift
universe_multiple=${9}
shift
exec_path=${9}
shift
fault_matrix_prefix=${9}
shift
fault_matrix_name=${9}
shift
install_script=${9}
shift
line_adjust=${9}

script=`gen_temp_file F`

v=${ver_start}
bound_vers=`expr ${ver_start} + ${vers}`
while (test $v -lt ${bound_vers})
    do
	if test ${universe_multiple} -gt 0
	    then \
		prev=`expr $v - 1`
		universe=testplans.alt/v${v}/v${prev}.${universe_name}
		testId=${subject_dir}/testplans.alt/v${v}/v${prev}.prio.junit.testId
	    else \
		universe=testplans/${universe_name}
	fi
	f=${fault_start}
	rm -f ${fault_data}
	fault_data_file=${exec_path}/${faults_file_name}
	stored=${subject_dir}/outputs.alt/v${v}
	rm ${exec_path}/${faults_file_name}

	current_dir=`pwd`
#########################################################
##   Modify the following line to install your subject ##
#########################################################
	cd ${subject_dir}/scripts
	${subject_dir}/scripts/${install_script} ${v}

	cd ${exec_path}
	find ${exec_path}/ -name ${faults_file_name} > tmp_f 
	while read LINE
	do
		cp $LINE ${faults_file_name}
	done < tmp_f
	cp ${faults_file_name} orig_FaultSeeds.h
	fault_dir=`cat tmp_f`
	rm tmp_f
	faults=`wc -l ${faults_file_name} | gawk -vn=1 -f ${current_dir}/nth.awk`
	mkdir -p ${subject_dir}/scripts
	cd ${subject_dir}/scripts
	rm -f ${script}
	echo Running script
	rm -f -r ${subject_dir}/outputs
	mkdir -p ${subject_dir}/outputs
	CLASSPATH=${class_path}
	export CLASSPATH 
        if test ${v} -gt 3
        then
            if test ${v} -ne 7
            then
                JAVA_HOME=/nfs/spectre/a4/solaris8/common/j2sdk1_3_1_02
                export JAVA_HOME
            fi
	else
	  	JAVA_HOME=/usr/local/common/j2sdk1.4.1
		export JAVA_HOME
        fi
#	echo $CLASSPATH
	rm -f -r ${stored}
	mkdir -p ${stored}
	cd ${subject_dir}/${executable}
	echo Run script 
	cp ${subject_dir}/scripts/TestScripts/scriptR${v}_prev.cls .
        chmod u+x scriptR${v}_prev.cls
        scriptR${v}_prev.cls
	cd ${current_dir}
	rm -f ${script}
	cp -r ${subject_dir}/outputs/* ${stored}
	echo Script is done

	rm -f ${all_diffs}

	f=${fault_start}
	bound_faults=`expr ${fault_start} + ${faults}`
	while (test $f -lt ${bound_faults})
	    do
		if test ${line_adjust} -eq 1
		then
			get_data_for_fault_matrix_version_adj_junit_class.sh ${subject_dir} \
			"${executable}" ${fault_dir} \
			${fault_data_file} ${f} ${v} \
			${universe} ${all_diffs} ${exec_path} ${stored} \
			"${class_path}"
		else
			get_data_for_fault_matrix_version_junit.sh ${subject_dir} \
			"${executable}" ${fault_dir} \
			${fault_data_file} ${f} ${v} \
			${testId} ${all_diffs} ${exec_path} ${stored} \
			"${class_path}"
		fi
		f=`expr $f + 1`
	    done
	fault_matrix_dir=${fault_matrix_prefix}/v${v}
        mkdir -p ${fault_matrix_dir}
	fault_matrix=${fault_matrix_dir}/${fault_matrix_name}
	echo universe ${subject_dir}/${universe}
	combine_fault_data ${all_diffs} ${subject_dir}/${universe} ${fault_matrix}
	rm -f ${all_diffs}
        v=`expr $v + 1`
   done
