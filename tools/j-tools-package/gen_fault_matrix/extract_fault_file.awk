BEGIN {arg=arg_num;}
 {
 buffer = $0;
 split(buffer, array, " ");
 print array[arg];
 }
END {}
