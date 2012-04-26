#ifndef FDIFF
#define FDIFF

#define MAX_NAME_LENGTH 10000

#define MIN(x, y) ((x) <= (y) ? (x) : (y))
#define MAX(x, y) ((x) >= (y) ? (x) : (y))

#define END -1
#define ERROR -2

typedef enum {PRAGMA_IF, PRAGMA_ELSE, PRAGMA_ENDIF, PRAGMA_OTHER, 
	      STRING, LITERAL, COMMENT, OTHER, ESCSEQ} pragma_type;

typedef struct
{
  int begin, end; 
  pragma_type type;
} item;

typedef struct
{
  int number_of_items;
  int max_pairs;
  item * data;
} items;

typedef struct
{
  char * fname;
  int fbegin, fend;
} fentry;

typedef struct
{
  int num_funcs, max_funcs;
  fentry * data;
} fentries;

typedef enum {AND, OR, TERM} comp_type;

typedef struct element
{
  int number_of_elements;
  int max_elements;
  comp_type type;
  int text_begin, text_end, pragma_begin, pragma_end, pragma_type;
  int pid;
  struct element * * list;
} element;

typedef struct {int depth, width;} depth_width;

#define DEBUG_EXTRACTING 0
#define DEBUG_MATCHING 0
#define DEBUG_FUNC_TOKENS 0
#define DEBUG_DIFFING 0
#define DEBUG_TOKENS 0
#define DEBUG_MISC 0
#define DEBUG_PRAGMAS 0
#define DEBUG_PRAGMAS_1 0
#define DEBUG_SELECTORS 0
#define DEBUG_WARNINGS 0
#define DEBUG_FUNCS 0
#define FREE 1
#define HUGE_ALLOCATE 0
#define HUGE_MEM 1000000
#define INTERNAL_DIFF 1
#define allow_space_in_pragma_name 1

int get_line_number(char * buffer, int index);
int is_delimiter(char c);
int get_token(char * orig_buffer, char * buffer, int n, int index, int * begin, int * end, char * token);
int is_data_declaration(char * token);
void invert(char * src, char * dest);
int get_func_name(char * orig_buffer, char * buffer, int index, char * name);
int get_next_function(char * orig_buffer, char * buffer, int current,  char * fname, int * fbegin, int * fend, int prev_decl_end);
void compare_functions(char * src1, char * src2);
int find_functions(char * buffer, fentries * functions);
void add_item(items * Items, int begin, int end, pragma_type type);
void print_functions(char * orig_buffer, fentries * functions);
void save_function(char * file, char * buffer, int begin, int end);
void diff_functions(char * buffer1, char * buffer2, fentries * functions1, fentries * functions2);
void finit(fentries * FEntries, int max_funcs);
void fadd(fentries * FEntries, char * name, int begin, int end);
void adjust_items(items * Items, int shift, int begin, int end);
void removeItems(char * buffer, items * Items);
void clear(char * buffer, items * Items);
void insertSpaces(char * buffer, items * Items);
int match_symbol(char * buffer, int index, char symbol);
int matchEmptyLines(char * buffer, int index, items * Items);
int matchSpace(char * buffer, int index, items * Items);
int matchComment(char * buffer, int index, items * Items);
void find_comments_and_literals(char * orig_buffer, char * buffer, items * Items, int add_literals, int add_comments, int add_backslashes);
void find_pragmas(char * orig_buffer, char * buffer, items * Items);
void findSpaces(char * buffer, items * Items);
void findEmptyLines(char * buffer, items * Items);
void findNeededSpaces(char * buffer, items * Items);
void init_items(items * Items, int max_items);
void free_items(items * Items);
int diff(char * _buffer1, char * _newbuffer1, int fbegin1, int fend1, items * _literals1, 
	 char * _buffer2, char * _newbuffer2, int fbegin2, int fend2, items * _literals2,
	 int * offset1, int * offset2);
void create_func_items(items * orig, items * new, int fbegin, int fend);
int is_space(char c);
int skip_spaces(char * buffer, int index);
int compatible_tokens(char * token1, char * token2);
int get_simple_token(char * orig_buffer, char * buffer, int n, int index, int * begin, int * end, char * token);
int match_bracket(char * orig_buffer, char * buffer, int n, int index, char * opening, char * closing);
int find_token(char * orig_buffer, char * buffer, int n, int index, char * target);
int is_identifier(char * token);
int move_to_BOL(char * buffer, int index);
int move_to_EOL(char * buffer, int n, int index);
pragma_type get_pragma_type(char * token);
int get_line(char * orig_buffer, char * buffer, int n, int index, int * lbegin, int * lend, char * line);
pragma_type extract_pragma_name(char * line);
void copy_items_type(items * orig, items * new, pragma_type type);
void delete_items_type(items * orig, items * new, pragma_type type);
void * Malloc(int size);
void Free(void * data);
void print_items(char * orig_buffer, items * Items);
void parse_pragmas(items * input, element * Pragmas);
char * types_enum2str(pragma_type x);
element * parse_OR_pragmas(char * orig_buffer, items * input, int begin, int end, int * _index);
element * parse_AND_pragmas(char * orig_buffer, items * input, int begin, int end, int * _index);
element * create_pragmas(int pid, int tbegin, int tend, int pbegin, 
			 int pend, comp_type type, int max_elements);
int find_next_pragma(char * orig_buffer, items * input, int start, int end);
void print_pragmas(char * orig_buffer, element * Element, int indent);
char * comp_types_enum2str(comp_type x);
void fill_pdata(char * orig_buffer, items * inputs, element * Pragma);
void fill_tdata(char * orig_buffer, items * inputs, element * Pragma);
void select_branch(char * orig_buffer, char * buffer, element * Pragmas, 
		   items * deleted, int * selectors);
void select_branch_internal(char * orig_buffer, char * buffer, element * Pragmas, 
			    items * deleted, int * selectors, int depth);
depth_width compute_depth_width(element * Pragmas);
int compute_VS(depth_width dw);
int find_functions_internal(char * buffer, fentries * functions, 
			    int choice, int * number_of_choices);
void create_selectors(depth_width dw, int selector, int * selectors);
void select_best_func_limits(fentries * source, int counter, fentries * destination);
void create_items_from_functions(items * fitems, fentries * functions);
int fix_func_overlap(fentries * functions);
int check_func_overlap(fentries * functions);
int between(int x, int min, int max);

#endif
