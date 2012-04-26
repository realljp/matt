# Include file for make

SRCDIR = .
OBJDIR = .
BINDIR = .
TOOLS1HDRDIR = .
TOOLS2HDRDIR = ../misc_lib
TOOLS3HDRDIR = 
TOOLS4HDRDIR = 


MISC_HDRS = ../misc_lib/defs.h ../misc_lib/file_utils.h \
		../misc_lib/vers.h ../misc_lib/test_matrix.h

CC = gcc
CFLAGS = -g \
	-I$(TOOLS1HDRDIR) -I$(TOOLS2HDRDIR) -I$(TOOLS3HDRDIR) -I$(TOOLS4HDRDIR) \
	-O -c
ACFLAGS = -g \
	-I$(TOOLS1HDRDIR) -I$(TOOLS2HDRDIR) -I$(TOOLS3HDRDIR) -I$(TOOLS4HDRDIR) \
	-O -c
LIBFLAGS = -lm

OBJS = 
SRC = 
EXES = 

LIB_DIR = ../lib
