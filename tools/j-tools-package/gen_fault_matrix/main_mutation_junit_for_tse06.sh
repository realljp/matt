#!/bin/sh
if test $# -ne 7
then echo "Usage: ${0} <subject name> <subject installation script> <version start> <number of versions> <test universe file> <executable file> <fault matrix file>"
     exit 1
fi

## This script generates mutation-matrix by comparing outputs
## between orig and Mk. 
## Outputs are already generated in the class-level during mutant generation. 
## Require xml_v3.mutants.adj.number

subject=${1}
install_script=${2}
subject_dir=${experiment_root}/${subject}
fault_start=1
ver_start=${3}
vers=${4}
fault_matrix_prefix=${subject_dir}/info
fault_matrix_name=${7}

universe_name=${5}
executable=${6}
echo executable $executable
universe_multiple=1
exec_path=${subject_dir}/source
sh get_data_mutation_junit_for_tse06.sh ${subject_dir} ${subject} ${fault_start} \
	${ver_start} ${vers} ${universe_name} "${executable}" ${universe_multiple} \
	${exec_path} ${fault_matrix_prefix} ${fault_matrix_name} ${install_script}
