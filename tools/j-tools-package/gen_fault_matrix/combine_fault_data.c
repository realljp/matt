#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "defs.h"
#include "file_utils.h"

#define MAX(x, y) ((x) > (y) ? (x) : (y))

int main(int argc, char * * argv)
{
  static data[MAX_VERS][MAX_TESTS];
  int testid, version, tests, versions, i, j, exposed, n;
  FILE * f;
  char * input, * output, * universe;
  static char buffer[MAX_READ_LINE + 1];
  char * s = NULL;

  if (argc != 4)
    {
      printf("%s <input fault data> <universe file> <output fault matrix>\n", argv[0]);
      exit(-1);
    }
  
  input = argv[1];
  universe = argv[2];
  output = argv[3];

  n = getNumberLines(input);

  f = fopen(input, "r");
  if (f == NULL)
    {
      printf("Cannot open file %s\n", input);
      exit(-1);
    }

  versions = 0;
  tests = 0;
  for (i = 0; i < n; i++)
    {
      fgets(buffer, MAX_READ_LINE, f);
      buffer[strlen(buffer) - 1] = 0;

      s = strtok(buffer, ": ");
      assert(s != NULL);
      s = strtok(NULL, ": ");
      assert(s != NULL);
      sscanf(s, "%i", &version);

      s = strtok(NULL, ": ");
      assert(s != NULL);
      s = strtok(NULL, ": ");
      assert(s != NULL);
      sscanf(s, "%i", &testid);

      s = strtok(NULL, ": ");
      assert(s != NULL);
      s = strtok(NULL, ": ");
      assert(s != NULL);
      sscanf(s, "%i", &exposed);

      /*      printf("version = %i, test = %i, exposed = %i\n", version, testid, exposed); */

      versions = MAX(versions, version);
      tests = MAX(tests, testid + 1);

      assert(versions < MAX_VERS);
      assert(tests < MAX_TESTS);

      assert(testid >= 0);
      assert(version > 0);

      data[version][testid] = exposed;
    }

  fclose(f);

  f = fopen(output, "w");
  if (f == NULL)
    {
      printf("Cannot open file %s for writing\n", output);
      exit(-1);
    }
  
  fprintf(f, "\t%i listversions\n", versions);
  fprintf(f, "\t%i listtests\n", tests);

  printFile(universe, f);

  for (i = 0; i < tests; i++)
    {
      fprintf(f, "unitest%i:\n", i);
      for (j = 1; j <= versions; j++)
	fprintf(f, "v%i:\n\t%i\n", j, data[j][i]);
    }

  fclose(f);

  return 0;
}
