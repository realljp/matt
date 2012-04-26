#!/bin/sh
if test $# -ne 13
then echo "Usage: ${0} <subject_dir> <subject> <fault_start> \
<version start> <number of versions> \
<class path> <universe file name> <executable file> \
<universe multiple> <exec path> <fault_matrix prefix> <fault matrix name> \
<installation script>"
     exit 1
fi

all_diffs=`./gen_temp_file F`
subject_dir=${1}
subject=${2}
fault_start=${3}
ver_start=${4}
vers=${5}
class_path=${6}
universe_name=${7}
executable=${8}
universe_multiple=${9}
shift
exec_path=${9}
shift
fault_matrix_prefix=${9}
shift
fault_matrix_name=${9}
shift
install_script=${9}

script=`./gen_temp_file F`

current_dir=`pwd`
v=${ver_start}
bound_vers=`expr ${ver_start} + ${vers}`
while (test $v -lt ${bound_vers})
    do
	mutants_file_name=ant_v${v}.mutants.adj.number
	if test ${universe_multiple} -gt 0
	    then \
		prev=`expr $v - 1`
		universe=testplans.alt/v${v}/v${prev}.${universe_name}
	    else \
		universe=testplans/${universe_name}
	fi
	f=${fault_start}
	stored=${subject_dir}/outputs.alt/v${v}/orig

	echo "Copy outputs from Final directory"
        cp -r ${subject_dir}/outputs.alt/Final/v${v} ${subject_dir}/outputs.alt/.
	echo ${mutants_file_name}
	cp ${subject_dir}/scripts/MutantList/Final/mutants.v${v}/${mutants_file_name} \
	${current_dir}/.
	t_num=`cat ${subject_dir}/${universe} | wc -l`
	t_num=`expr $t_num - 1`

	rm -f ${all_diffs}

	f=${fault_start}
        while read LINE
        do
	    echo $LINE
	    n=0
	    touch ${all_diffs}
	    while (test $n -lt ${t_num})
    	    do
       		exp=0
       		nplus=`expr $n + 1`
       		echo testID $nplus
       		if (cmp -s ${subject_dir}/outputs.alt/v${v}/$LINE/t${nplus} \
		${subject_dir}/outputs.alt/v${v}/orig/t${nplus}) 
		then
               		exp=0
       		else
               		exp=1
       		fi
       		echo testNumber $n
       		echo "Version:${f} Test:${n} Exposed:${exp}" >> ${all_diffs}
       		n=`expr $n + 1`
   	    done

	    f=`expr $f + 1`

	done < ${mutants_file_name}

	fault_matrix_dir=${fault_matrix_prefix}/v${v}
        mkdir -p ${fault_matrix_dir}
	fault_matrix=${fault_matrix_dir}/${fault_matrix_name}
	echo universe ${subject_dir}/${universe}
	./combine_fault_data ${all_diffs} ${subject_dir}/${universe} ${fault_matrix}
	rm -f ${all_diffs}
        v=`expr $v + 1`
   done
