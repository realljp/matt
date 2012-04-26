BEGIN {number = -1; exposed = 0;}
{
 if ($0 ~ />>>>>/) 
  {
   split($3, numbers, "\"");
   number = numbers[1] - 1;
  }

  if ($0 ~ /different results/)
   exposed = 1;
}
END {print "Test:" number " Exposed:" exposed;}
