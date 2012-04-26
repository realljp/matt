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

int is_delimiter(char c)
{
 char * delimiters = "!@#$%^&*()-+=|\\`~[]{};:'\"<>,.?/ \t\r\n";

 if (strchr(delimiters, c) == NULL)
   return 0;
 else
   return 1;
}

int is_space(char c)
{
  if ((c == ' ') || (c == '\t') || (c == '\n') || (c == '\r'))
    return 1;
  else
    return 0;
}

int skip_spaces(char * buffer, int index)
{
  assert(index >= 0);

  while (is_space(buffer[index])) index++;

  return index;
}

int get_simple_token(char * orig_buffer, char * buffer, int n, int index, 
		     int * begin, int * end, char * token)
{
  if ((index < 0) || (index >= n))
    return -1;

  index = skip_spaces(buffer, index);
  
  if (index >= n)
    return -1;

  *begin = index;
  
  if (is_delimiter(buffer[index]))
    {
      *end = *begin;
    }
  else
    {
      index++;
      
      for (; index < n; index++)
	if (is_delimiter(buffer[index]))
	  break;
     
      *end = index - 1;
    }
  
  strncpy(token, buffer + (*begin), (*end) - (*begin) + 1); /* ??? + 2 or + 1 ??? */
  token[(*end) - (*begin) + 1] = 0;

  index = skip_spaces(buffer, (*end) + 1);
  
  return index;
}

int compatible_tokens(char * token1, char * token2)
{
  char c1, c2;
  int i;

  if (token1[0] == '#')
    {
      if (token1[1] == 0)
	return 1;
    }

  if ((token1[0] == 0) || (token2[0] == 0))
    return 0;
  
  if ((token1[1] != 0) || (token2[1] != 0))
    return 0;
  
  c1 = token1[0];
  c2 = token2[0];

  for (i = 1; i <= 2; i++)
    {
      if ((c1 == '<') || (c1 == '>') || (c1 == '='))
	if (c2 == '=')
	  return 1;
      
      if ((c1 == '+') || (c1 == '-') || 
	  (c1 == '/') || (c1 == '*') || 
	  (c1 == '&') || (c1 == '|') || 
	  (c1 == '%') || (c1 == '^') ||
	  (c1 == '~') || (c1 == '!'))
	if (c2 == '=')
	  return 1;
      
      if ((c1 == '+') || (c1 == '-') || 
	  (c1 == '/') || (c1 == '*') || 
	  (c1 == '&') || (c1 == '|') || 
	  (c1 == '%') || (c1 == '^') ||
	  (c1 == '~') || (c1 == '!'))
	if (c2 == '=')
	  return 1;
      
      if ((c1 == '<') || (c1 == '>') || (c1 == '='))
	if (c2 == '=')
	  return 1;
      
      if ((c1 == '<') && (c2 == '<'))
	return 1;
      
      if ((c1 == '>') && (c2 == '>'))
	return 1;

      if ((c1 == '-') && (c2 == '>'))
	return 1;

      if ((c1 == '+') && (c2 == '+'))
	return 1;

      if ((c1 == '-') && (c2 == '-'))
	return 1;

      if ((c1 == '|') && (c2 == '|'))
	return 1;

      if ((c1 == '&') && (c2 == '&'))
	return 1;

      if ((c1 == '*') && (c2 == '/'))
	return 1;

      if ((c1 == '/') && (c2 == '*'))
	return 1;

      c1 = token2[0];
      c2 = token1[0];
    }

  return 0;
}

int get_token(char * orig_buffer, char * buffer, int n, int index, 
	      int * begin, int * end, char * token)
{
  int old_index, index1, index2, i, n1, n2, 
    begin1, end1, begin2, end2, special_case_flag;
  char * ptr1, * ptr2;

  while (1)
    {
      ptr1 = token;
      old_index = index;
      index1 = get_simple_token(orig_buffer, buffer, n, index, &begin1, &end1, ptr1);
      *begin = begin1;
      *end = end1;
      if (index1 < 0)
	{
	  index = -1;
	  break;
	}
      ptr2 = token + strlen(token) + 1;
      index2 = get_simple_token(orig_buffer, buffer, n, index1, &begin2, &end2, ptr2);

      if (index2 < 0)
	{
	  index = index1;
	  break;
	}
      
      special_case_flag = 0;
      if (allow_space_in_pragma_name)
	if (ptr1[0] == '#')
	  if (ptr1[1] == 0)
	    special_case_flag = 1;
      
      if ((index + strlen(ptr1) != index1) && (! special_case_flag))
	{
	  index = index1;
	  break;
	}
      
      if (compatible_tokens(ptr1, ptr2))
	{
	  n1 = strlen(ptr1);
	  n2 = strlen(ptr2);
	  for (i = 0; i <= n2; i++)
	    ptr1[n1 + i] = ptr2[i];
	  *begin = begin1;
	  *end = end2;
	  index = index2;
	  break;
	}
      else
	{
	  index = index1;
	  break;
	}

      break;
    }

  if (DEBUG_TOKENS)
    if (index >= 0)
      printf("Token '%s' at line %i\n", token, get_line_number(orig_buffer, index));

  return index;
}

int is_identifier(char * token)
{
  int i, n;
  n = strlen(token);
  if (n == 0)
    return 0;
  for (i = 0; i < n; i++)
    if (is_delimiter(token[i]))
      return 0;
  return 1;
}

int find_token(char * orig_buffer, char * buffer, int n, int index, char * target)
{
  int counter, begin, end;
  char * token;

  token = malloc((n + 10) * sizeof(char));
  assert(token != NULL);

  do
    {
      index = get_token(orig_buffer, buffer, n, index, &begin, &end, token);

      if (index < 0)
	{
	  Free(token);
	  return -1;
	}

      if (DEBUG_FUNC_TOKENS)
	printf("Checking tokens in function body \"%s\" [%i:%i]\n", 
	       token, index, get_line_number(orig_buffer, index));

      if (strcmp(token, target) == 0)
	{
	  Free(token);
	  return begin;
	}

    } while (1);

  Free(token);

  return -1; /* should never be reached */
}

int is_data_declaration(char * token)
{
  if (strcmp(token, "struct") == 0)
    return 1;
  if (strcmp(token, "union") == 0)
    return 1;
  if (strcmp(token, "enum") == 0)
    return 1;
  if (strcmp(token, "=") == 0)
    return 1;
  return 0;
}
