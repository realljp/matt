#include <stdio.h>
#include <stdlib.h>
#include "defs.h"

#define MAX(x, y) ((x) > (y) ? (x) : (y))

int main(int argc, char * * argv)
{
  int i, j, tests, faults, exposed, counter;
  char * input;

  if (argc != 2)
    {
      printf("%s <fault matrix>\n", argv[0]);
      exit(-1);
    }

  input = argv[1];

  read_matrix(input);

  faults = number_of_versions();
  tests = number_of_tests();

  for (j = 1; j <= faults; j++)
    {
      printf("--------------------------------\n");
      printf("Statistics for fault %i:\n", j);
      printf("Tests which expose this fault: ");
      exposed = 0;
      counter = 0;
      for (i = 0; i < tests; i++)
	{
	  if (fault_exposed(i, j))
	    {
	      exposed++;
	      if ((counter % 10) == 0)
		printf("\n\t");
	      counter++;
	      printf("%i ", i);
	    }
	}
      printf("\nPercentage of tests which expose this fault is %.5lf %c\n", 
	     100.0 * ((double) exposed) / tests, '%');
    }

  printf("--------------------------------\n");

  return 0;
}
