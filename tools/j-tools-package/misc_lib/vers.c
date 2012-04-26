/* Created by Alexey Malishevsky 

   functions which access "newvers" files
*/

#ifndef VERS
#define VERS

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include "vers.h"
#include "file_utils.h"
#include "defs.h"

int numfaults = -1, numversions = -1;

int faults[MAXVERS][MAXFAULTS];

int get_line_size_v(char * file)
{
  int i;
  char buff[MAXSTR];
  FILE * f;

  f = fopen(file, "rt");

  if (f == NULL)
    {
      printf("Cannot open file %s for reading\n", file);
      exit(-1);
    }

  assert(f != NULL);

  fgets(buff, MAXSTR, f);

  if (strtok(buff, " \t") == NULL) 
    i = 0;
  else
    i = 1;

  while(strtok(NULL, " \t") != NULL) 
    i++;

  fclose(f);

  return i;
}

int vers_get_num_faults(int version)
{
  int i, n;
  n = 0;
  for (i = 1; i <= numfaults; i++)
    n += faults[version][i];
  return n;
}


void load_faults(char * file)
{
  int i, v;
  FILE * f;
  char buff[MAXSTR];

  numfaults = get_line_size_v(file) - 2;

  assert(numfaults > 0);

  numversions = getNumberLines(file);

  f = fopen(file, "rt");

  if (f == NULL)
    {
      printf("Cannot open file %s for reading\n", file);
      exit(-1);
    }

  assert(f != NULL);

  for (v = 1; v <= numversions; v++)
    {
      fscanf(f, "%s", buff);
      fscanf(f, "%s", buff);
      for (i = 1 ; i <= numfaults; i++)
	fscanf(f, "%i", &faults[v][i]);
    }
  assert(numversions > 0);
 
  fclose(f);

  printf("Loaded newVer file %s with %i faults\n", file, numversions);
}

void print_faults()
{
  int i, j, l;
      for (i = 1 ; i <= numversions; i++)
	{
	  l = 0;
	  for (j = 1 ; j <= numfaults; j++)
	    l += faults[i][j];

	  printf("Version=%i Faults=%i:   ", i, l);
	  for (j = 1 ; j <= numfaults; j++)
	    printf("%i  ", faults[i][j]);
	  printf("\n");
	}
 
}

int fault_exposed_version(int version, int test, int fault)
{
  int n;

  assert(version <= numversions);
  assert(fault <= numfaults);

  n = (fault_exposed(test, fault) && faults[version][fault]);
  return n;
}

#endif
