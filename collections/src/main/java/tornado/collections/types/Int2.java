package tornado.collections.types;

import java.nio.IntBuffer;

import tornado.api.Payload;
import tornado.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 2x ints
 * e.g. <int,int>
 * @author jamesclarkson
 *
 */
@Vector
public class Int2 implements PrimitiveStorage<IntBuffer> {
	public static final Class<Int2>	TYPE		= Int2.class;
	
	private static final String numberFormat = "{ x=%-7d, y=%-7d }";
	
	/**
	 * backing array
	 */
	@Payload final protected int[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private static int			numElements	= 2;
    
	public Int2(int[] storage) {
		this.storage = storage;
	}

	public Int2() {
		this(new int[numElements]);
	}

	public Int2(int x, int y) {
		this();
		setX(x);
		setY(y);
	}

	public int get(int index) {
		return storage[index];
	}

	public void set(int index, int value) {
		storage[index] = value;
	}
	
	public void set(Int2 value){
		setX(value.getX());
		setY(value.getY());
	}
    
    public int getX(){
    	return get(0);
    }
    
    public int getY(){
    	return get(1);
    }
    
    public int getS0(){
    	return get(0);
    }
    
    public int getS1(){
    	return get(1);
    }
    
    public void setX(int value){
    	set(0,value);
    }
    
    public void setY(int value){
    	set(1,value);
    }
    
	/**
	 * Duplicates this vector
	 * @return
	 */
	public Int2 duplicate(){
		Int2 vector = new Int2();
		vector.set(this);
		return vector;
	}
       
    public String toString(String fmt){
        return String.format(fmt, getX(),getY());
   }
   
   public String toString(){
      return toString(numberFormat);
   }
   
   protected static final Int2 loadFromArray(final int[] array, int index){
		final Int2 result = new Int2();
		result.setX(array[index]);
		result.setY(array[index + 1]);
		return result;
	}
	
	protected final void storeToArray(final int[] array, int index){
		array[index] = getX();
		array[index+1] = getY();
	}

	@Override
	public void loadFromBuffer(IntBuffer buffer) {
		asBuffer().put(buffer);
	}

	@Override
	public IntBuffer asBuffer() {
		return IntBuffer.wrap(storage);
	}

	public int size() {
		return numElements;
	}
	
	/***
	 * Operations on Int2 vectors
	 */
	
	
	/*
	 * vector = op( vector, vector )
	 */
	public static Int2 add(Int2 a, Int2 b){
		return new Int2(a.getX() + b.getX(),a.getY() + b.getY());
	}
	
	public static Int2 sub(Int2 a, Int2 b){
		return new Int2(a.getX() - b.getX(),a.getY() - b.getY());
	}
	
	public static Int2 div(Int2 a, Int2 b){
		return new Int2(a.getX() / b.getX(),a.getY() / b.getY());
	}
	
	public static Int2 mult(Int2 a, Int2 b){
		return new Int2(a.getX() * b.getX(),a.getY() * b.getY());
	}
	
	public static Int2 min(Int2 a, Int2 b){
		return new Int2(Math.min(a.getX() , b.getX()),Math.min(a.getY() , b.getY()));
	}
	
	public static Int2 max(Int2 a, Int2 b){
		return new Int2(Math.max(a.getX() , b.getX()),Math.max(a.getY() , b.getY()));
	}
	
	
	/*
	 * vector = op (vector, scalar)
	 */
	
	public static Int2 add(Int2 a, int b){
		return new Int2(a.getX() + b,a.getY() + b);
	}
	
	public static Int2 sub(Int2 a, int b){
		return new Int2(a.getX() - b,a.getY() - b);
	}
	
	public static Int2 mult(Int2 a, int b){
		return new Int2(a.getX() * b,a.getY() * b);
	}
	
	public static Int2 div(Int2 a, int b){
		return new Int2(a.getX() / b,a.getY() / b);
	}
	
	/*
	 * vector = op (vector, vector)
	 */
	
	public static void add(Int2 a, Int2 b, Int2 c){
		c.setX(a.getX() + b.getX());
		c.setY(a.getY() + b.getY());
	}
	
	public static void sub(Int2 a, Int2 b, Int2 c){
		c.setX(a.getX() - b.getX());
		c.setY(a.getY() - b.getY());
	}
	
	public static void mult(Int2 a, Int2 b, Int2 c){
		c.setX(a.getX() * b.getX());
		c.setY(a.getY() * b.getY());
	}
	
	public static void div(Int2 a, Int2 b, Int2 c){
		c.setX(a.getX() / b.getX());
		c.setY(a.getY() / b.getY());
	}
	
	public static void min(Int2 a, Int2 b, Int2 c){
		c.setX(Math.min(a.getX() , b.getX()));
		c.setY(Math.min(a.getY() , b.getY()));
	}
	
	public static void max(Int2 a, Int2 b, Int2 c){
		c.setX(Math.max(a.getX() , b.getX()));
		c.setY(Math.max(a.getY() , b.getY()));
	}
	
	/*
	 *  inplace src = op (src, scalar)
	 */
	
	public static void inc(Int2 a, int value){
		a.setX(a.getX() + value);
		a.setY(a.getY() + value);
	}
	
	
	public static void dec(Int2 a, int value){
		a.setX(a.getX() - value);
		a.setY(a.getY() - value);
	}
	
	public static void scaleByInverse(Int2 a, int value){
		a.setX(a.getX() / value);
		a.setY(a.getY() / value);
	}
	
	
	public static void scale(Int2 a, int value){
		a.setX(a.getX() * value);
		a.setY(a.getY() * value);
	}
	
	/*
	 * misc inplace vector ops
	 */

	public static void clamp(Int2 x, int min, int max){
		x.setX(TornadoMath.clamp(x.getX(), min, max));
		x.setY(TornadoMath.clamp(x.getY(), min, max));
	}
	
	/*
	 * vector wide operations
	 */
	

	public static int min(Int2 value){
		return Math.min(value.getX(), value.getY());
	}
	
	public static int max(Int2 value){
		return Math.max(value.getX(), value.getY());
	}
	
	public static int dot(Int2 a, Int2 b){
		final Int2 m = mult(a,b);
		return m.getX() + m.getY();
	}
	
	public static boolean isEqual(Int2 a, Int2 b){
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}
}