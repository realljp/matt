#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "adiff.h"

void find_pragmas(char * orig_buffer, char * buffer, items * pragmas)
{
  int lbegin, lend, index, n;
  char * token, * line;
  pragma_type ptype;

  n = strlen(buffer);

  token = Malloc((n + 10) * sizeof(char));

  line = Malloc((n + 10) * sizeof(char));

  init_items(pragmas, 10);

  index = 0;
  while (1)
    {
      index = get_line(orig_buffer, buffer, n, index, &lbegin, &lend, line);
      if (index < 0)
	break;

      ptype = extract_pragma_name(line);

      if (ptype == OTHER)
	continue;

      add_item(pragmas, lbegin, lend, ptype);
    }
}

/* assumes that there are no '\' before the end of line, those symbols must be removed together 
with the end of line */
int get_line(char * orig_buffer, char * buffer, int n, int index, int * lbegin, int * lend, char * line)
{
  int m;

  if (index >= n)
    return -1;

  *lbegin = move_to_BOL(buffer, index);

  *lend = move_to_EOL(buffer, n, index);

  m = (*lend) - (*lbegin) + 1;
  strncpy(line, buffer + (*lbegin), m);
  line[m] = 0;

  return ((*lend) + 1);
}

int find_next_pragma(char * orig_buffer, items * input, int start, int end)
{
  int found, counter, index;
  item Item;

  if ((start < 0) || (start >= input->number_of_items) ||
      (end < 0) || (end >= input->number_of_items) ||
      (start > end))
    return -1;

  found = 0;
  counter = 0;
  for (index = start; index <= end; index++)
    {
      Item = input->data[index];

      if (DEBUG_PRAGMAS)
	printf("Scanning %s at %i\n", types_enum2str(Item.type), get_line_number(orig_buffer, Item.begin));

      switch (Item.type)
	{
	case PRAGMA_IF:
	  counter++;
	  break;

	case PRAGMA_ELSE:
	  if (counter == 0)
	    return index;
	  break;

	case PRAGMA_ENDIF:
	  if (counter == 0)
	    return index;
	  counter--;
	  break;

	default:
	  printf("Invalid pragma type\n"); /* other types must be filtered out before */
	  exit(-1);
	  break;
	}
    }

  printf("Cound not find closing pragma starting from %i and ending at %i\n", 
	 get_line_number(orig_buffer, input->data[start].begin),
	 get_line_number(orig_buffer, input->data[end].end));
  exit(-1);
}

element * parse_OR_pragmas(char * orig_buffer, items * input, int begin, int end, int * _index)
{
  item oldItem, Item;
  element * Pragmas, * p;
  int oldindex, index, x;
  int done;

  if (DEBUG_PRAGMAS)
    printf("Entering 'parse_OR_pragmas' with [%i, %i]\n", begin, end);

  Pragmas = NULL;

  Pragmas = create_pragmas(-1, -1, -1, -1, -1, OR, 10);

  /*
  if (begin < 0)
    begin = 0;
  if (end < 0)
    end = input->number_of_items - 1;
  */

  index = begin;
  assert(begin >= 0);
  assert(begin <= end);

  done = 0;
  while (1)
    {
      oldindex = index;
      if (done)
	index = find_next_pragma(orig_buffer, input, index + 1, end);

      oldItem = input->data[oldindex];

      if (index == -1)
	{
	  printf("Cannot find matching #else or #endif for #if pragma at %i\n", 
		 get_line_number(orig_buffer, oldItem.begin));
	  exit(-1);
	}

      Item = input->data[index];

      if (DEBUG_PRAGMAS)
	printf("Parsing pragmas in 'parse_OR_pragmas' at %i\n", get_line_number(orig_buffer, Item.begin));

      if (! done)
	{
	  if (oldItem.type != PRAGMA_IF)
	    {
	      printf("%s%s%s", "Internal error in parsing pragmas: ", 
		     "call to 'parse_OR_pragmas' ", 
		     "was made without #if present\n");
	      exit(-1);
	    }
	  /*
	  p = parse_AND_pragmas(orig_buffer, input, oldindex + 1, index - 1, &x);
	  p->pid = index;
	  add_pragma(Pragmas, p);
	  */
	  done = 1;
	}
      else
	{
	  switch(Item.type)
	    {
	    case PRAGMA_IF:
	      printf("%s%s%s%i\n", 
		     "Internal error in parsing pragmas: ", 
		     "program used #if from the lower level ", 
		     "to be as in the upper level at ", 
		     get_line_number(orig_buffer, Item.begin));
	      exit(-1);

	    case PRAGMA_ELSE:
	      p = parse_AND_pragmas(orig_buffer, input, oldindex + 1, index - 1, &x);
	      p->pid = oldindex;
	      add_pragma(Pragmas, p);
	      break;

	    case PRAGMA_ENDIF:
	      if (DEBUG_PRAGMAS)
		printf("Exiting 'parse_OR_pragmas'\n");
	      p = parse_AND_pragmas(orig_buffer, input, oldindex + 1, index - 1, &x);
	      p->pid = oldindex;
	      add_pragma(Pragmas, p);
	      /*
	      p = create_pragmas(index, -1, -1, -1, -1, AND, 10);
	      add_pragma(Pragmas, p);
	      */
	      *_index = index;
	      return Pragmas;

	    case PRAGMA_OTHER:
	    case STRING:
	    case LITERAL:
	    case COMMENT:
	    case OTHER:
	    case ESCSEQ:
	      printf("Internal error in pragmas handling\n");
	      exit(-1);
	    }
	}
    }

  /* should never be reached */
  printf("Reached unreachable code\n");
  exit(-1);
}

element * parse_AND_pragmas(char * orig_buffer, items * input, int begin, int end, int * _index)
{
  item Item;
  element * Pragmas, * p;
  int index, old_index;

  Pragmas = NULL;

  Pragmas = create_pragmas(-1, -1, -1, -1, -1, AND, 10);

  /*
  if (begin < 0)
    begin = 0;
  if (end < 0)
    end = input->number_of_items - 1;
  */

  index = begin;

  if (DEBUG_PRAGMAS)
    printf("Entering 'parse_AND_pragmas' with [%i, %i]\n", begin, end);

  if (begin > end)
    {
      if (DEBUG_PRAGMAS)
	printf("Exiting 'parse_AND_pragmas'\n");
      *_index = -1;
      return Pragmas;
    }

  old_index = index;
  while (index <= end)
    {
      old_index = index;
      Item = input->data[index];

      switch (Item.type)
	{
	case PRAGMA_IF: 
	  if (DEBUG_PRAGMAS)
	    printf("#if detected in 'parse_AND_pragmas' at %i\n", 
		   get_line_number(orig_buffer, Item.begin));

	  old_index = index;
	  p = parse_OR_pragmas(orig_buffer, input, old_index, end, &index);
	  p->pid = old_index;
	  add_pragma(Pragmas, p);

	  index++;

	  if (DEBUG_PRAGMAS)
	    printf("#if clause starting at %i was added successfully, next pragma number is %i\n", 
		   get_line_number(orig_buffer, Item.begin), 
		   index);
	  break;

	case PRAGMA_ELSE:
	case PRAGMA_ENDIF:
	  printf("#else or #endif appear without #if at %i\n", 
		 get_line_number(orig_buffer, Item.begin));
  	  exit(-1);
	  break;

	case PRAGMA_OTHER:
	case STRING:
	case LITERAL:
	case COMMENT:
	case OTHER:
	case ESCSEQ:
	  printf("Internal error in pragmas handling: more pragma types are present than should be\n");
	  exit(-1);
	}
    }

  if (DEBUG_PRAGMAS)
    printf("Exiting 'parse_AND_pragmas'\n");

  *_index = index;
  return Pragmas;
}

void select_branch(char * orig_buffer, char * buffer, 
		   element * Pragmas, items * deleted, int * selectors)
{
  init_items(deleted, 10);

  select_branch_internal(orig_buffer, buffer, Pragmas, deleted, selectors, 0);
}

void select_branch_internal(char * orig_buffer, char * buffer, 
			    element * Pragmas, items * deleted, 
			    int * selectors, int depth)
{
  int i, n, selector;

  n = Pragmas->number_of_elements;
  if (Pragmas->type == OR)
    {
      selector = selectors[depth];
      if (selector >= n)
	selector = n - 1;
      for (i = 0; i < n; i++)
	{
	  if (i != selector)
	    add_item(deleted, Pragmas->list[i]->text_begin, 
		     Pragmas->list[i]->text_end, OTHER);
	}
      select_branch_internal(orig_buffer, buffer, Pragmas->list[selector], 
			     deleted, selectors, depth + 1);
    }
  else
    {
      for (i = 0; i < n; i++)
	select_branch_internal(orig_buffer, buffer, Pragmas->list[i], 
			       deleted, selectors, depth); /* should be the same depth */
    }
}

depth_width compute_depth_width(element * Pragmas)
{
  int i, n;
  depth_width dw, max_dw;

  n = Pragmas->number_of_elements;

  max_dw.depth = 0;
  max_dw.width = 0;

  for (i = 0; i < n; i++)
    {
      dw = compute_depth_width(Pragmas->list[i]);
      max_dw.depth = MAX(max_dw.depth, dw.depth);
      max_dw.width = MAX(max_dw.width, dw.width);
    }

  if (Pragmas->type == OR)
    {
      max_dw.depth++;
      max_dw.width = MAX(max_dw.width, n);
    }

  return max_dw;
}

void fill_pdata(char * orig_buffer, items * inputs, element * Pragma)
{
  int i, ne, ni;
  item Item;

  ne = Pragma->number_of_elements;
  ni = inputs->number_of_items;

  Pragma->pragma_begin = -1;
  Pragma->pragma_end = -1;
  Pragma->pragma_type = -1;

  if (Pragma->pid >= 0)
    {
      Item = inputs->data[Pragma->pid];

      Pragma->pragma_begin = Item.begin;
      Pragma->pragma_end = Item.end;
      Pragma->pragma_type = Item.type; 
   }

  Pragma->text_begin = -1;
  Pragma->text_end = -1;

  for (i = 0; i < ne; i++)
    fill_pdata(orig_buffer, inputs, Pragma->list[i]);
}

void fill_tdata(char * orig_buffer, items * inputs, element * Pragma)
{
  int i, ne, first, last;

  ne = Pragma->number_of_elements;

  for (i = 0; i < ne; i++)
    fill_tdata(orig_buffer, inputs, Pragma->list[i]);

  switch (Pragma->type)
    {
    case OR:
      if (ne >= 1)
	{
	  Pragma->text_begin = Pragma->list[0]->text_begin;
	  Pragma->text_end = Pragma->list[ne - 1]->text_end;
	  break;
	}
      else
	{
	  printf("Incorrect usage of OR-type pragma (internal error)\n");
	  exit(-1);
	}
      break;

    case AND:
      if (ne > 0)
	{
	  Pragma->text_begin = Pragma->list[0]->text_begin;
	  Pragma->text_end = Pragma->list[ne - 1]->text_end;
	}
      else
	{
	  if (Pragma->pid < 0)
	    {
	      printf("Unassigned pid for AND-type pragma\n");
	      exit(-1);
	    }

	  first = Pragma->pid;
	  last = Pragma->pid + 1;

	  if (last >= inputs->number_of_items)
	    {
	      printf("Internal error: didn't catch unmatched #if (first = %i, last = %i)\n", first, last);
	      exit(-1);
	    }

	  Pragma->text_begin = inputs->data[first].begin;
	  Pragma->text_end = inputs->data[last].begin - 1;
	  if (Pragma->text_end < 0)
	    {
	      printf("Invalid pragma address: internal error\n");
	      exit(-1);
	    }
	}
      break;

    default:
      printf("Incorrect pragma element type (internal error)\n");
      exit(-1);
      break;
    }
}

char * types_enum2str(pragma_type x)
{
  char * result;
  result = "ERROR";

  switch(x)
    {
      /*
	case PRAGMA_DEFINE:
	result = "PRAGMA_DEFINE";
	break;
      */

    case PRAGMA_IF:
      result = "PRAGMA_IF";
      break;

      /*
	case PRAGMA_IFDEF:
	result = "PRAGMA_IFDEF";
	break;
      */

    case PRAGMA_ELSE:
      result = "PRAGMA_ELSE";
      break;

    case PRAGMA_ENDIF:
      result = "PRAGMA_ENDIF";
      break;

    case PRAGMA_OTHER:
      result = "PRAGMA_OTHER";
      break;

    case STRING:
      result = "STRING";
      break;

    case LITERAL:
      result = "LITERAL";
      break;

    case COMMENT:
      result = "COMMENT";
      break;

    case OTHER:
      result = "OTHER";
      break;

    case ESCSEQ:
      result = "ESCSEQ";
      break;

    default:
      printf("Invalid argument to 'types_enum2str'\n");
      exit(-1);
    }

  return result;
}

char * comp_types_enum2str(comp_type x)
{
  char * result;
  result = "ERROR";

  switch(x)
    {
    case AND:
      result = "AND";
      break;

    case OR:
      result = "OR";
      break;

    default:
      printf("Invalid argument to 'comp_types_enum2str'\n");
      exit(-1);
    }

  return result;
}

pragma_type get_pragma_type(char * token)
{
  if (DEBUG_PRAGMAS)
    printf("ptoken='%s'\n", token);

  if (token[0] == 0)
    return OTHER;

  if (strcmp(token, "#define") == 0)
    return PRAGMA_OTHER;

  if (strcmp(token, "#undef") == 0)
    return PRAGMA_OTHER;

  if (strcmp(token, "#if") == 0)
    return PRAGMA_IF;

  if (strcmp(token, "#ifdef") == 0)
    return PRAGMA_IF;

  if (strcmp(token, "#ifndef") == 0)
    return PRAGMA_IF;

  if (strcmp(token, "#else") == 0)
    return PRAGMA_ELSE;

  if (strcmp(token, "#elif") == 0)
    return PRAGMA_ELSE;

  if (strcmp(token, "#endif") == 0)
    return PRAGMA_ENDIF;

  if (token[0] == '#')
    return PRAGMA_OTHER;

  return OTHER;
}

pragma_type extract_pragma_name(char * line)
{
  int n, begin, end, index;
  char * token;
  pragma_type result;

  n = strlen(line);

  token = Malloc((n + 10) * sizeof(char));

  index = 0;
  index = get_token(line, line, n, index, &begin, &end, token);
  if (index >= 0)
    result = get_pragma_type(token);
  else
    result = OTHER;

  Free(token);

  return result;
}

int compute_VS(depth_width dw)
{
  int i, s;

  s = 1;

  for (i = 0; i < dw.depth; i++)
    s *= dw.width;

  if (DEBUG_SELECTORS)
    {
      printf("max depth = %i, max width = %i, number of choices = %i\n", 
	     dw.depth, dw.width, s);
    }

  return s;
}

void create_selectors(depth_width dw, int selector, int * selectors)
{
  int i, n;
  int * powers;

  n = dw.depth;
  assert(n > 0);

  powers = Malloc((n + 10) * sizeof(int));
  powers[n - 1] = dw.width;
  for (i = n - 2; i >= 0; i--)
    powers[i] = powers[i + 1] * dw.width;

  for (i = 0; i < n; i++)
    {
      if (i < (n - 1))
	selectors[i] = (selector / powers[i + 1]) % dw.width;
      else
	selectors[i] = selector % dw.width;
    }

  if (DEBUG_SELECTORS)
    {
      printf("%i: ", selector);
      for (i = 0; i < n; i++)
	printf("%i ", selectors[i]);
      printf("\n");
    }

  Free(powers);
}

