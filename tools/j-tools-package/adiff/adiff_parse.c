#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "adiff.h"

extern int flag_find_full_function;

extern char error_message[1024];

extern int number_of_choices_limit;

int get_line_number(char * buffer, int index)
{
  int i, count;

  if (index < 0)
    return -1;

  if (index > strlen(buffer)) /* must also include the last '0' character */
    {
      printf("Incorrect usage of get_line_number (index = %i, buffer length = %i)\n", 
	     index, strlen(buffer));
      exit(-1);
    }

  count = 1;
  for (i = 0; i < index; i++)
    if (buffer[i] == '\n')
      count++;

  return count;
}

void invert(char * src, char * dest)
{
  int i, n;

  n = strlen(src);
  for (i = 0; i < n; i++)
    dest[i] = src[n - i - 1];

  dest[n] = 0;
}

int match_bracket(char * orig_buffer, char * buffer, int n, 
		  int index, char * opening, char * closing)
{
  int counter, begin, end, flag, old_index;
  char * token;

  token = malloc((n + 10) * sizeof(char));
  assert(token != NULL);

  counter = 0;
  flag = 0;
  do
    {
      old_index = index;
      index = get_token(orig_buffer, buffer, n, index, &begin, &end, token);

      if (index < 0)
	{
	  Free(token);
	  sprintf(error_message, "Cannot find token '%s' at line %i", 
		 opening, get_line_number(orig_buffer, old_index));
	  return ERROR;
	}

      if (! flag)
	if (strcmp(token, opening) != 0)
	  {
	    sprintf(error_message, 
		    "Invalid call to 'match_bracket': must be '%s' insted of '%s'", 
		    opening, 
		    token);
	    return ERROR;
	  }

      flag = 1;

      if (strcmp(token, opening) == 0)
	counter++;

      if (strcmp(token, closing) == 0)
	counter--;

      if (counter == 0)
	{
	  Free(token);
	  if (DEBUG_EXTRACTING)
	    printf("%s [%s %s] %i %i\n", token, opening, closing, 
		   get_line_number(orig_buffer, begin), get_line_number(orig_buffer, end));
	  return begin;
	}

    } while (1);

  Free(token);

  return ERROR; /* should never be reached */
}

int get_next_function(char * orig_buffer, char * buffer, int current,  
		      char * fname, int * fbegin, int * fend, int prev_decl_end)
{
  int begin, end, index, n, old_index, flag_found, body_begin, func_name_index;
  int i, val;
  char * token, * old_token;
  
  n = strlen(buffer);
  
  token = malloc((n + 10) * sizeof(char));
  old_token = malloc((n + 10) * sizeof(char));

  strcpy(token, "");

  index = current;
  old_index = index;

  while (1)
    {
      old_index = index;
      strcpy(old_token, token);
      index = get_token(orig_buffer, buffer, n, index, &begin, &end, token);

      if (index < 0)
	break;

      if (DEBUG_EXTRACTING)
	printf("Processing line %i (\"%s\")\n", 
	       get_line_number(orig_buffer, old_index), token);

      if (strcmp(token, "(") == 0)
	{
	  if (is_identifier(old_token))
	    {
	      func_name_index = old_index;

	      strcpy(fname, old_token);

	      index = match_bracket(orig_buffer, buffer, n, old_index, "(", ")");
	      if (index < 0)
		{
		  sprintf(error_message, "Cannot find matching ')'");
		  return ERROR;
		}
	      old_index = index;
	  
	      index++;
	      if (index >= n)
		{
		  sprintf(error_message, "Cannot find function body");
		  return ERROR;
		}
		
	      old_index = index;
	      index = get_token(orig_buffer, buffer, n, index, &begin, &end, token);

	      if ((strcmp(token, ";") == 0) || (strcmp(token, ",") == 0))
		continue; /* found function declaration without body */

	      *fbegin = find_token(orig_buffer, buffer, n, old_index, "{");

	      if (*fbegin < 0)
		{
		  sprintf(error_message, "Cannot find function body");
		  return ERROR;
		}

	      body_begin = *fbegin;

	      if (flag_find_full_function)
		{
		  flag_found = 0;
		  for (i = func_name_index; (i >= 0) && (i > prev_decl_end); i--)
		    if (buffer[i] == ';')
		      {
			flag_found = 1;
			*fbegin = i + 1;
			break;
		      }
		  if (! flag_found)
		    *fbegin = prev_decl_end + 1;
		  val = get_token(orig_buffer, buffer, n, *fbegin, &begin, &end, token);
		  if (val < 0)
		    {
		      sprintf(error_message, 
			      "Internal error in 'get_next_function' (no function name and body)");
		      return ERROR;
		    }

		  *fbegin = begin;
		}

	      *fend = match_bracket(orig_buffer, buffer, n, body_begin, "{", "}");
	      if (*fend < 0)
		{
		  sprintf(error_message, "Cannot find function body");
		  return ERROR;
		}

	      index = *fend + 1;

	      if (DEBUG_EXTRACTING)
		printf("Found function '%s' in lines %i ... %i\n", 
		       fname, get_line_number(orig_buffer, *fbegin), 
		       get_line_number(orig_buffer, *fend));
	      Free(token);

	      return index;
	    }
	  else
	    {
	      index = match_bracket(orig_buffer, buffer, n, old_index, "(", ")");
	      if (index < 0)
		{
		  sprintf(error_message, "No matching ')'");
		  return ERROR;
		}
	      index++;
	      continue;
	    }
	}

      if (strcmp(token, "[") == 0)
	{
	  index = match_bracket(orig_buffer, buffer, n, old_index, "[", "]");
	  if (index < 0)
	    {
	      sprintf(error_message, "Cannot find matching ']'");
	      return ERROR;
	    }
	  continue;
	}

      if (strcmp(token, "{") == 0)
	{
	  index = match_bracket(orig_buffer, buffer, n, old_index, "{", "}");
	  if (index < 0)
	    {
	      sprintf(error_message, "Cannot find matching '}'");
	      return ERROR;
	    }
	  continue;
	}
    }

  Free(token);
  
  return END;
}

int find_functions(char * buffer, fentries * functions)
{
  int i, number_of_choices, choice, counter, x, error, current_number_of_choices;
  fentries * functions_arr;
  
  find_functions_internal(buffer, functions, -1, &number_of_choices);

  current_number_of_choices = number_of_choices;
  if (current_number_of_choices > number_of_choices_limit)
    {
      printf("%s%i%s%i%s\n", 
	     "WARNING: search space for pragmas in too large (number_of_choices = ", 
	     current_number_of_choices, 
	     "), reducing it to ", 
	     number_of_choices_limit, 
	     ": RESULTS CAN BE INCORRECT");
      current_number_of_choices = number_of_choices_limit;
    }

  functions_arr = Malloc((number_of_choices + 10) * sizeof(fentries));

  counter = 0;
  for (i = 0; i < current_number_of_choices; i++)
    {
      finit(&functions_arr[counter], 100);
      strcpy(error_message, "");
      error = find_functions_internal(buffer, &functions_arr[counter], i, &x);
      assert(x == number_of_choices);
      if (error != ERROR)
	counter++;
      else
	{
	  if (DEBUG_WARNINGS)
	    printf("WARNING: Parse error %i-th pragma choice \"%s\"\n", 
		   i, error_message);
	}
      /*
      if (i != max_selector)
	func_free(functions);
      */
    }

  if (counter == 0)
    {
      printf("ERROR: %s\n", error_message);
      exit(-1);
    }

  select_best_func_limits(functions_arr, counter, functions);

  if (check_func_duplicates(functions))
    {
      printf("WARNING: duplicate function names found: RESULTS CAN BE INCORRECT\n");
    }

  if (check_func_overlap(functions))
    {
      printf("%s%s\n", 
	     "WARNING: function declarations overlapped: ", 
	     "RESULTS can show more changed functions than necessary");
    }

  if (FREE)
    Free(functions_arr);
}

int check_func_duplicates(fentries * functions)
{
  int i, j, n;

  n = functions->num_funcs;

  for (i = 0; i < n; i++)
    {
      for (j = 0; j < n; j++)
	if (i != j)
	  if (strcmp(functions->data[i].fname, functions->data[j].fname) == 0)
	    return 1;
    }

  return 0;
}

int check_func_overlap(fentries * functions)
{
  int i, j, n;

  n = functions->num_funcs;

  for (i = 0; i < n; i++)
    {
      for (j = 0; j < n; j++)
	if (i != j)
	  {
	    if (between(functions->data[i].fbegin, 
			functions->data[j].fbegin, 
			functions->data[j].fend))
	      return 1;

	    if (between(functions->data[i].fend, 
			functions->data[j].fbegin, 
			functions->data[j].fend))
	      return 1;
	  }
    }

  return 0;
}

/*
int fix_func_overlap(fentries * functions)
{
  int i, n;

  n = functions->num_funcs;

  for (i = 0; i < n; i++)
    {
      for (j = 0; j < n; j++)
	if (i != j)
	  {
	    if (between(functions->data[i].fbegin, 
			functions->data[j].fbegin, 
			functions->data[j].fend))
	      functions->data[i].fbegin = functions->data[j].fbegin;
		
	  }
    }
}
*/

int between(int x, int min, int max)
{
  if ((x >= min) && (x <= max))
    return 1;
  else
    return 0;
}


void select_best_func_limits(fentries * source, int counter, fentries * destination)
{
  int i, j, k, found;

  finit(destination, 100);

  for (i = 0; i < counter; i++)
    {
      for (j = 0; j < source[i].num_funcs; j++)
	{
	  found = 0;
	  for (k = 0; k < destination->num_funcs; k++)
	    if (strcmp(destination->data[k].fname, source[i].data[j].fname) == 0)
	      {
		found = 1;
		break;
	      }

	  if (found)
	    {
	      if (DEBUG_PRAGMAS_1)
		printf("Updating \"%s\"...\n", source[i].data[j].fname);
	      destination->data[k].fbegin = MIN(destination->data[k].fbegin, 
						source[i].data[j].fbegin);
	      destination->data[k].fend = MAX(destination->data[k].fend, 
					      source[i].data[j].fend);
	    }
	  else
	    {
	      if (DEBUG_PRAGMAS_1)
		printf("Adding \"%s\"...\n", source[i].data[j].fname);
	      fadd(destination, source[i].data[j].fname, 
		   source[i].data[j].fbegin, source[i].data[j].fend);
	    }
	}
    }
}

int find_functions_internal(char * buffer, fentries * functions, 
			    int choice, int * number_of_choices)
{
  int counter, n, m, i, val, prev_decl_end;
  char * newbuffer;
  int len;
  char buff[MAX_NAME_LENGTH];
  items Items, Items_pragmas, Items_pragmas_other, 
    Items_pragmas_control, Items_pragmas_unselected;
  char * name;
  int begin, end, index;
  element * Pragmas;
  depth_width dw;
  int * selectors;

  n = strlen(buffer);

  name = Malloc((n + 10) * sizeof(char));

  init_items(&Items, n + 10);

  newbuffer = strdup(buffer);
  assert(newbuffer != NULL);

  find_comments_and_literals(buffer, newbuffer, &Items, 1, 1, 1);
  clear(newbuffer, &Items);

  init_items(&Items_pragmas, n + 10);

  find_pragmas(buffer, newbuffer, &Items_pragmas);

  copy_items_type(&Items_pragmas, &Items_pragmas_other, PRAGMA_OTHER);
  clear(newbuffer, &Items_pragmas_other);
  delete_items_type(&Items_pragmas, &Items_pragmas_control, PRAGMA_OTHER);
  
  if (DEBUG_PRAGMAS)
    print_items(buffer, &Items_pragmas);

  *number_of_choices = 1;

  if (Items_pragmas_control.number_of_items > 0)
    {
      Pragmas = parse_AND_pragmas(buffer, &Items_pragmas_control, 
			      0, Items_pragmas_control.number_of_items - 1, &val);

      fill_pdata(buffer, &Items_pragmas_control, Pragmas);
      fill_tdata(buffer, &Items_pragmas_control, Pragmas);

      if (DEBUG_PRAGMAS)
	print_pragmas(buffer, Pragmas, 0);

      dw = compute_depth_width(Pragmas);

      selectors = malloc((dw.depth + 10) * sizeof(int));

      *number_of_choices = compute_VS(dw);

      if (choice >= 0)
	{
	  create_selectors(dw, choice, selectors);
	  
	  select_branch(buffer, newbuffer, Pragmas, &Items_pragmas_unselected, selectors);
	  
	  clear(newbuffer, &Items_pragmas_unselected);
	}

      free_pragmas(Pragmas);
    }

  clear(newbuffer, &Items_pragmas);

  index = 0;
  if (choice >= 0)
    {
      index = 0;
      prev_decl_end = -1;
      while ((index = get_next_function(buffer, newbuffer, index, 
					name, &begin, &end, prev_decl_end)) >= 0)
	{
	  prev_decl_end = end;
	  fadd(functions, name, begin, end);
	}
    }

  Free(newbuffer);
  free_items(&Items);
  free_items(&Items_pragmas);
  free_items(&Items_pragmas_other);
  free_items(&Items_pragmas_control);
  free_items(&Items_pragmas_unselected);

  return index;
}

int move_to_BOL(char * buffer, int index)
{
  if (index <= 0)
    return index;
  index--;
  while ((index > 0) && (buffer[index] != '\n')) index--;
  if (buffer[index] == '\n')
    index++;
  return index;
}

int move_to_EOL(char * buffer, int n, int index)
{
  if (index >= n)
    return index;

  while ((index < n) && (buffer[index] != '\n')) index++;

  if ((n > 0) && (index >= n))
    index--;
  assert(index < n);

  return index;
}
