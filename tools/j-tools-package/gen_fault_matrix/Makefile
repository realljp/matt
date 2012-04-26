# Makefile for creation of fault matrices

include ../Makefile.inc

executables = gen_temp_file get_fault_matrix_stats combine_fault_data gen_newVer

default:
	@echo 
	@echo   Make options:
	@echo
	@echo   build:   build fault matrix creation tools tools
	@echo   erase:   remove executables 
	@echo 

build: begin $(executables) end

erase: 
	rm $(executables) *.o

begin:
	@echo 
	@echo Building fault matrix creation tools ...

gen_temp_file: gen_temp_file.c
	@ echo "          compiling gen_temp_file"
	@ $(CC) -o gen_temp_file gen_temp_file.c $(LIBFLAGS)

get_fault_matrix_stats: get_fault_matrix_stats.c $(MISC_HDRS) $(LIB_DIR)/libmisc.a
	@ echo "          compiling get_fault_matrix_stats"
	@ $(CC)  $(CFLAGS) get_fault_matrix_stats.c
	@ $(CC) -o get_fault_matrix_stats get_fault_matrix_stats.o $(LIB_DIR)/libmisc.a $(LIBFLAGS)

combine_fault_data: combine_fault_data.c $(MISC_HDRS) $(LIB_DIR)/libmisc.a
	@ echo "          compiling combine_fault_data"
	@ $(CC)  $(CFLAGS) combine_fault_data.c 
	@ $(CC) -o combine_fault_data combine_fault_data.o $(LIB_DIR)/libmisc.a $(LIBFLAGS)

gen_newVer: gen_newVer.c $(MISC_HDRS)
	@ echo "          compiling gen_newVer"
	@ $(CC)  $(CFLAGS) gen_newVer.c
	@ $(CC) -o gen_newVer gen_newVer.o $(LIBFLAGS)

end:
	@echo 
	@echo Building fault matrix creation tools is completed
