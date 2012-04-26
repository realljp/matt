/* Created by Alexey Malishevsky 

   functions which access "newvers" files
*/

#ifndef VERS_H
#define VERS_H

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include "defs.h"

int get_line_size_v(char * file);
int vers_get_num_faults(int version);
void load_faults(char * file);
void print_faults();
int fault_exposed_version(int version, int test, int fault);

#endif
