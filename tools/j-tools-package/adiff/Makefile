#makefile for the tools

include ../Makefile.inc

executables = adiff

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
	@echo Building adiff...

end:
	@echo 
	@echo Finished building adiff

adiff_parse.o: adiff.h adiff_parse.c
	@ echo "          compiling adiff_parse.c"
	@ $(CC) -c adiff_parse.c $(CFLAGS)

adiff_pragmas.o: adiff.h adiff_pragmas.c
	@ echo "          compiling adiff_pragmas.c"
	@ $(CC) -c adiff_pragmas.c $(CFLAGS)

adiff_diff.o: adiff.h adiff_diff.c
	@ echo "          compiling adiff_diff.c"
	@ $(CC) -c adiff_diff.c $(CFLAGS)

adiff_storage.o: adiff.h adiff_storage.c
	@ echo "          compiling adiff_storage.c"
	@ $(CC) -c adiff_storage.c $(CFLAGS)

adiff_matching.o: adiff.h adiff_matching.c
	@ echo "          compiling adiff_matching.c"
	@ $(CC) -c adiff_matching.c $(CFLAGS)

adiff.o: adiff.h adiff.c
	@ echo "          compiling adiff.c"
	@ $(CC) -c adiff.c $(CFLAGS)

adiff_tokens.o: adiff.h adiff_tokens.c
	@ echo "          compiling adiff_tokens.c"
	@ $(CC) -c adiff_tokens.c $(CFLAGS)

adiff: adiff.h adiff_parse.o adiff_diff.o adiff_storage.o adiff_matching.o adiff_pragmas.o adiff_tokens.o  adiff.o
	@ echo "          linking adiff"
	@ $(CC) -o adiff adiff_parse.o adiff_diff.o adiff_storage.o adiff_matching.o adiff_pragmas.o adiff_tokens.o adiff.o $(LIBFLAGS)

