#ifndef FILE_UTILS
#define FILE_UTILS

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "defs.h"
#include "file_utils.h"

int get_line_size(char * file)
{
  int i;
  static char buff[MAX_READ_LINE];
  FILE * f;

  f = fopen(file, "rt");

  if (f == NULL)
    {
      printf("Cannot open file %s for reading\n", file);
      exit(-1);
    }

  assert(f != NULL);

  fgets(buff, MAX_READ_LINE, f);

  if (strtok(buff, " \t") == NULL) 
    i = 0;
  else
    i = 1;

  while(strtok(NULL, " \t") != NULL) 
    i++;

  fclose(f);

  return i;
}

int getNumberLines(char * file)
{
  char c;
  int num;
  FILE * f;

  num = 0;

  f = fopen(file, "rt");
  if (f == NULL)
    {
      printf("Cannot open file %s for reading\n", file);
      exit(-1);
    }
  assert(f != NULL);

  while (fread(&c, 1, 1, f) == 1)
    {
      if (c == '\n')
	num++;
    }

  fclose(f);

  return num;
}

void read_file_into_matrix(char * file, char * format, int elem_size, 
			   void * data, int * max_rows, int * max_cols)
{
  int r, c, rows, cols;
  FILE * f;

  rows = getNumberLines(file);
  cols = get_line_size(file);

  if ((rows >= (*max_rows)) || (cols >= (*max_cols)))
    {
      printf("Needs size rows = %i, cols = %i\n", rows + 1, cols + 1);
      exit(-1);
    }

  f = fopen(file, "rt");
  if (f == NULL)
    {
      printf("Cannot open file %s for reading\n", file);
      exit(-1);
    }

  for (r = 0; r < rows; r++)
    for (c = 0; c < cols; c++)
      {
	fscanf(f, format, ((char *) data) + (cols * r + c) * elem_size);
	if (feof(f))
	  {
	    printf("feof reached at row = %i, column = %i\n", r, c);
	    exit(0);
	  }
      }

  fclose(f);

  (*max_rows) = rows;
  (*max_cols) = cols;
}


void printFile(char * in, FILE * fout)
{
  int i, n;
  FILE * f;
  static char buffer[MAX_READ_LINE + 1];

  n = getNumberLines(in);

  f = fopen(in, "r");
  if (f == NULL)
    {
      printf("Cannot open file %s\n", in);
      exit(-1);
    }

  for (i = 0; i < n; i++)
    {
      fgets(buffer, MAXSTR, f);
      fputs(buffer, fout);
    }

  fclose(f);
}

void storeLines(FILE * fout, char * * lines, int n)
{
  int i;

  for (i = 0; i < n; i++)
    {
      fprintf(fout, "%s", lines[i]);
      if (strchr(lines[i], '\n') == NULL)
	fprintf(fout, "\n");
    }

}

int stripEndSpaces(char * * lines, int numlines)
{
  int i;

  for (i = numlines - 1; i >= 0; i--)
    if (!spacesOnly(lines[i]))
      return i;
  return 0;
}

int readLinesFile(char * file, char * * * Lines)
{
  FILE * f;
  int numlines, i;
  char * ptr;
  char * * lines = NULL;
  char buffer[MAX_TEST_LINE + 10];

  numlines = getNumberLines(file);
  if (numlines <= 0)
    {
      return 0;
    }

  lines = malloc(numlines * sizeof(char *));
  if (lines == NULL)
    {
      printf("No enough memory\n");
      exit(-1);
    }

  assert(numlines < MAX_SUITE_TESTS);

  /* read the contents of the original suite file */
  f = fopen(file, "rt");
  for(i = 0; i < numlines; i++)
    {
      buffer[0] = 0;
      fgets(buffer, MAX_TEST_LINE, f);
      ptr = strchr(buffer, '\n');
      if (ptr != NULL)
	*ptr = 0;

      lines[i] = malloc((strlen(buffer) + 10) * sizeof(char));
      if (lines[i] == NULL)
	{
	  printf("No enough memory\n");
	  exit(0);
	}
      strcpy(lines[i], buffer);
    }

  /*  numlines = stripEndSpaces(lines, numlines); */

  assert(numlines > 0);

  fclose(f);

#ifndef PRINT_LOADED_TESTS_STATUS
  printf("Loaded suite file %s with %i tests\n", file, numlines);
#endif

  *Lines = lines;

  return numlines;
}

int findMaxIndexInt(int * x, int n)
{
  int i, index;

  assert(n > 0);

  index = 0;
  for (i = 0; i < n; i++)
    if (x[i] > x[index])
      index = i;

  return index;
}

int findMaxIndexDouble(double * x, int n)
{
  int i, index;

  assert(n > 0);

  index = 0;
  for (i = 0; i < n; i++)
    {
#if (FORCE_DETERM_ARITH)
      if (! fequals(x[i], x[index]))
#endif
	if (x[i] > x[index])
	  index = i;
    }

  return index;
}

void printVector(int * x, int n)
{
  int i;

  printf("\nVector is: ");
  for (i = 0; i < n; i++)
    printf("%i ", x[i]);
  printf("\n");
}

void printVectorD(double * x, int n)
{
  int i;

  printf("\nVector is: ");
  for (i = 0; i < n; i++)
    printf("%lf ", x[i]);
  printf("\n");
}

void printVectorNLD(double * x, int n)
{
  int i;

  for (i = 0; i < n; i++)
    printf("%i: %lf\n", i, x[i]);
}

int spacesOnly(char * buffer)
{
  int i, n;

  n = strlen(buffer);

  if (n == 0)
    return 1;

  for (i = 0; i < n; i++)
    if ((buffer[i] != ' ') && (buffer[i] != '\t'))
      return 0;

  return 1;
}


int fequals(double x, double y)
{
  double diff, diff1, diff2;

  if (ABS(x) < MINVAL)
    x = (x > 0)?MINVAL:-MINVAL;

  if (ABS(y) < MINVAL)
    y = (y > 0)?MINVAL:-MINVAL;

  diff = ABS(x - y);

  diff1 = ABS(diff / x);
  diff2 = ABS(diff / y);

  if (diff1 >= EPSILON)
    return 0;

  if (diff2 >= EPSILON)
    return 0;

  return 1;
}


void freeLinesGen(char * * * lines, int n)
{
  int i;
  for (i = 0; i < n; i++)
    free((*lines)[i]);
  free(*lines);
  (*lines) = NULL;
}


void write_fault_matrix(char * output, char * * universe, int num_faults, int num_tests, int * data)
{
  int i, j;
  FILE * f;

  f = fopen(output, "w");
  if (f == NULL)
    {
      printf("Cannot open file %s for writing\n", output);
      exit(-1);
    }
  
  fprintf(f, "\t%i listversions\n", num_faults);
  fprintf(f, "\t%i listtests\n", num_tests);

  storeLines(f, universe, num_tests);

  for (i = 0; i < num_tests; i++)
    {
      fprintf(f, "unitest%i:\n", i);
      for (j = 1; j <= num_faults; j++)
	fprintf(f, "v%i:\n\t%i\n", j, data[(j - 1) * num_tests + i]);
    }
  fclose(f);
}

#endif
