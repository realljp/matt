#!/bin/sh
if test $# -ne 15
then echo "Usage: ${0} <subject_dir> <subject> <fault_start> \
<faults_file_name> <version start> <number of versions> \
<universe path> <universe file name> <executable file> \
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
universe_path=${7}
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
		universe=${universe_path}/v${v}/${universe_name}
	    else \
		universe=${universe_path}/${universe_name}
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
	${subject_dir}/scripts/${install_script} seeded ${v}

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
	javamts ${subject_dir} ${executable} ${universe} R ${script} NULL NULL NULL
	echo Running script
	rm -f -r ${subject_dir}/outputs
	mkdir -p ${subject_dir}/outputs
	chmod u+x ${script}
	${script}

	cd ${current_dir}
	rm -f ${script}
	rm -f -r ${stored}
	mkdir -p ${stored}
	cp -r ${subject_dir}/outputs/* ${stored}
	echo Script is done

	rm -f ${all_diffs}

	f=${fault_start}
	bound_faults=`expr ${fault_start} + ${faults}`
	while (test $f -lt ${bound_faults})
	    do
		if test ${line_adjust} -eq 1
		then
			get_data_for_fault_matrix_version_adj.sh ${subject_dir} \
			${executable} ${fault_dir} \
			${fault_data_file} ${f} ${v} \
			${universe} ${all_diffs} ${exec_path} ${stored}
		else
			get_data_for_fault_matrix_version.sh ${subject_dir} \
			${executable} ${fault_dir} \
			${fault_data_file} ${f} ${v} \
			${universe} ${all_diffs} ${exec_path} ${stored}
		fi
		f=`expr $f + 1`
	    done
	fault_matrix_dir=${fault_matrix_prefix}/v${v}
        mkdir -p ${fault_matrix_dir}
	fault_matrix=${fault_matrix_dir}/${fault_matrix_name}
	combine_fault_data ${all_diffs} ${universe} ${fault_matrix}
	rm -f ${all_diffs}
        v=`expr $v + 1`
   done
