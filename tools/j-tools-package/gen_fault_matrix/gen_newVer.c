#include <stdio.h>
#include <stdlib.h>

main(int argc, char * * argv)
{
  int f, faults;
  char * output = NULL;
  FILE * file;

  if (argc != 3)
    {
      printf("%s <number of faults> <output newVer file>\n", argv[0]);
      return 0;
    }

  sscanf(argv[1], "%i", &faults);
  output = argv[2];

  file = fopen(output, "w");
  if (file == NULL)
    {
      printf("Cannot open file %s for writing\n", output);
      exit(-1);
    }

  fprintf(file, "Version=1 Faults=%i: ", faults);
  for (f = 0; f < faults; f++)
    fprintf(file, " 1");
  fprintf(file, "\n");

  return 0;
}
