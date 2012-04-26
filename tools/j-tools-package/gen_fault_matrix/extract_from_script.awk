BEGIN {counter = -1; flag = 0; hflag = 1; num=num_test;}
{
 if (hflag)
  print $0;
 if (/CLASSPATH/)
  {
   if (hflag) hflag = 0;
   else print $0;
  }

 if (/>>>>>/) 
  {
   if (flag) exit;
   counter++;
   if (counter == num) flag = 1;
  }
 if (flag) print $0;
}
END {}
