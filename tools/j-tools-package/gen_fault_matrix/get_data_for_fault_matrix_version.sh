
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
make

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
