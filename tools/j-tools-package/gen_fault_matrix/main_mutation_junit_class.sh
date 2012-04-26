#!/bin/sh
if test $# -ne 8
then echo "Usage: ${0} <subject name> <subject installation script> <version start> <number of versions> <class path> <test universe file> <executable file> <fault matrix file>"
     exit 1
fi

subject=${1}
install_script=${2}
subject_dir=${experiment_root}/${subject}
#mutants_file_name=jtopas_v${3}.adj.mutants
fault_start=1
ver_start=${3}
vers=${4}
fault_matrix_prefix=${subject_dir}/info
fault_matrix_name=${8}

class_path=${5}
universe_name=${6}
executable=${7}
echo executable $executable
universe_multiple=1
exec_path=${subject_dir}/source
sh get_data_mutation_junit_class.sh ${subject_dir} ${subject} ${fault_start} \
	${ver_start} ${vers} "${class_path}" \
	${universe_name} "${executable}" ${universe_multiple} \
	${exec_path} ${fault_matrix_prefix} ${fault_matrix_name} \
	${install_script}
