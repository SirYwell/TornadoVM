package tornado.collections.types;

import java.nio.ShortBuffer;


public class VolumeShort2  implements PrimitiveStorage<ShortBuffer> {
    
	/**
	 * backing array
	 */
	final protected short[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private int			numElements;
	final private static int elementSize = 2;
	
	
    /**
     * Size in Y dimension
     */
	final protected int Y;
    
    /**
     * Size in X dimension
     */
	final protected int X;
	
	/**
	 * Size in Y dimension
	 */
	final protected int Z;
    
    public VolumeShort2(int width, int height,int depth, short[] array){
    	storage = array;
    	X = width;
    	Y = height;
    	Z = depth;
    	numElements= X*Y*Z*elementSize;
    }
    
    /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     */
    public VolumeShort2(int width,int height,int depth){
    	this(width,height,depth,new short[width*height*depth*elementSize]);
    }
    
    
    private final int toIndex(int x, int y, int z){
    	return  (z * X * Y * elementSize ) + (y * elementSize * X) + (x * elementSize);
    }
    
//    public Short2 get(int i){
//    	return new Short2(toIndex(i,0),storage);
//    }
    
//    public void set(int i, Short2 value){
//    	get(i).set(value);
//    }
    
    public Short2 get(int x, int y, int z){
    	final int index = toIndex(x,y,z);
    	return Short2.loadFromArray(storage, index);
    }
    
    public void set(int x, int y, int z, Short2 value){
    	final int index = toIndex(x,y,z);
    	value.storeToArray(storage, index);
    }
    
    public int Y(){
    	return Y;
    }
    
    public int X(){
    	return X;
    }
    
    public int Z(){
    	return Z;
    }
    
//    public VectorShort2 row(int row){
//    	int index = toIndex(row,0);
//    	VectorShort2 v = new VectorShort2(X,index,1,getElementSize(),storage);
//    	return v;
//    }
//    
//    public VectorShort2 column(int col){
//    	int index = toIndex(0, col);
//    	VectorShort2 v = new VectorShort2(Y,index,getStep(),getElementSize(),storage );
//    	return v;
//    }
    
//    public VectorShort2 diag(){
//    	VectorShort2 v = new VectorShort2(Math.min(Y,X), getOffset(), getStep() + 1,getElementSize(),storage);
//    	return v;
//    }
    
//    public VolumeShort2 subMatrix(int i, int j, int m, int n){
//    	int index = get(i,j).getOffset();
//    	VolumeShort2 subM = new VolumeShort2(m,n,index,getStep(),getElementSize(),storage);
//    	return subM;
//    }
    
    public void fill(short value){
    	for(int i=0;i<storage.length;i++)
    			storage[i] = value;
    }
    
    public VolumeShort2 duplicate(){
    	final VolumeShort2 volume = new VolumeShort2(X,Y,Z);
    	volume.set(this);
    	return volume;
    }
    
    public void set(VolumeShort2 other) {
    	for(int i=0;i<storage.length;i++)
			storage[i] = other.storage[i];
	}

    
    public String toString(String fmt){
    	 String str = "";

    	 for(int z=0;z<Z();z++){
    		 str += String.format("z = %d\n", z);
    		 for(int y=0;y<Y();y++){
    			 for(int x=0;x<X();x++){
    				 final Short2 point = get(x,y,z);
    				 str += String.format(fmt, point.getX(),point.getY()) + " ";
    			 }
    		 str += "\n";
    		 }
    	 }

         return str;
    }
    
    public String toString(){
    	String result = String.format("VolumeShort2 <%d x %d x %d>",Y,X,Z);
		return result;
	 }

    @Override
	public void loadFromBuffer(ShortBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public ShortBuffer asBuffer() {
		return ShortBuffer.wrap(storage);
	}

	@Override
	public int size() {
		return numElements;
	}

}