#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "adiff.h"

void init_items(items * Items, int max_pairs)
{
  Items->max_pairs = max_pairs;
  Items->number_of_items = 0;
  Items->data = Malloc(Items->max_pairs * sizeof(item));
}

void free_items(items * Items)
{
  Items->max_pairs = 0;
  Items->number_of_items = 0;
  if (Items->data != NULL)
    Free(Items->data);
  Items->data = NULL;
}

void add_item(items * Items, int begin, int end, pragma_type type)
{
  item * old_data = NULL;
  int i;

  if (Items->number_of_items >= Items->max_pairs)
    {
      Items->max_pairs *= 2;
      if (DEBUG_MISC)
	printf("Increasing number of pairs to %i\n", Items->max_pairs);
      old_data = Items->data;
      Items->data = Malloc(Items->max_pairs * sizeof(item));
      for (i = 0; i < Items->number_of_items; i++)
	Items->data[i] = old_data[i];
      Free(old_data);
    }

  Items->data[Items->number_of_items].begin = begin;
  Items->data[Items->number_of_items].end = end;
  Items->data[Items->number_of_items].type = type;
  Items->number_of_items++;
}

void finit(fentries * FEntries, int max_funcs)
{
  FEntries->max_funcs = max_funcs;
  FEntries->num_funcs = 0;
  FEntries->data = Malloc(FEntries->max_funcs * sizeof(fentry));
  assert(FEntries->data != NULL);
}

void fadd(fentries * FEntries, char * name, int begin, int end)
{
  fentry * old_data = NULL;
  int i;

  if (FEntries->num_funcs >= FEntries->max_funcs)
    {
      FEntries->max_funcs *= 2;
      if (DEBUG_MISC)
	printf("Increasing number of functions to %i\n", FEntries->max_funcs);
      old_data = FEntries->data;
      FEntries->data = Malloc(FEntries->max_funcs * sizeof(fentry));
      for (i = 0; i < FEntries->num_funcs; i++)
	{
	  FEntries->data[i].fname = old_data[i].fname;
	  FEntries->data[i].fbegin = old_data[i].fbegin;
	  FEntries->data[i].fend = old_data[i].fend;
	}
      Free(old_data);
    }

  FEntries->data[FEntries->num_funcs].fname = strdup(name);
  assert(FEntries->data[FEntries->num_funcs].fname != NULL);
  FEntries->data[FEntries->num_funcs].fbegin = begin;
  FEntries->data[FEntries->num_funcs].fend = end;
  FEntries->num_funcs++;
}

void adjust_items(items * Items, int shift, int begin, int end)
{
  int i;

  for (i = 0; i < Items->number_of_items; i++)
    {
      if ((Items->data[i].begin >= begin) || (Items->data[i].begin >= end))
	Items->data[i].begin += shift;
      if ((Items->data[i].end >= begin) || (Items->data[i].end >= end))
	Items->data[i].end += shift;
    }
}

void removeItems(char * buffer, items * Items)
{
  int i, j, n, begin, end;

  for (i = 0; i < Items->number_of_items; i++)
    {
      begin =  Items->data[i].begin;
      end =  Items->data[i].end;
      n = strlen(buffer);
      assert(begin < n);
      assert(end < n);
      for (j = end + 1; j <= n; j++)
	buffer[begin + j - end - 1] = buffer[j];
      adjust_items(Items, - (end - begin + 1), begin, end);
    }
}

void clear(char * buffer, items * Items)
{
  int i, j, begin, end, n;

  n = strlen(buffer);
  for (i = 0; i < Items->number_of_items; i++)
    {
      begin =  Items->data[i].begin;
      end =  Items->data[i].end;
      assert(begin < n);
      assert(end < n);
      for (j = begin; j <= end; j++)
	buffer[j] = ' ';
    }
}

void create_func_items(items * orig, items * new, int fbegin, int fend)
{
  int i;

  init_items(new, orig->max_pairs);
  for (i = 0; i < orig->number_of_items; i++)
    if ((orig->data[i].begin >= fbegin) && (orig->data[i].begin <= fend) && 
	(orig->data[i].end >= fbegin) && (orig->data[i].end <= fend))
      add_item(new, orig->data[i].begin, orig->data[i].end, orig->data[i].type);
}

void print_functions(char * orig_buffer, fentries * functions)
{
  int i;

  for (i = 0; i < functions->num_funcs; i++)
    {
      printf("Function \"%s\" [%i, %i]\n", functions->data[i].fname, 
	     get_line_number(orig_buffer, functions->data[i].fbegin),  
	     get_line_number(orig_buffer, functions->data[i].fend));
      if (DEBUG_EXTRACTING)
	printf("Function body limits offset [%i, %i]\n", functions->data[i].fbegin, functions->data[i].fend);
    }
}

void copy_items_type(items * orig, items * new, pragma_type type)
{
  int i;

  init_items(new, orig->max_pairs);
  for (i = 0; i < orig->number_of_items; i++)
    if (orig->data[i].type == type)
      add_item(new, orig->data[i].begin, orig->data[i].end, orig->data[i].type);
}

void delete_items_type(items * orig, items * new, pragma_type type)
{
  int i;

  init_items(new, orig->max_pairs);
  for (i = 0; i < orig->number_of_items; i++)
    if (orig->data[i].type != type)
      add_item(new, orig->data[i].begin, orig->data[i].end, orig->data[i].type);
}

element * create_pragmas(int pid, int tbegin, int tend, int pbegin, 
			 int pend, comp_type type, int max_elements)
{
  element * Element;

  Element = Malloc(sizeof(element));

  Element->max_elements = max_elements;
  Element->number_of_elements = 0;
  Element->type = type;
  Element->text_begin = tbegin;
  Element->text_end = tend;
  Element->pragma_begin = pbegin;
  Element->pragma_end = pend;
  Element->pid = pid;
  Element->list = Malloc(Element->max_elements * sizeof(element *));
  
  return Element;
}

void free_pragmas(element * Element)
{
  int i;

  for (i = 0; i < Element->number_of_elements; i++)
    free_pragmas(Element->list[i]);

  Element->max_elements = 0;
  Element->number_of_elements = 0;
  if (Element->list != NULL)
    Free(Element->list);
  Element->list = NULL;

  Free(Element);
}

void add_pragma(element * Element, element * subelement)
{
  struct element * * old_list = NULL;
  int i;

  if (Element->number_of_elements >= Element->max_elements)
    {
      Element->max_elements *= 2;
      if (DEBUG_MISC)
	printf("Increasing number of elements to %i\n", Element->max_elements);
      old_list = Element->list;
      Element->list = Malloc(Element->max_elements * sizeof(element *));
      for (i = 0; i < Element->number_of_elements; i++)
	Element->list[i] = old_list[i];
      Free(old_list);
    }

  Element->list[Element->number_of_elements] = subelement;
  Element->number_of_elements++;
}

void * Malloc(int size)
{
  int n;
  void * p;

  n = (HUGE_ALLOCATE) ? (HUGE_MEM) : (size);

  p = malloc(n);

  if (p == NULL)
    {
      printf("Cannot allocate %lf MB of memory\n", n / 1024.0 / 1024.0);
      exit(-1);
    }

  return p;
}

void Free(void * data)
{
  if (FREE)
    free(data);
}

void print_items(char * orig_buffer, items * Items)
{
  int i, j;

  for (i = 0; i < Items->number_of_items; i++)
    {
      printf("Item \n\t");
      for (j = Items->data[i].begin; j <= Items->data[i].end; j++)
	printf("%c", orig_buffer[j]);
      printf("of type \"%s\" at lines [%i, %i]\n", types_enum2str(Items->data[i].type), 
	     get_line_number(orig_buffer, Items->data[i].begin),  
	     get_line_number(orig_buffer, Items->data[i].end));
    }
}

void print_pragmas(char * orig_buffer, element * Element, int indent)
{
  int i, j, n;

  n = Element->number_of_elements;

  for (j = 0; j < indent; j++)
    printf(" ");

  printf("Element %i of type '%s' has pragma with data \"", 
	 Element->pid, 
	 comp_types_enum2str(Element->type));

  for (j = Element->pragma_begin; j <= Element->pragma_end; j++)
    printf("%c", orig_buffer[j] != '\n' ? orig_buffer[j] : ' ');

  printf("\" at lines [%i (%i), %i (%i)] and contains text in lines [%i (%i), %i (%i)]\n", 
	 get_line_number(orig_buffer, Element->pragma_begin), 
	 Element->pragma_begin, 
	 get_line_number(orig_buffer, Element->pragma_end), 
	 Element->pragma_end, 
	 get_line_number(orig_buffer, Element->text_begin), 
	 Element->text_begin, 
	 get_line_number(orig_buffer, Element->text_end), 
	 Element->text_end);

  for (i = 0; i < n; i++)
    print_pragmas(orig_buffer, Element->list[i], indent + 1);
}

void create_items_from_functions(items * fitems, fentries * functions)
{
  int i, n;

  n = functions->num_funcs;

  init_items(fitems, n);

  for (i = 0; i < n; i++)
    add_item(fitems, functions->data[i].fbegin, functions->data[i].fend, OTHER);
}
