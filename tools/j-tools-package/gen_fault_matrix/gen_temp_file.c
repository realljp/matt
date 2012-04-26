#include <stdio.h>
#include <stdlib.h>

main(int argc, char * * argv)
{
  char * name;
  int dir, flag;
  char command[1024];
  FILE * f;
  int counter;

  if (argc < 2)
    {
      printf("%s <type>\n", argv[0]);
      printf("<type> is D (directory) or F (file)\n");
      exit(-1);
    }

  if (strlen(argv[1]) != 1)
    {
      printf("Invalid argument \n");
      exit(-1);
    }

  switch(argv[1][0])
    {
    case 'D':
      dir = 1;
      break;

    case 'F':
      dir = 0;
      break;

    default:
	printf("Invalid argument \n");
	exit(-1);
    }

  counter = 0;
  do
    {
      if (counter > 10)
	{
	  printf("Unable co create temporary file\n");
	  exit(-1);
	}

      flag = 0;
      name = tempnam("/tmp/", "prio_temp_");
      if (dir)
	{
	  sprintf(command, "mkdir -p %s", name);
	  system(command);
	}
      else
	{
	  f = fopen(name, "w");
	  if (f == NULL)
	    flag = 1;
	  else
	    fclose(f);
	}
      counter++;
    }
  while(flag);

  printf("%s\n", name);
  return 0;
}
