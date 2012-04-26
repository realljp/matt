/*----------------------------------------------------------------*/
/* test_matrix.c

   This file contains the code for test_matrix.c, a 
   set of routines for reading in and providing access to
   the contents of a test matrix file output by test_matrix.sh

   Compile this, link with it, and you can use its routines.

   There's an example routine that uses these in test_matrix_driver.c

*/
/*----------------------------------------------------------------*/

/* Include files */ 
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <assert.h>
#include "defs.h"
#include "test_matrix.h"

/*----------------------------------------------------------------*/

static int numtests;
static int numversions;
static int *matrix;
static char *universe_lines[MAXULINES];

int handle_error(int line_num, enum ERROR_TYPES error_type, FILE*, ... );

/*---------------------------------------------------------------------------*/
/* Description:  read_matrix

     Reads in a matrix, into a global structure, where
     it is accessible by other access routines.

   Parameters:  takes arg naming test matrix.

   Return value:  TRUE or FALSE.

   Output:  On failure diagnostic routines are written to stderr

   Revision History: 11-21-1996  Gregg Rothermel   - created
                     07-09-1998  Jeff Ronne        - enhanced error detection
		     07-10-1998  Jeff Ronne        - cleaned up matrix parsing
*/
/*---------------------------------------------------------------------------*/

int read_matrix(char *matrixfile)
{

   FILE *mfp;
   char numstr[FILENAME_LENGTH];    
   char line[INPUTMAX];
   char *tmpstr;
   int i,j,k;
   int outsuitenum=0;
   int thisversion,thistest,faultvalue;
   int linelen, num_matches, line_num=0;

   /* Open the matrix file. */
   mfp = fopen(matrixfile,"rt");
   if(mfp == NULL) return handle_error( line_num, OPEN_FILE, mfp, matrixfile );

   /* first line of the file hold number of versions */
   tmpstr = fgets( line, INPUTMAX, mfp); line_num++;
   if( tmpstr == NULL ) return handle_error( line_num, NUM_VERS_A, mfp );

   num_matches = sscanf( line, "%d", &numversions);
   if( num_matches != 1 ) return handle_error( line_num, NUM_VERS_B, mfp );
     
   /* second line of the file hold number of tests */
   tmpstr = fgets( line, INPUTMAX, mfp); line_num++;
   if (tmpstr == NULL) return handle_error( line_num, NUM_TESTS_A, mfp );

   num_matches = sscanf( line, "%d", &numtests);
   if( num_matches != 1 ) return handle_error( line_num, NUM_TESTS_B, mfp );
   if (numtests > MAXULINES) return handle_error( line_num, TOO_MANY, mfp );
   
   /* now read numtests lines, each a universe file line,
      into the universe structure */ 

   /* now read the lines */
   for (i=0;i<numtests;i++)
   {
       tmpstr = fgets(line,INPUTMAX,mfp); line_num++;
       if(tmpstr == NULL) return handle_error(line_num, UNIVERSE_READ, mfp, i);

        /* for java subjects, CLASSPATH setting line is skipped */
	if((line[0]=='C' && line[1]=='L' && line[2]=='A' &&
           line[3]=='S' && line[4]=='S' && line[5]=='P' &&
           line[6]=='A' && line[7]=='T' && line[8]=='H') ||
	  (line[0]=='s' && line[1]=='e' && line[2]=='t' &&
           line[3]=='e' && line[4]=='n' && line[5]=='v'))
	{ 
	    i = i - 1;
	}
	else {
            	/* here malloc storage for and store the line */
            	linelen = strlen(line);
            	if ((tmpstr = (char *) malloc((linelen+1)*sizeof(char *))) == NULL)
	            return handle_error( line_num, UNIVERSE_MALLOC, mfp, i, linelen+1 );
            	strcpy(tmpstr,line);
            	universe_lines[i] = tmpstr;
	}
   }

   /* now malloc the struct to hold the matrix */
   if ((matrix = (int *)malloc(sizeof(int)*(numtests*numversions)))== NULL)
       return handle_error(line_num, MATRIX_MALLOC, mfp, numtests,numversions);

   /* now, for each test */

   for (i=1;i<=numtests;i++)
   {
      /* get line and read test number from it */
      tmpstr = fgets(line,INPUTMAX,mfp); line_num++;
      if (tmpstr == NULL) return handle_error( line_num, TEST_NUM_A, mfp, i );

      num_matches = sscanf(line,"%*7s%d:",&thistest);
      if( num_matches != 1 ) return handle_error(line_num, TEST_NUM_B, mfp, i);

      /* now, for each version */
      for (j=1;j<=numversions;j++)
      {
         /* get line and read version number from it */
         tmpstr = fgets(line,INPUTMAX,mfp); line_num++;
         if ( tmpstr == NULL)
	     return handle_error( line_num, VERS_NUM_A, mfp, thistest, i, j );

         num_matches = sscanf(line,"%*1s%d:",&thisversion);
	 if( num_matches != 1 ) 
	     return handle_error( line_num, VERS_NUM_B, mfp, thistest, i, j );

         /* get line and read 0 or 1 from it */
         tmpstr = fgets(line,INPUTMAX,mfp); line_num++;
         if(tmpstr == NULL) 
	    return handle_error(line_num,FAULT_VAL_A,mfp,thistest,thisversion);
         
         num_matches = sscanf(line,"%d",&faultvalue);
	 if(num_matches!=1) 
	    return handle_error(line_num,FAULT_VAL_A,mfp,thistest,thisversion);

         /* fill in space in struct */ 
         matrix[((thisversion -1)*numtests) + thistest] = faultvalue;

	 #ifdef DEBUG
         printf("setting %d (%d,%d) to %d\n",
             ((thisversion-1)*numtests)+thistest,
	     thisversion,thistest,faultvalue);
	 #endif
     }
   }

   fclose(mfp);

   return TRUE;
}

/*----------------------------------------------------------------*/
/* Internal Functions                                             */


int handle_error( int line_num, enum ERROR_TYPES error_type, 
		  FILE *file_handle, ... )
{
    va_list argp;
    char *error_string[15];

    error_string[OPEN_FILE] = 
	"unable to open fault matrix file: %s \n";
    error_string[NUM_VERS_A] = 
	"unable to read first line (number of versions)\n";
    error_string[NUM_VERS_B] = 
	"unable to parse first line (number of versions)\n";
    error_string[NUM_TESTS_A] = 
	"unable to read second line (number of tests)\n";
    error_string[NUM_TESTS_B] = 
	"unable to parse second line (number of tests)\n";
    error_string[TOO_MANY] =
        "too many (%d) tests in universe, increase MAXULINES constant (%d)\n";
    error_string[UNIVERSE_READ] =
	"unable to read line %d of the universe\n";
    error_string[UNIVERSE_MALLOC] = 
	"unable to malloc memory for line %d of the universe (%d chars)\n";
    error_string[MATRIX_MALLOC] = 
	"unable to malloc memory for matrix (%dx%d)\n";
    error_string[TEST_NUM_A] = 
	"unable to read test number [%d tests read]\n";
    error_string[TEST_NUM_B] = 
	"unable to parse test number [%d tests read]\n";
    error_string[VERS_NUM_A] = 
	"unable to read version number for test #%d " 
	"[%d tests, %d versions read]\n";
    error_string[VERS_NUM_B] = 
	"unable to parse version number for test #%d "
	"[%d tests, %d versions read]\n";
    error_string[FAULT_VAL_A] = 
	"unable to read fault value for test #%d, fault #%d\n";
    error_string[FAULT_VAL_B] = 
	"unable to parse fault value for test #%d, fault #%d\n";

    /* Close file handle if appropriate */
    if( file_handle != NULL ) fclose( file_handle );

    /* Print error message with variable paramaters. */
    va_start(argp, file_handle);
    fprintf(stderr, "error:%d: ", line_num);
    vfprintf(stderr, error_string[error_type], argp);
    va_end(argp);

    return FALSE;
}

/*----------------------------------------------------------------*/
/* Access Functions                                               */

int fault_exposed(int test, int version)
{
   return matrix[((version-1)*numtests) + test]; 
}

int number_of_tests()
{
   return numtests;
}

int number_of_versions()
{
   return numversions;
}

int testid_for_universe_line(char *uline)
{
   int i;

   for (i=0;i<numtests;i++)
   {
      if (strcmp(uline,universe_lines[i]) == 0)
         return(i);
   } 
   printf("Warning: uline %s not found in universe.\n",uline);
   return -1;
}

/* added */

int fault_matrix_copy_universe_line(int testid, char * dest)
{
  char * ptr = NULL;
  int n;

  assert(testid < number_of_tests());
  assert(testid >= 0);

  n = strlen(universe_lines[testid]);
  ptr = strchr(universe_lines[testid], '\n');
  if (ptr != NULL)
    n--;

  if (dest != NULL)
    {
      strncpy(dest, universe_lines[testid], n);
      dest[n] = 0;
    }

  return n;
}
