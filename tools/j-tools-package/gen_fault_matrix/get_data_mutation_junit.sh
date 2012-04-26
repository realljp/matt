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
	mutants_file_name=ant_v${v}.mutants.adj.viable
	if test ${universe_multiple} -gt 0
	    then \
		prev=`expr $v - 1`
		universe=testplans.alt/v${v}/v${prev}.${universe_name}
		testId=${subject_dir}/testplans.alt/v${v}/v${prev}.prio.junit.testId
	    else \
		universe=testplans/${universe_name}
	fi
	f=${fault_start}
	stored=${subject_dir}/outputs.alt/v${v}/orig

#########################################################
##   Modify the following line to install your subject ##
#########################################################
	cd ${subject_dir}/scripts
	${subject_dir}/scripts/${install_script} ${v}

        if [ ! -d ${subject_dir}/source_org ]
        then
            mkdir -p ${subject_dir}/source_org
        else
            rm -rf ${subject_dir}/source_org/*
        fi
        cp -r ${subject_dir}/source/ant/build/classes/org ${subject_dir}/source_org/.

        echo copy mutants 
	rm -rf ${subject_dir}/mutants
        cp -r ${subject_dir}/scripts/MutantList/Final/mutants.v${v} ${subject_dir}/mutants

        cd ${subject_dir}/mutants
	mutants=`wc -l ${mutants_file_name} | gawk -vn=1 -f ${current_dir}/nth.awk`

#	cd ${subject_dir}/scripts
	echo Running script
	rm -f -r ${subject_dir}/outputs
	mkdir -p ${subject_dir}/outputs
	CLASSPATH=${class_path}
	export CLASSPATH 
#	echo $CLASSPATH

	rm -f -r ${stored}
	mkdir -p ${stored}
	cd ${subject_dir}/${executable}
	echo `pwd`
	echo TestRunner starts
	echo ${t_num}
	cat ${testId} | sh runTests.sh junit.textui.SelectiveTestRunner -o ${subject_dir}/outputs
	cd ${current_dir}
	rm -f ${script}
	cp -r ${subject_dir}/outputs/* ${stored}
	echo Script is done

	rm -f ${all_diffs}

	f=${fault_start}
	bound_mutants=`expr ${fault_start} + ${mutants}`
        cd ${subject_dir}/mutants
        while read LINE
        do
            if [ -r $LINE ]
            then
                # change file.M* to file.class
                className=`echo $LINE | sed "s/\.M[0-9]*/\.class/"`
                echo $className
                rm -rf ${subject_dir}/source/ant/build/classes/org
                cp -r ${subject_dir}/source_org/org ${subject_dir}/source/ant/build/classes/.
                echo copy mutant $LINE
                rm -f ${subject_dir}/source/ant/build/classes/$className
                cp $LINE  ${subject_dir}/source/ant/build/classes/$className
		cd ${current_dir}
		sh get_data_version_adj_mutation_junit.sh ${subject_dir} \
		"${executable}" ${f} ${v} ${testId} ${all_diffs} \
		${exec_path} ${stored} "${class_path}"
                if [ ! -d ${subject_dir}/outputs.alt/v${v}/M${f} ]
                then
                    mkdir -p ${subject_dir}/outputs.alt/v${v}/M${f}
                else
                    rm -rf ${subject_dir}/outputs.alt/v${v}/M${f}/*
                fi
                mv ${subject_dir}/outputs/* ${subject_dir}/outputs.alt/v${v}/M${f}/.
	    fi
	    cd ${subject_dir}/mutants
	    f=`expr $f + 1`

	done < ${mutants_file_name}

	cd ${current_dir}
	fault_matrix_dir=${fault_matrix_prefix}/v${v}
        mkdir -p ${fault_matrix_dir}
	fault_matrix=${fault_matrix_dir}/${fault_matrix_name}
	echo universe ${subject_dir}/${universe}
	./combine_fault_data ${all_diffs} ${subject_dir}/${universe} ${fault_matrix}
	rm -f ${all_diffs}
        v=`expr $v + 1`
   done
