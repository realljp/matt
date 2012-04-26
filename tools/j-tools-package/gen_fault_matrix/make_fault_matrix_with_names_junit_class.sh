#!/bin/sh
if test $# -lt 8
then echo "Usage: ${0} <subject name> <subject installation script> <version start> <number of versions> <class path> <test universe file> <executable file> <fault matrix file> [<line_adjust>]"
     exit 1
else
     if test $# -gt 9
     then echo "Usage: ${0} <subject name> <subject installation script> <version start> <number of versions> <class path> <test universe file> <executable file> <fault matrix file> [<line_adjust>]"
	exit 1
     fi
fi

subject=${1}
install_script=${2}
subject_dir=${experiment_root}/${subject}
faults_file_name=FaultSeeds.h
fault_start=1
ver_start=${3}
vers=${4}
fault_matrix_prefix=${subject_dir}/info
fault_matrix_name=${8}
if test ${9}
then
	line_adjust=${9}
else
	line_adjust=false
fi

echo line_adjust $line_adjust
class_path=${5}
universe_name=${6}
executable=${7}
echo executable $executable
universe_multiple=1
exec_path=${subject_dir}/source
get_data_for_fault_matrix_junit_class.sh ${subject_dir} ${subject} ${fault_start} \
	${faults_file_name} ${ver_start} ${vers} "${class_path}" \
	${universe_name} "${executable}" ${universe_multiple} \
	${exec_path} ${fault_matrix_prefix} ${fault_matrix_name} \
	${install_script} ${line_adjust}
