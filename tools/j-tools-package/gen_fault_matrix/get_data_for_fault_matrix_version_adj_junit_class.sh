
if test $# -ne 11 
then echo "Usage: ${0} <subject_dir> <subject> <fault dir> <faults file> <fault number> <version> <universe> <all diffs> <exec path> <stored> <classpath>"
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
shift
classpath=${9}

echo Compiling faulty version

current_dir=`pwd`
cd ${exec_path}
cp orig_FaultSeeds.h FaultSeeds.h

# Remove .class files
find ./ -name "*.class" >  __tmpfile
while read LINE
do
        rm -f $LINE
done < __tmpfile
rm __tmpfile

cp ${current_dir}/EqualizeLineNumbers*.class ${exec_path}/. 
#echo CLASSPATH $CLASSPATH
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
##
echo Faulty class: $fault_class
cp tmpclass classlist
while read LINE
do
	grep "#ifdef $fault_id" $LINE > grep_tmp
	grep "#ifndef $fault_id" $LINE >> grep_tmp
	cat ${grep_tmp}
	if [ -s grep_tmp ]
	then
		##
		echo Equalizing...
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
cd ${subject_dir}/scripts
build.sh ${version}
cd ${current_dir}
mkdir -p ${subject_dir}/scripts
#CLASSPATH="$CLASSPATH":${classpath}
#export CLASSPATH
touch ${all_diffs}
rm -f -r ${subject_dir}/outputs
mkdir -p ${subject_dir}/outputs
t_num=`cat ${subject_dir}/${universe} | wc -l`
t_num=`expr $t_num - 1`
cd ${subject_dir}/${executable}
echo t_num $t_num
scriptR${version}_prev.cls
cd ${current_dir}

n=0
while (test $n -lt ${t_num})
#while read LINE
    do
#	echo Doing for test ${n}
	exp=0
	nplus=`expr $n + 1`
	echo testTD $nplus
	current_dir=`pwd`
	cd ${current_dir}
        if (cmp -s ${subject_dir}/outputs/t${nplus} ${subject_dir}/outputs.alt/v${version}/t${nplus}) then
               	exp=0
	else
		exp=1
        fi
	echo testNumber $n
        echo "Version:${fault_number} Test:${n} Exposed:${exp}" >> ${all_diffs}
	n=`expr $n + 1`
    done

rm -f ${script} ${diffs} ${ascript}

cp -r ${subject_dir}/outputs/* ${subject_dir}/outputs.alt/f${fault_number}/.

echo Done for fault ${fault_number} and version ${version}
