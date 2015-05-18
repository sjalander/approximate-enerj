package enerj.rt;

/**
 * Information applied to any object upon creation.
 */
class CreationInfo {
    /**
     * The object that in turn created *this* object. 
     */
    public Object creator;
    
    /**
     * Whether the created object is approximate or not.
     */
    public boolean approx;
    
    /**
     * Size of the precise object (equal to 0 if approximative)
     */
    public int preciseSize;
    
    /**
     * Size of the approximative object (equal to 0 if precise)
     */
    public int approxSize;

    /**
     * Simple constructor to store given argument values.
     * @param creator Creator of the object
     * @param approx Whether or not the created object is approximative
     * @param preciseSize Precise data size
     * @param approxSize Approximative data size
     */
    CreationInfo(Object creator, boolean approx, int preciseSize,
                 int approxSize) {
        this.creator = creator;
        this.approx = approx;
        this.preciseSize = preciseSize;
        this.approxSize = approxSize;
    }
}
