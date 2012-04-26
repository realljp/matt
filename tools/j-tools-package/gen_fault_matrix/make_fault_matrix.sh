#!/bin/sh
if test $# -lt 5
then echo "Usage: ${0} <subject> <installation script> <number of versions> <universe path> <executable file> [<line_adjust>]"
     exit 1
else
     if test $# -gt 6
     then echo "Usage: ${0} <subject> <installation script> <number of versions> <universe path> <executable file> [<line_adjust>]"
	exit 1
     fi
fi

subject=${1}
install_script=${2}
subject_dir=${experiment_root}/${subject}
faults_file_name=FaultSeeds.h
fault_start=1
ver_start=1
vers=${3}
univ_path=${4}
executable=${5}

echo subject       : $subject
echo install_script: $install_script
echo subject_dir   : $subject_dir
echo vers          : $vers
echo univ_path     : $univ_path
echo executable    : $executable    

: '
if test ${6}
then
        line_adjust=${6}
else
        line_adjust=0
fi

fault_matrix_prefix=${subject_dir}/info
fault_matrix_name=fault-matrix.tsl

universe_path=${subject_dir}/${univ_path}
universe_name=universe.tsl
universe_multiple=0
exec_path=${subject_dir}/source

get_data_for_fault_matrix.sh ${subject_dir} ${subject} ${fault_start} \
    ${faults_file_name} ${ver_start} ${vers} ${universe_path} \
    ${universe_name} ${executable} ${universe_multiple} \
    ${exec_path} ${fault_matrix_prefix} ${fault_matrix_name} \
    ${install_script} ${line_adjust}
'