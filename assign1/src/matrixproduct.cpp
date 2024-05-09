#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <papi.h>
#include <fstream>
#include <math.h>

using namespace std;

#define SYSTEMTIME clock_t
#define L1_DCM 0
#define L2_DCM 1
#define LD_INS 2
#define SR_INS 3
#define PRF_DM 4  // Data prefetch cache misses



void OnMult(int m_ar, int m_br, ofstream &file) 
{
	
	SYSTEMTIME Time1, Time2;
	
	char st[100];
	double temp;
	int i, j, k;

	double *pha, *phb, *phc;
	

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;



	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);



    Time1 = clock();

	for(i=0; i<m_ar; i++)
	{	for( j=0; j<m_br; j++)
		{	temp = 0;
			for( k=0; k<m_ar; k++)
			{	
				temp += pha[i*m_ar+k] * phb[k*m_br+j];
			}
			phc[i*m_ar+j]=temp;
		}
	}


    Time2 = clock();
	
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	file << st;
	double t = 2 * pow(m_ar,3);
	file << "MFlops: " << (pow(10, -6)) * t / ((double)(Time2 - Time1) / CLOCKS_PER_SEC) << endl;

	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;


    free(pha);
    free(phb);
    free(phc);
}

// add code here for line x line matriz multiplication
void OnMultLine(int m_ar, int m_br, ofstream &file)
{
    SYSTEMTIME Time1, Time2;
	char st[100];
	
	double *a, *b, *c;
	a = (double *)malloc((m_ar * m_ar) * sizeof(double));
	b = (double *)malloc((m_ar * m_ar) * sizeof(double));
	c = (double *)malloc((m_ar * m_ar) * sizeof(double));	

	for(int i=0; i<m_ar; i++)
		for(int j=0; j<m_ar; j++)
			a[i*m_ar + j] = (double)1.0;
	
	for(int i=0; i<m_br; i++)
		for(int j=0; j<m_br; j++)
			b[i*m_br + j] = (double)(i+1);

	for(int i=0; i<m_br; i++)
		for(int j=0; j<m_br; j++)
			c[i*m_br + j] = (double)0.0;

	Time1 = clock();
	int temp;
	for(int i = 0; i < m_ar; i++){
		for(int j = 0; j < m_br; j++){
			for(int k = 0; k < m_br; k++){
				// c[i][k] = c[i][k] + ( a[i][j] * b[j][k] );
				c[i * m_ar + k] = c[i * m_ar + k] + (a[i*m_ar + j] * b[j * m_ar + k]);
			}
		}
	}


	Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	file << st;

	double t = 2 * pow(m_ar,3);
	file << "MFlops: " << (pow(10, -6)) * t / ((double)(Time2 - Time1) / CLOCKS_PER_SEC) << endl;
	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;

	for(int i=0; i<1; i++)
	{	for(int j=0; j<min(10,m_br); j++)
			cout << c[j] << " ";
	}
	cout << endl;
    
}

int findMin(int a, int b){
	return a > b ? b : a;
}

// add code here for block x block matriz multiplication
void OnMultBlock(int m_ar, int m_br, int bkSize, ofstream& file)
{
	SYSTEMTIME Time1, Time2;
	char st[100];
	
	double *a, *b, *c;
	a = (double *)malloc((m_ar * m_ar) * sizeof(double));
	b = (double *)malloc((m_ar * m_ar) * sizeof(double));
	c = (double *)malloc((m_ar * m_ar) * sizeof(double));	
	
	for(int i=0; i<m_ar; i++)
		for(int j=0; j<m_ar; j++)
			a[i*m_ar + j] = (double)1.0;
	
	for(int i=0; i<m_br; i++)
		for(int j=0; j<m_br; j++)
			b[i*m_br + j] = (double)(i+1);

	for(int i=0; i<m_br; i++)
		for(int j=0; j<m_br; j++)
			c[i*m_br + j] = (double)(0.0);

	Time1 = clock();
	int temp;

	
	for(int ii = 0; ii < m_ar; ii+=bkSize){
		for(int jj=0; jj< m_ar ; jj+= bkSize){
			for(int kk=0; kk < m_ar; kk+= bkSize){
				for(int i=ii; i< ii + bkSize; i++){
					for(int j = jj; j < jj+bkSize; j++){
						for(int k = kk; k<  kk + bkSize ; k++){
							c[i * m_ar + k]+= a[i*m_ar + j] * b[j * m_ar + k];
						}
						
					}
				}
			}
		}
	}
	
	
	Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	file << st;

	int n_blocks = m_ar / bkSize;

	double t = 2 * pow(m_ar,3);
	file << "MFlops: " << pow(10,-6) * t / ((double)(Time2 - Time1) / CLOCKS_PER_SEC) << endl;
	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;

	for(int i=0; i<1; i++)
	{	for(int j=0; j<min(10,m_br); j++)
			cout << c[j] << " ";
	}
	cout << endl;
}



void handle_error (int retval)
{
  	printf("PAPI error %d: %s\n", retval, PAPI_strerror(retval));
  	exit(1);
}

void init_papi() {
  	int retval = PAPI_library_init(PAPI_VER_CURRENT);
  	if (retval != PAPI_VER_CURRENT && retval < 0) {
    	printf("PAPI library version mismatch!\n");
    	exit(1);
  	}	
  	if (retval < 0) handle_error(retval);

  	std::cout << "PAPI Version Number: MAJOR: " << PAPI_VERSION_MAJOR(retval)
            << " MINOR: " << PAPI_VERSION_MINOR(retval)
            << " REVISION: " << PAPI_VERSION_REVISION(retval) << "\n";
}



int main (int argc, char *argv[])
{

	char c;
	int lin, col, blockSize;
	int op;
	
	int EventSet = PAPI_NULL;
	
  	long long values[2];
  	int ret;
	
	
	ret = PAPI_library_init( PAPI_VER_CURRENT );
	if ( ret != PAPI_VER_CURRENT )
		std::cout << "FAIL" << endl;


	ret = PAPI_create_eventset(&EventSet);
		if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L1_DCM );
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;

	ret = PAPI_add_event(EventSet,PAPI_L2_DCM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl;

	ret = PAPI_add_event(EventSet,PAPI_LD_INS);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_LD_INS" << endl;

	ret = PAPI_add_event(EventSet,PAPI_SR_INS);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_SR_INS" << endl;

	ret = PAPI_add_event(EventSet,PAPI_PRF_DM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_PRF_DM" << endl;

	
	
	op=1;
	do {
		cout << endl << "1. Multiplication" << endl;
		cout << "2. Line Multiplication" << endl;
		cout << "3. Block Multiplication" << endl;
		cout << "Selection?: ";
		cin >>op;
		if (op == 0)
			break;
		printf("Dimensions: lins=cols ? ");
   		cin >> lin;
   		col = lin;


		// Start counting
		ret = PAPI_start(EventSet);
		if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;
		
		ofstream myfile;
		std::string filename;
		switch (op){
			
			case 1: 
				filename = "selection" +  to_string(op) + "_" + to_string(lin) + ".txt";
				myfile.open(filename);
				OnMult(lin, col, myfile);
				break;
			case 2:
				filename = "selection" +  to_string(op) + "_" + to_string(lin) + ".txt";
				myfile.open(filename);
				OnMultLine(lin, col, myfile);  
				break;
			case 3:
				cout << "Block Size? ";
				cin >> blockSize;
				filename = "selection" +  to_string(op) + "_" + to_string(lin) + "_" + to_string(blockSize) + ".txt";
				myfile.open(filename);
				OnMultBlock(lin, col, blockSize, myfile);  
				break;
			
		}

		

  		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		myfile << "L1 DCM:" <<  values[L1_DCM] << endl;
  		myfile << "L2 DCM: " << values[L2_DCM] << endl;

		myfile << "Data prefetch misses: " << values[PRF_DM] << endl;
                          
		myfile << "\n";

		double sum = values[LD_INS] + values[SR_INS];

		myfile << "L1 data cache hit rate: " << 1.0 - (values[L1_DCM] / sum) << endl;
		myfile << "L2 data cache hit rate " << 1.0 - (values[L2_DCM] / sum) << endl;
		myfile.close();
		
		
		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )      
			std::cout << "FAIL reset" << endl; 

		

	}while (op != 0);

	ret = PAPI_remove_event( EventSet, PAPI_L1_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_L2_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_destroy_eventset( &EventSet );
	if ( ret != PAPI_OK )
		std::cout << "FAIL destroy" << endl; 

}