#!/bin/csh -f


if (${#argv} != 4) then
 echo "$0 <root 1> <root 2> <subdir> <diff file>"
 exit
endif

set root1=$1
set root2=$2
set subdir=$3
set diff_file=$4
set current_dir=`pwd`
cd ${root1}/${subdir}
set list=`ls`
set n=${#list}

set i=1
while ($i <= $n)
 set file=${list[$i]}
 if (-d ${file}) then
  set dir=${subdir}/${file}
  cd ${current_dir} 
  adiff_dir.sh ${root1} ${root2} ${dir} ${diff_file}
  cd ${root1}/${subdir}
 else
  set flag1=`echo ${file} | gawk '{if ($0 ~ /\.c/) print "YES"; else print "NO";}'`
  set flag2=`echo ${file} | gawk '{if ($0 ~ /\.int\.c/) print "NO"; else print "YES";}'`
  if (((${flag1} != "YES") && (${flag1} != "NO")) || ((${flag2} != "YES") && (${flag2} != "NO"))) then
   echo "Error in processing"
   exit
  endif
  if ((${flag1} == "YES") && (${flag2} == "YES")) then
   set cfile1=${root1}/${subdir}/${file}
   set cfile2=${root2}/${subdir}/${file}
   if ((-e ${cfile1}) || (-e ${cfile2})) then
    echo Diffing ${cfile1} and ${cfile2}
    echo "Comparing files ${cfile1} and ${cfile2}" >> ${diff_file}
    ${current_dir}/adiff ${cfile1} ${cfile2} >> ${diff_file}
   endif
  endif
 endif

 @ i = $i + 1
end
