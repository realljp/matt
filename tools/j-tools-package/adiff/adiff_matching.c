#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "adiff.h"

extern int flag_nested_comments;

int match_symbol(char * buffer, int index, char symbol)
{
  char * ptr = NULL;
  int i, flag, begin, end;
  int index_orig;

  index_orig = index;

  if (buffer[index] != symbol)
    return -1;
  index++;

  do
    {
      ptr = strchr(buffer + index, symbol);

      if (DEBUG_MATCHING)
	printf("index=%i, buffer[index...]=%s, *ptr=%s\n", index, buffer+index, ptr==NULL?"NULL":ptr);

      if (ptr != NULL)
	{
	  index = ptr - buffer + 1;
	  
	  if (ptr[-1] == '\\')
	    {
	      begin = index_orig + 1;
	      end = ptr - buffer - 1;
	      if (DEBUG_MATCHING)
		printf("buffer[begin=%i] = %c, buffer[end=%i] = %c\n", 
		       begin, buffer[begin], end, buffer[end], end);
	      assert(end >= begin); /* should never happen */
	      flag = 0;
	      for (i = begin; i <= end; i++)
		{
		  if (DEBUG_MATCHING)
		    printf("%i: %c\n", i, buffer[i]);
		  if (buffer[i] == '\\')
		    {
		      if (flag)
			flag = 0;
		      else
			flag = 1;
		    }
		  else
		    flag = 0;
		}
	      if (flag)
		continue;
	      else
		break;
	    }
	  else
	    {
	      break;
	    }
	}
      else
	{
	  index = strlen(buffer);
	  break;
	}
    }
  while(1);

  if (DEBUG_MATCHING)
    printf("finished: index=%i\n", index);

  return index;
}

int matchComment(char * buffer, int index, items * Items)
{
  char * ptr = NULL;

  if (buffer[index] != '/')
    return -1;

  if (buffer[index + 1] != '*')
    return -1;

  ptr = strstr(buffer + index + 2, "*/");

  if (ptr != NULL)
    {
      /*      add_item(Items, index, ptr - buffer + 1); */
      index = ptr - buffer + 2;
    }
  else
    {
      index = strlen(buffer);
    }

  return index;
}

void find_comments_and_literals(char * orig_buffer, char * buffer, items * Items, int add_literals, int add_comments, int add_backslashes)
{
  int i, n, end;

  n = strlen(buffer);

  for (i = 0; i < n;)
    {
      switch (buffer[i])
	{
	case '\"':
	  end = match_symbol(buffer, i, '\"');
	  assert(end > 0);
	  if (add_literals)
	    add_item(Items, i, end - 1, STRING);
	  i = end;
	  break;

	case '\'':
	  end = match_symbol(buffer, i, '\'');
	  assert(end > 0);
	  if (add_literals)
	    add_item(Items, i, end - 1, LITERAL);
	  i = end;
	  break;

	case '/':
	  if (buffer[i + 1] == '*')
	    {
	      if (flag_nested_comments)
		{
		  end = match_bracket(orig_buffer, buffer, n, i, "/*", "*/");
		  if (end < 0)
		    {
		      printf("No matching closing comment\n");
		      exit(-1);
		    }
		  if (add_comments)
		    add_item(Items, i, end + 1, COMMENT);
		  i = end + 2;
		}
	      else
		{
		  end = matchComment(buffer, i, Items);
		  assert(end > 0);
		  if (add_comments)
		    add_item(Items, i, end - 1, COMMENT);
		  i = end;
		}
	    }
	  else
	    i++;
	  break;
	  
	case '\\':
	  if (add_backslashes)
	    add_item(Items, i, i + 1, ESCSEQ);
	  i += 2;
	  break;
	  
	default:
	  i++;
	  break;
	}

      if (i < 0)
	{
	  printf("Internal error detected\n");
	  exit(-1);
	}
    }
      
}

