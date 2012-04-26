
if test $# -ne 11 
then echo "Usage: ${0} <subject_dir> <subject> <fault dir> <faults file> <fault number> <version> <testId> <all diffs> <exec path> <stored> <classpath>"
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
testId=${7}
all_diffs=${8}
exec_path=${9}
shift
stored=${9}
shift
classpath=${9}

echo Compiling faulty version

current_dir=`pwd`
cd ${exec_path}
cp orig_FaultSeeds.h FaultSeeds.h
cp FaultSeeds.h tmp_fault

fault_id=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=1 tmp_fault | head -${fault_number} | tail -1`
echo Turn the fault-id $fault_id on
fault_num=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=2 tmp_fault | head -${fault_number} | tail -1`
fault_class=`gawk -f ${current_dir}/extract_fault_file.awk -varg_num=3 tmp_fault| head -${fault_number} | tail -1`

# get fault_id and turn it on in FaultSeeds.h 
sed -n "${fault_number},${fault_number}p" tmp_fault > Fault.h
sed "1,1s/ ${fault_num}//" Fault.h > tmp_fault 
sed "1,1s/${fault_class}//" tmp_fault > Fault.h 
sed "1,1s/${fault_id}/#define ${fault_id}/" Fault.h > ${fault_dir} 
rm Fault.h tmp_fault

find ./ -name "*.cpp" >  __tmpfile
cp __tmpfile tmpcpp
while read LINE
do
	ls $LINE | sed "s/\.cpp//" > tmp_java 
	tmp_java_file=`cat tmp_java`
	cp ${tmp_java_file}.java ${tmp_java_file}.tmpjava
	gcc -E -P $LINE > `echo $LINE | sed "s/\.cpp/\.java/"`
done < __tmpfile
rm __tmpfile
#############################################################################
##   The following line compiles java files after turning one fault on     ##   
##   This works for jtopas and Siena.                                      ##
##   If you are working on different subject, then adjust this line for it.## 
#############################################################################
cur_wd_432532=`pwd`
cd ${subject_dir}/scripts
build.sh ${version}
cd ${cur_wd_432532}

# turn the fault off 
find ./ -name "*.tmpjava" >  __tmpfile
while read LINE
do
	ls $LINE | sed "s/\.tmpjava//" > tmpjava 
	tmp_java_file=`cat tmpjava`
	cp ${tmp_java_file}.tmpjava ${tmp_java_file}.java
done < __tmpfile
rm __tmpfile

cd ${current_dir}
mkdir -p ${subject_dir}/scripts
CLASSPATH=${classpath}
export CLASSPATH
touch ${all_diffs}
rm -f -r ${subject_dir}/outputs
mkdir -p ${subject_dir}/outputs
#java junit.textui.SelectiveTestRunner -count ${executable} > test_num
#t_num=`cat test_num`
cd ${subject_dir}/${executable}
#t_num=`runTests.sh junit.textui.SelectiveTestRunner -count | /usr/bin/sed -e '$!d'`
#java junit.textui.SelectiveTestRunner -o ${subject_dir}/outputs -sID 1-${t_num} ${executable}
cat ${testId} | runTests.sh junit.textui.SelectiveTestRunner -o ${subject_dir}/outputs
cd ${current_dir}

n=0
while (test $n -lt ${t_num})
    do
#	echo Doing for test ${n}
	exp=0
	nplus=`expr $n + 1`
	current_dir=`pwd`
	cd ${subject_dir}/scripts
	cd ${current_dir}
	echo $nplus
	if (cmp -s ${subject_dir}/outputs/t${nplus} ${subject_dir}/outputs.alt/v${version}/t${nplus}) then
		exp=0
	else
		exp=1
	fi
	echo "Version:${fault_number} Test:${n} Exposed:${exp}" >> ${all_diffs}
	n=${nplus}
    done

rm -f ${script} ${diffs} ${ascript}

echo Done for fault ${fault_number} and version ${version}
