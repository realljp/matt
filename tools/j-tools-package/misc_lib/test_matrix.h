
#ifndef TEST_MATRIX_H
#define TEST_MATRIX_H

/* Include files */ 
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include "defs.h"

/*----------------------------------------------------------------*/

/* global variables */

enum ERROR_TYPES { 
    OPEN_FILE, NUM_VERS_A, NUM_VERS_B, NUM_TESTS_A, NUM_TESTS_B, TOO_MANY, 
    UNIVERSE_READ, UNIVERSE_MALLOC, MATRIX_MALLOC, TEST_NUM_A, TEST_NUM_B,
    VERS_NUM_A, VERS_NUM_B, FAULT_VAL_A, FAULT_VAL_B
};

int read_matrix(char *matrixfile);
int handle_error( int line_num, enum ERROR_TYPES error_type, 
		  FILE *file_handle, ... );
int fault_exposed(int test, int version);
int number_of_tests();
int number_of_versions();
int testid_for_universe_line(char *uline);

int fault_matrix_copy_universe_line(int testid, char * dest);

#endif
