

if test $# -ne 2
then echo "Usage: ${0} <prog> <number of versions>"
     exit 1
fi

prog=${1}
subject_dir=${experiment_root}/subjects/${prog}
faults_file_name=FaultSeeds.h
fault_start=1
ver_start=1
vers=${2}
newVer_prefix=${subject_dir}/info/
newVer_name=newVer.${prog}

source_seeded_path_prefix=${subject_dir}/versions.alt/versions.seeded/

make_newVer_vers.sh ${subject_dir} ${prog} ${fault_start} \
    ${faults_file_name} ${ver_start} ${vers} ${source_seeded_path_prefix} \
    ${newVer_prefix} ${newVer_name}
