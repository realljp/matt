#ifndef DEFS
#define DEFS

#define EPSILON 1e-10
#define MINVAL 1e-100

#define MAX(x, y) (((x) => (y)) ? (x) : (y))
#define ABS(x) (((x) < 0) ? (-(x)) : (x))

#define FORCE_DETERM_ARITH 1
#define FORCE_INCR_IND 1

/* maximum characters for one line in the suite file */
#define MAX_TEST_LINE 1000
#define MAX_VERS 5000
#define MAX_SOFT_VERS 20
#define MAXSTR 10000
#define MAX_READ_LINE 10000000
#define MAX_INPUT_LINE MAX_TEST_LINE
#define MAXVERS MAX_VERS
#define MAXFAULTS 1000
#define MAX_FAULTS MAXFAULTS
#define MAX_FUNC_NAME_SIZE 1024
#define MAX_COMMAND_LINE 10240

#define MAX_TESTS 20000
#define MAX_FUNCS 3000
#define MAX_STATS 3000
#define MAX_SUITE_TESTS 10000

#define BYTE char

#define FIELDMAX 32
#define FALSE 0
#define TRUE (!FALSE)
#define FILENAME_LENGTH 1000
/* #define INPUTMAX 40960 */
#define MAXULINES MAX_TESTS

#define INPUTMAX 100000

#define TESTMAX MAX_SUITE_TESTS
#define VERSIONMAX MAXFAULTS

#define MAX_TEST_PER_SUITE MAX_SUITE_TESTS

#define MAXSUITESIZE 100000

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif

#include "test_matrix.h"

#endif
