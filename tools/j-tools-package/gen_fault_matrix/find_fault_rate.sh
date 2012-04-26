cnt=1
rm v$1.data
rm v$1.rate
touch v$1.data
touch v$1.rate
while (test $cnt -le 30)
do
	 ./get_fault_matrix_stats ../../../info/v$1/mutants-matrix.prio.junit.universe.linux.sub.${cnt} > sub
	grep Perc sub > perc.sub
	total=`wc -l perc.sub | gawk -vn=1 -f nth.awk`
	grep " 0.000" perc.sub > zero.sub
	zero=`wc -l zero.sub | gawk -vn=1 -f nth.awk`
	echo ${total}
	echo ${zero}
	non_zero=`expr ${total} - ${zero}`
	echo ${non_zero} ${total} >> v$1.data
	cnt=`expr ${cnt} + 1`
	rm -rf sub zero.sub perc.sub
done
./average v$1.data > v$1.rate
