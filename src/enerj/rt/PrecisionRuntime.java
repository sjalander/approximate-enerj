package enerj.rt;

import checkers.runtime.rt.Runtime;
import java.lang.ref.PhantomReference;


/**
 * Interface to implement a memory-ALU-system simulator. 
 */
public interface PrecisionRuntime extends Runtime {
	/**
     * This method is called immediately before an object creation.
     * The runtime keeps a stack of (creator, approx) pairs, per thread ID.
     *
     * @param creator The object that is instantiating the new object.
     * @param approx True, iff the new object should be approximate.
     * @param preciseSize The precise memory (in bytes) used by the object.
     * @param approxSize The approximate memory used by the object.
     * @param approximativeBits Number of bits being approximative; 0 is all
     * approx or precise.
     */
    boolean beforeCreation(Object creator, boolean approx,
                           int preciseSize, int approxSize, int approximativeBits);

	/**
	 * Set whether the referenced object is approximate.
	 *
	 * @param o Object that should be cataloged.
	 * @param approx Whether the object is approximate.
	 * @param heap Whether the object is on the heap (as opposed to the stack).
  	 * @param preciseSize The precise memory (in bytes) used by the object.
  	 * @param approxSize The approximate memory used by the object.
	 * @param approximativeBits Number of bits to be approximate; 0 if all or none (precise).
	 */
	PhantomReference<Object> setApproximate(
	    Object o, boolean approx, boolean heap,
	    int preciseSize, int approxSize, int approximativeBits
	);

	/**
	 * Query whether the referenced object is approximate.
	 *
	 * @param o The object to test.
	 * @return True, iff the referenced object is approximate.
	 */
	boolean isApproximate(Object o);

	// Simulated operations.
	public enum NumberKind { INT, BYTE, DOUBLE, FLOAT, LONG, SHORT }

	public enum ArithOperator { PLUS, MINUS, MULTIPLY, DIVIDE, BITXOR }

	public Number binaryOp(Number lhs, Number rhs, ArithOperator op, NumberKind nk, boolean approx, int approximativeBits);

	public <T> T countLogicalOp(T value);

	// Instrumented memory accesses.
	public enum MemKind { VARIABLE, FIELD, ARRAYEL }

        /**
	 * Wrap an array initialization.
	 *
	 * @param <T> The array type (not element type, which may be primitive).
	 * @param created The array.
	 * @param dims The number of dimensions in the new array.
	 * @param approx Whether the component type is, in fact, approximate.
	 * @param preciseElSize The precise size of the component type.
	 * @param approxElSize The approximate size of the component type.
	 * @param approximativeBits Number of bits being approximative; 0 is all
	 * approx or precise.
	 */
        <T> T newArray(T created, int dims, boolean approx,
		       int preciseElSize, int approxElSize, int approximativeBits);

	public <T> T storeValue(T value, boolean approx, MemKind kind);
	public <T> T loadValue(T value, boolean approx, MemKind kind);
	public <T> T loadLocal(Reference<T> ref, boolean approx);
	public <T> T loadArray(Object array, int index, boolean approx);
	public <T> T loadField(Object obj, String fieldname, boolean approx);
	public <T> T storeLocal(Reference<T> ref, boolean approx, T rhs);
	public <T> T storeArray(Object array, int index, boolean approx, T rhs);
	public <T> T storeField(Object obj, String fieldname, boolean approx, T rhs);

	// Fancier assignments.
	public <T extends Number> T assignopLocal(Reference<T> var, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx, int approximativeBits);
	public <T extends Number> T assignopArray(Object array, int index, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx, int approximativeBits);
	public <T extends Number> T assignopField(Object obj, String fieldname, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx, int approximativeBits);
}
