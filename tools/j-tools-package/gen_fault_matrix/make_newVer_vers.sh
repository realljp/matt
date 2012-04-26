


if test $# -ne 9
then echo "Usage: ${0} <subject_dir> <prog> <fault_start> \
<faults_file_name> <version start> <number of versions> \
<source seeded path> <newVer_prefix> <newVer_name>"
     exit 1
fi

subject_dir=${1}
prog=${2}
fault_start=${3}
faults_file_name=${4}
ver_start=${5}
vers=${6}
source_seeded_path_prefix=${7}
newVer_prefix=${8}
newVer_name=${9}

v=${ver_start}
bound_vers=`expr ${ver_start} + ${vers}`
while (test $v -lt ${bound_vers})
    do
	f=${fault_start}
	source_seeded_path=${source_seeded_path_prefix}/v${v}/
	fault_data_file=${source_seeded_path_prefix}/v${v}/${faults_file_name}

	faults=`wc -l ${fault_data_file} | gawk -vn=1 -f nth.awk`

	mkdir -p ${newVer_prefix}/v${v}/
	newVer_file=${newVer_prefix}/v${v}/${newVer_name}
	echo ${newVer_file}
	gen_newVer ${faults} ${newVer_file}

        v=`expr $v + 1`
   done
