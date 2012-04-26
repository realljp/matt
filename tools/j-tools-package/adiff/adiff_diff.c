#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "adiff.h"

extern int flag_print_all_funcs;

char * duplicate_substr(char * buffer, int begin, int end)
{
  char * data;
  int n, m;

  n = strlen(buffer);
  m = end - begin + 1;

  if ((begin > n) || (end > n) || (begin > end))
    {
      printf("Invalid subrange\n");
      exit(-1);
    }

  data = Malloc((m + 10) * sizeof(char));

  strncpy(data, buffer + begin, m);

  data[m] = 0;

  return data;
}

int diff(char * _buffer1, char * _newbuffer1, int fbegin1, int fend1, items * _literals1, 
	 char * _buffer2, char * _newbuffer2, int fbegin2, int fend2, items * _literals2,
	 int * offset1, int * offset2)
{
  int i, j, n1, n2, l1, l2, flag, _n1, _n2, ni1, ni2, ni;
  char * buffer1, * newbuffer1, * buffer2, * newbuffer2;
  items literals1, literals2;

  *offset1 = -1;
  *offset2 = -1;

  buffer1 = duplicate_substr(_buffer1, fbegin1, fend1);
  newbuffer1 = duplicate_substr(_newbuffer1, fbegin1, fend1);
  buffer2 = duplicate_substr(_buffer2, fbegin2, fend2);
  newbuffer2 = duplicate_substr(_newbuffer2, fbegin2, fend2);

  n1 = strlen(buffer1);
  n2 = strlen(buffer2);
  _n1 = strlen(_buffer1);
  _n2 = strlen(_buffer2);

  if ((n1 != strlen(newbuffer1)) || (n2 != strlen(newbuffer2)) ||
      (_n1 != strlen(_newbuffer1)) || (_n2 != strlen(_newbuffer2)))
    {
      printf("The size of the original and processed buffers do not match\n");
      exit(-1);
    }

  create_func_items(_literals1, &literals1, fbegin1, fend1);
  create_func_items(_literals2, &literals2, fbegin2, fend2);

  ni1 = literals1.number_of_items;
  ni2 = literals2.number_of_items;

  ni = MIN(ni1, ni2);

  for (i = 0; i < ni; i++)
    {
      l1 = literals1.data[i].end - literals1.data[i].begin + 1;
      l2 = literals2.data[i].end - literals2.data[i].begin + 1;

      *offset1 = literals1.data[i].begin;
      *offset2 = literals2.data[i].begin;

      if (l1 != l2)
	{
	  if (DEBUG_DIFFING)
	    printf("Literals %i and %i have different sizes\n", l1, l2);
	  goto different;
	}

      if (literals1.data[i].type != literals2.data[i].type)
	{
	  if (DEBUG_DIFFING)
	    printf("Literals are of different types %i and %i\n", 
		   literals1.data[i].type, literals2.data[i].type);
	  goto different;
	}

      if (strncmp(_buffer1 + literals1.data[i].begin, _buffer2 + literals2.data[i].begin, l1) != 0)
	{
	  if (DEBUG_DIFFING)
	    printf("Literals %i and %i are different\n", l1, l2);
	  goto different;
	}
    }

  if (i < ni1)
    *offset1 = literals1.data[i].begin;

  if (i < ni2)
    *offset2 = literals2.data[i].begin;

  if (ni1 != ni2)
    {
      if (DEBUG_DIFFING)
	printf("The number of literals is different\n");
      goto different;
    }

  if (DEBUG_DIFFING)
    printf("Literals were OK\n");

  for (i = 0, j = 0; ((i < n1) && (j < n2)); i++, j++)
    {
      while ((newbuffer1[i] == ' ') || (newbuffer1[i] == '\t') || (newbuffer1[i] == '\n')) i++;
      while ((newbuffer2[j] == ' ') || (newbuffer2[j] == '\t') || (newbuffer2[j] == '\n')) j++;

      if (newbuffer1[i] != newbuffer2[j])
	{
	  if (DEBUG_DIFFING)
	    printf("Difference was found between %i and %i lines\n", 
		   get_line_number(_buffer1, i + fbegin1), get_line_number(_buffer2, j + fbegin2));
	  *offset1 = i + fbegin1;
	  *offset2 = j + fbegin2;
	  goto different;
	}
    }

  if ((i < n1) || (j < n2))
    {
      *offset1 = i + fbegin1;
      *offset2 = j + fbegin2;
      goto different;
    }

  flag = 0;
  goto next;

 different:
  flag = 1;

 next:

  Free(buffer1);
  Free(buffer2);
  Free(newbuffer1);
  Free(newbuffer2);
  free_items(&literals1);
  free_items(&literals2);

  return flag;
}

void diff_functions(char * buffer1, char * buffer2, fentries * functions1, fentries * functions2)
{
  int i, j, found;
  static char command[1024];
  char * file1 = NULL;
  char * file2 = NULL;
  char * file3 = NULL;
  char * file4 = NULL;
  struct stat filestat;
  FILE * f;
  items comments1, comments2, literals1, literals2;
  char * newbuffer1, * newbuffer2;
  int n1, n2;
  int diff_flag;
  int offset1, offset2;

  for (i = 0; i < functions1->num_funcs; i++)
    {
      found = 0;
      for (j = 0; j < functions2->num_funcs; j++)
	if (strcmp(functions1->data[i].fname, functions2->data[j].fname) == 0)
	  {
	    found = 1;
	    break;
	  }

      if (found)
	{
	  if (DEBUG_EXTRACTING)
	    printf("Function \"%s\" is found in both files\n", functions1->data[i].fname);

	  file1 = tempnam("/tmp/", "diff_");
	  file2 = tempnam("/tmp/", "diff_");
	  file3 = tempnam("/tmp/", "diff_");
	  file4 = tempnam("/tmp/", "diff_");

	  if ((file1 == NULL) || (file2 == NULL) || (file3 == NULL) || (file4 == NULL))
	    {
	      printf("Cannot create temporary file[s]\n");
	      exit(-1);
	    }

	  if (DEBUG_DIFFING)
	    printf("Saving function \"%s\" in the first file\n", functions1->data[i].fname);
	  save_function(file1, buffer1, functions1->data[i].fbegin, functions1->data[i].fend);
	  if (DEBUG_DIFFING)
	    printf("Saving function \"%s\" in the second file\n", functions2->data[j].fname);
	  save_function(file2, buffer2, functions2->data[j].fbegin, functions2->data[j].fend);

	  if (DEBUG_DIFFING)
	    printf("Comparing function \"%s\"\n", functions1->data[i].fname);

	  remove(file3);

	  offset1 = -1;
	  offset2 = -1;

	  if (INTERNAL_DIFF)
	    {
	      n1 = strlen(buffer1);
	      n2 = strlen(buffer2);

	      init_items(&comments1, n1);
	      init_items(&literals1, n1);
	      newbuffer1 = strdup(buffer1);
	      assert(newbuffer1 != NULL);
	      find_comments_and_literals(buffer1, newbuffer1, &literals1, 1, 0, 0);
	      clear(newbuffer1, &literals1);
	      find_comments_and_literals(buffer1, newbuffer1, &comments1, 0, 1, 0);
	      clear(newbuffer1, &comments1);

	      init_items(&comments2, n2);
	      init_items(&literals2, n2);
	      newbuffer2 = strdup(buffer2);
	      assert(newbuffer2 != NULL);
	      find_comments_and_literals(buffer2, newbuffer2, &literals2, 1, 0, 0);
	      clear(newbuffer2, &literals2);
	      find_comments_and_literals(buffer2, newbuffer2, &comments2, 0, 1, 0);
	      clear(newbuffer2, &comments2);

	      diff_flag = diff(buffer1, newbuffer1, 
			       functions1->data[i].fbegin, functions1->data[i].fend, 
			       &literals1, 
			       buffer2, newbuffer2, 
			       functions2->data[j].fbegin, functions2->data[j].fend, 
			       &literals2,
			       &offset1, &offset2);

	      free_items(&comments1);
	      free_items(&comments2);
	      free_items(&literals1);
	      free_items(&literals2);
	    }
	  else
	    {
	      f = fopen(file4, "w");
	      assert(f != NULL);
	      fprintf(f, "\n\n\ncmp %s %s >& %s\n", file1, file2, file3);
	      fclose(f);
	      
	      sprintf(command, "csh -f %s", file4);
	      if (DEBUG_DIFFING)
		printf("command = %s\n", command);
	      system(command);
	      
	      if (stat(file3, &filestat))
		{
		  printf("Cannot access file %s\n", file3);
		  exit(-1);
		}
	      
	      if (filestat.st_size > 0)
		diff_flag = 1;
	      else
		diff_flag = 0;
	    }

	  if (diff_flag)
	    {   
	      printf("Function \"%s\" is changed at lines (%i, %i)\n", 
		     functions1->data[i].fname, 
		     get_line_number(buffer1, offset1), 
		     get_line_number(buffer2, offset2));
	    }
	  else
	    {
	      if (flag_print_all_funcs)
		printf("Function \"%s\" is the same\n", functions1->data[i].fname);
	    }

	  remove(file1);
	  remove(file2);
	  remove(file3);
	  remove(file4);

	  Free(file1);
	  Free(file2);
	  Free(file3);
	  Free(file4);
	}
      else
	printf("Function \"%s\" is deleted at line %i\n", 
	       functions1->data[i].fname,
	       get_line_number(buffer1, functions1->data[i].fbegin));
    }

  for (i = 0; i < functions2->num_funcs; i++)
    {
      found = 0;
      for (j = 0; j < functions1->num_funcs; j++)
	if (strcmp(functions2->data[i].fname, functions1->data[j].fname) == 0)
	  {
	    found = 1;
	    break;
	  }

      if (! found)
	printf("Function \"%s\" is added at line %i\n",
	       functions2->data[i].fname, 
	       get_line_number(buffer2, functions2->data[i].fbegin));
    }
}

void save_function(char * file, char * buffer, int begin, int end)
{
  FILE * f;

  f = fopen(file, "w");
  if (f == NULL)
    {
      printf("Cannot open file %s for writing\n", file);
      exit(-1);
    }

  if (DEBUG_DIFFING)
    printf("Saving function from file located in offsets [%i, %i]\n", begin, end);

  fwrite(buffer + begin, 1, end - begin + 1, f);

  fclose(f);

  if (DEBUG_DIFFING)
    printf("Function from offsets [%i, %i] was saved in the file %s\n", begin, end, file);
}

