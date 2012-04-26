#include <stdio.h>


main(int argc, char *argv[])
{

FILE *fp, *fopen();
float det, tot;
int *B;
int first, sec;
int num_data;
int n=0;
float sum=0.0;
float rate=0.0;


    if((fp  = fopen(argv[1],"r")) != NULL){
        while(!feof(fp)){
	    fscanf(fp,"%f %f \n", &det, &tot);
	    rate = det/tot;
            printf("%f\n", rate);
	    rate=0.0;

        }
    }

}
