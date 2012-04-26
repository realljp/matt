#makefile for the tools

executables = 

default:
	@echo 
	@echo   Make options:
	@echo
	@echo   build:   build prioritization tools
	@echo   erase:   remove executables 
	@echo   build-all:   build all tools
	@echo   erase-all:   erases all tools
	@echo 

build-all:
	echo "Building all tools..."
	(cd gen_fault_matrix; make build)
	echo "Finished building of all tools"

build: begin $(executables) end

erase: 
	rm -f $(executables) *.o

erase-all:
	echo "Erasing all tools..."
	make -i erase
	(cd gen_fault_matrix; make -i erase)
	echo "Finished erasing of all tools"

begin:
	@echo 
	@echo Building prioritization utilities...

end:
	@echo Prioritization utilities build complete.
	@echo 

