#makefile for the misc library

include ../Makefile.inc

default:
	@echo 
	@echo   Make options:
	@echo
	@echo   build:   build miscellaneous tools
	@echo   erase:   remove executables 
	@echo 

build: begin $(LIB_DIR)/libmisc.a $(executables) end

clean:
	rm *.o

erase: 
	rm $(executables) *.o

begin:
	@echo 
	@echo Building prioritization utilities...

file_utils.o: file_utils.h file_utils.c defs.h
	@ echo "          compiling file_utils"
	@ $(CC) -c $(CFLAGS) file_utils.c

test_matrix.o: test_matrix.c
	@ echo "          compiling test_matrix.c"
	@ $(CC) -c $(CFLAGS) test_matrix.c

vers.o: vers.h vers.c
	@ echo "          compiling vers.c"
	@ $(CC) -c $(CFLAGS) vers.c

$(LIB_DIR)/libmisc.a: file_utils.o test_matrix.o vers.o
	@ echo "          making libmisc.a"
	@ mkdir -p $(LIB_DIR)
	@ rm -f $(LIB_DIR)/libmisc.a
	@ ar -r $(LIB_DIR)/libmisc.a file_utils.o test_matrix.o vers.o

end:
	@echo Prioritization utilities build complete.
	@echo 

