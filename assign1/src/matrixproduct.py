import time

def main():
    
    op = 0
    while (op != 1 and op != 2):
    
        print("1. Multiplication")
        print("2. Line Multiplication")
        print("Selection?: ")
        op = int(input())
        if op == 0:
            return
        print("Dimensions: lins=cols ? ")
        lin = int(input())
        col = lin
        filename = "python_selection" + str(op) + "_"  + str(lin) + ".txt"
        f = open(filename, "w")
        if op == 1:
            onMult(col, lin, f)
            
        elif op == 2:
            OnMultLine(col, lin,f)
            

        
        

def onMult(M1Size, M2Size, f):
    M1 = [None] * (M1Size * M1Size)
    for i in range (0, M1Size):
        for j in range(0, M1Size):
            M1[i*M1Size + j] = 1.0

    M2 = [None] * (M2Size * M2Size)
    for i in range (0, M2Size):
        for j in range(0, M2Size):
            M2[i*M2Size + j] = i + 1.0

    
    M3 = [None] * (M1Size * M1Size)

    t0 = time.time()
    for i in range(0, M1Size):
        for j in range(0, M2Size):
            temp = 0
            for k in range(0, M1Size):
                temp += M1[i*M1Size + k] * M2[k*M2Size + j]
            M3[i * M1Size + j] = temp

    f.write("Time:"+ str( time.time() - t0)+  " seconds\n")
    f.write("MFlops:"+ str( (pow(10,-6)) * 2 * M1Size**3 /(time.time() - t0)))
    for i in range(0,10):
        print(M3[j])	



def OnMultLine(Size1, Size2,f):
    M1 = [None] * (Size1 * Size1)
    M2 = [None] * (Size2 * Size2)
    M3 = [None] * (Size2 * Size2)
    for i in range(0,Size1):
        for j in range(0,Size1):
            M1[i * Size1 + j] = 1.0
        
    for i in range(0,Size2):
        for j in range(0,Size2):
            M2[i *Size2+j] = i + 1.0

    for i in range(0,Size2):
        for j in range(0,Size2):
            M3[i *Size2+j] = 0.0

    t0 = time.time()

    for i in range(0,Size1):
	    for j in range(0,Size2):
		    for k in range(0,Size2):
			    M3[i * Size1 + k]+= M1[i*Size1 + j] *M2[j * Size1 + k]
	
    f.write("Time:"+ str(time.time() - t0) + " seconds\n")
    f.write("MFlops:"+ str( (pow(10,-6)) * 2 * Size1**3 /(time.time() - t0)))
    # display 10 elements of the result matrix tto verify correctness
    for i in range(0,10):
        print(M3[j])	
    
main()