#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "adiff.h"

int flag_print_all_funcs;

int flag_find_full_function;

int flag_nested_comments;

char error_message[1024];

int number_of_choices_limit = 1000;

void compare_functions(char * src1, char * src2)
{
  FILE * f;
  struct stat filestat;
  int n1, n2;
  char * buffer1, * buffer2, * buffer1_, * buffer2_;
  fentries functions1, functions2, other1, other2;
  items fitems1, fitems2;

  finit(&functions1, 10);
  finit(&functions2, 10);

  f = fopen(src1, "rt");

  if (f == NULL)
    {
      printf("File %s is missing\n", src1);
      buffer1 = Malloc(10 * sizeof(char));
      n1 = 0;
    }
  else
    {
      stat(src1, &filestat);
      n1 = filestat.st_size;
      buffer1 = Malloc((n1 + 10) * sizeof(char));
      assert(fread(buffer1, 1, n1, f) == n1);
      fclose(f);
    }

  buffer1[n1] = 0;

  f = fopen(src2, "rt");

  if (f == NULL)
    {
      printf("File %s is missing\n", src2);
      buffer2 = Malloc(10 * sizeof(char));
      n2 = 0;
    }
  else
    {
      stat(src2, &filestat);
      n2 = filestat.st_size;
      buffer2 = Malloc((n2 + 10) * sizeof(char));
      assert(fread(buffer2, 1, n2, f) == n2);
      fclose(f);
    }    

  buffer2[n2] = 0;

  if (DEBUG_EXTRACTING)
    printf("Searching for functions in the first file\n");
  find_functions(buffer1, &functions1);
  if (DEBUG_EXTRACTING)
    printf("Searching for functions in the second file\n");
  find_functions(buffer2, &functions2);

  if (DEBUG_FUNCS)
    {
      printf("Printing functions in the first file\n");
      print_functions(buffer1, &functions1);
    }
  if (DEBUG_FUNCS)
    {
      printf("Printing functions in the second file\n");
      print_functions(buffer2, &functions2);
    }

  diff_functions(buffer1, buffer2, &functions1, &functions2);

  finit(&other1, 10);
  finit(&other2, 10);

  buffer1_ = strdup(buffer1);
  assert(buffer1_ != NULL);

  buffer2_ = strdup(buffer2);
  assert(buffer2_ != NULL);

  fadd(&other1, "#DATA DECLARATIONS OUTSIDE OF FUNCTIONS#", 0, n1);
  fadd(&other2, "#DATA DECLARATIONS OUTSIDE OF FUNCTIONS#", 0, n2);

  create_items_from_functions(&fitems1, &functions1);
  create_items_from_functions(&fitems2, &functions2);
  clear(buffer1_, &fitems1);
  clear(buffer2_, &fitems2);

  diff_functions(buffer1_, buffer2_, &other1, &other2);

  Free(buffer1_);
  Free(buffer2_);
  free_items(&fitems1);
  free_items(&fitems2);
}


main(int argc, char * * argv)
{
  int i;

  if (argc < 3)
    {
      printf("%s <input1> <input2> [<-show_all> print all functions] [<-body_only> compare whole functions declaration] [-not_nested disable nested comments] [-vs=<n> is the search space size for pragmas]\n", argv[0]);
      return 0;
    }

  flag_print_all_funcs = 0;
  flag_find_full_function = 1;
  flag_nested_comments = 1;
  number_of_choices_limit = 500;

  for (i = 3; i < argc; i++)
    {
      if (strcmp(argv[i], "-show_all") == 0)
	{
	  flag_print_all_funcs = 1;
	  continue;
	}
      if (strcmp(argv[i], "-body_only") == 0)
	{
	  flag_find_full_function = 0;
	  continue;
	}

      if (strcmp(argv[i], "-not_nested") == 0)
	{
	  flag_nested_comments = 0;
	  continue;
	}
      if (strncmp(argv[i], "-vs=", 4) == 0)
	{
	  sscanf(argv[i], "-vs=%i", &number_of_choices_limit);
	  continue;
	}
      printf("Invalid argument %s\n", argv[i]);
      exit(-1);
    }

  printf("Processing functions in two files started\n");
  compare_functions(argv[1], argv[2]);
  printf("Processing functions in two files finished\n");

  return 0;
}

