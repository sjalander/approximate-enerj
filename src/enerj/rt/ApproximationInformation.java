package enerj.rt;

/**
 * Information related to (eventual approximation of data)
 */
class ApproximationInformation {
    /**
     *  Time stamp of creation
     */
    public long created;

    /**
     *  Time of collection by GC
     */
    public long collected;
    
    /**
     *  True if approximate value
     */
    public boolean approx;
    
    /**
     * True if heap value (false if stack value)
     */
    public boolean heap;
    
    /**
     * Total size of the precise object(s) (equal to 0 if approximative)
     */
    public int preciseSize;
    
    /**
     * Total size of the approximative object(s) (equal to 0 if precise)
     */
    public int approxSize;

    /**
     *  If partial approximation is used, this is the number of total bits being
     *  approximative. E.g. @Approx8.
     *  If partialApprox == 0, then the whole (primitive) type is approximative.
     */
    public int approximativeBits = -1;

    /**
     * To save a bit of space, we only store the creation time
     * @param t Time of creation
     * @param approx Whether the created object is approximative or not
     * @param heap Whether the created object resides on the heap or not (i.e. on the stack)
     * @param preciseSize Size of the precise object (equal to 0 if approximative)
     * @param approxSize Size of the approximative object (equal to 0 if precise)
     */
    ApproximationInformation(long t, boolean approx, boolean heap,
                             int preciseSize, int approxSize,
                             int approximativeBits) {
        created = t;
        this.approx = approx;
        this.heap = heap;
        this.preciseSize = preciseSize;
        this.approxSize = approxSize;
        this.approximativeBits = approximativeBits;
    }

    public int getSize() {
        return this.approx ? approxSize : preciseSize;
    }
}
