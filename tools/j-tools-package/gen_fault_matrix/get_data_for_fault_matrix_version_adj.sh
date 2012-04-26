
if test $# -ne 10 
then echo "Usage: ${0} <subject_dir> <subject> <fault dir> <faults file> <fault number> <version> <universe> <all diffs> <exec path> <stored>"
     exit 1
fi

script=`gen_temp_file F`
diffs=`gen_temp_file F`
ascript=`gen_temp_file F`
subject_dir=${1}
executable=${2}
fault_dir=${3}
faults_file=${4}
fault_number=${5}
version=${6}
universe=${7}
all_diffs=${8}
exec_path=${9}
shift
stored=${9}

num_tests=`cat ${universe} | wc -l`
num_tests=`expr $num_tests - 1`

echo Compiling faulty version

current_dir=`pwd`
cp EqualizeLineNumbers*.class ${exec_path}/. 
cd ${exec_path}
cp orig_FaultSeeds.h FaultSeeds.h

# turn off previous fault
find ./ -name "*.cpp" >  __tmpfile
while read LINE
do
        java EqualizeLineNumbers $LINE 0 `echo $LINE | sed "s/\.cpp/\.java/"`
done < __tmpfile
rm __tmpfile

fault_id=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=1 FaultSeeds.h  | head -${fault_number} | tail -1`
fault_num=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=2 FaultSeeds.h  | head -${fault_number} | tail -1`
fault_class=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=3 FaultSeeds.h | head -${fault_number} | tail -1`
find ./ -name "$fault_class" > tmpclass
while read LINE
do
	grep "#ifdef $fault_id" $LINE > grep_tmp
	grep "#ifndef $fault_id" $LINE >> grep_tmp
	cat ${grep_tmp}
	if [ -s grep_tmp ]
	then
        	java EqualizeLineNumbers $LINE ${fault_num} `echo $LINE | sed "s/\.cpp/\.java/"`
	else
		echo No fault is defined ...
	fi
done < tmpclass

rm tmpclass

#############################################################################
##   The following line compiles java files after turning one fault on     ##   
##   This works for jtopas and Siena.                                      ##
##   If you are working on different subject, then adjust this line for it.## 
#############################################################################
make
cd ${current_dir}
mkdir -p ${subject_dir}/scripts
javamts ${subject_dir} ${executable} ${universe} D ${script} NULL ${stored} NULL

touch ${all_diffs}

n=0
while (test $n -lt ${num_tests})
    do
	echo Doing for test ${n}
	nplus=`expr $n + 1`
	gawk -f extract_from_script.awk -vnum_test=${n} ${script} > ${ascript}
	touch ${ascript}
	current_dir=`pwd`
	cd ${subject_dir}/scripts
	chmod u+x ${ascript}
	rm -f -r ${subject_dir}/outputs
	mkdir -p ${subject_dir}/outputs
	${ascript} > ${diffs}
	cd ${current_dir}
	temp1=`gawk -f process_diffs.awk ${diffs}`
	echo "Version:${fault_number}" ${temp1} >> ${all_diffs}
	n=${nplus}
    done

rm -f ${script} ${diffs} ${ascript}

echo Done for fault ${fault_number} and version ${version}
