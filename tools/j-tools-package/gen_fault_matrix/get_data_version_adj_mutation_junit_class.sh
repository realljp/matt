
if test $# -ne 9 
then echo "Usage: ${0} <subject_dir> <subject> <fault number> <version> <testId> <all diffs> <exec path> <stored> <classpath>"
     exit 1
fi

script=`./gen_temp_file F`
diffs=`./gen_temp_file F`
ascript=`./gen_temp_file F`
subject_dir=${1}
executable=${2}
fault_number=${3}
version=${4}
testId=${5}
all_diffs=${6}
exec_path=${7}
stored=${8}
classpath=${9}

current_dir=`pwd`

cd ${subject_dir}/${executable}
touch ${all_diffs}
rm -f -r ${subject_dir}/outputs
mkdir -p ${subject_dir}/outputs
t_num=`cat ${testId} | wc -l` 
cat ${testId} | sh runTests.sh junit.textui.SelectiveTestRunner -o ${subject_dir}/outputs
cd ${current_dir}

n=0
while read LINE
    do
#	echo Doing for test ${n}
	exp=0
	nplus=$LINE
	echo testTD $nplus
	current_dir=`pwd`
	cd ${subject_dir}/scripts
	cd ${current_dir}
        if (cmp -s ${subject_dir}/outputs/t${nplus} ${subject_dir}/outputs.alt/v${version}/orig/t${nplus}) then
               	exp=0
	   else
		exp=1
        fi
	echo testNumber $n
        echo "Version:${fault_number} Test:${n} Exposed:${exp}" >> ${all_diffs}
	n=`expr $n + 1`
    done < ${testId}

rm -f ${script} ${diffs} ${ascript}

echo Done for fault ${fault_number} and version ${version}
