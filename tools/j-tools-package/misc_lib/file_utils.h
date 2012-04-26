#ifndef FILE_UTILS_H
#define FILE_UTILS_H

int getNumberLines(char * file);
int findMaxIndexInt(int * x, int n);
int findMaxIndexDouble(double * x, int n);
void printVector(int * x, int n);
void printVectorD(double * x, int n);
void printVectorNLD(double * x, int n);
int get_line_size(char * file);
void read_file_into_matrix(char * file, char * format, int elem_size, 
			   void * data, int * max_rows, int * max_cols);
void printFile(char * in, FILE * fout);
int stripEndSpaces(char * * lines, int numlines);
int readLinesFile(char * file, char * * * Lines);
int findMaxIndexInt(int * x, int n);
int findMaxIndexDouble(double * x, int n);
int spacesOnly(char * buffer);

int fequals(double x, double y);

void freeLinesGen(char * * * lines, int n);

void write_fault_matrix(char * output, char * * universe, int num_faults, int num_tests, int * data);

void storeLines(FILE * fout, char * * lines, int n);

#endif
