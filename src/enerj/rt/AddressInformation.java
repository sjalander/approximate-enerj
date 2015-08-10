package enerj.rt;

import java.lang.ref.WeakReference;

/**
 * Information related to specific data blocks and their time of operation
 * TODO #general: merge Approx- with AddressInformation to get rid of 'approx'
 * field in favor of putting approximation bit in address instead.
 */
class AddressInformation extends ApproximationInformation {
    
    /**
     * Address in simulated address space.
     */
    private final long address;
    
    /**
     * Last time seen in SRAM.
     */
    private long timeStamp;

    // If weird NullPointerException exceptions turn up, switch to Object.
    /**
     * May be an array- or some class object.
     */
    private WeakReference<Object> obj = null;
    //private Object obj = null;

    /**
     * If obj is a class object, field name is != null.
     */
    private String fieldname = null;

    /**
     * If obj is an array, index != null.
     */
    private Integer index = null;

    /************************ States for PCM modelling ************************/
    /**
     * Bit string for tracking which bits that have been flipped before; used
     * for PCM modelling.
     */
    private int flipped = 0;

    /**
     * Whether or not this memory block has experienced bit flips before; used
     * for PCM modelling.
     * @param pos Position in bit string to check
     */
    public boolean isFlipped(int pos) { 
        return ((flipped >> pos) & 1) != 0;
    }

    /**
     * Set new flip status; used for PCM modelling.
     * @param flipped New flip status; true for previous bit flip, false for
     * none.
     * @param pos Position in bit string to set
     */
    public void setFlipped(boolean flipped, int pos) { 
        if (flipped)
            this.flipped |= 1 << pos;
        else
            this.flipped &= ~(1 << pos);
    }

    /**
     * If no bit flips has occurred before, set bit flip status to true; used
     * for PCM modelling.
     */
    public void setFlipped(int pos) {
        if (!isFlipped(pos))
            setFlipped(true, pos);
    }

    /**
     * Clear the flip bit tracker; this is done on cache line evictions/loads,
     * as this infers that data is written to another set of cells.
     */
    public void clearFlipped() {
        flipped = 0;
    }

    /**
     * See flip tracker bit string; for debugging purposes.
     */
    public int getFlippedBitString() {
        return flipped;
    }
    /**************************************************************************/

    /**
     * Sets all given states.
     */
    AddressInformation(long t, boolean approx, boolean heap, int preciseSize,
               int approxSize, long address, long timeStamp) {
        super(t, approx, heap, preciseSize, approxSize);
        this.timeStamp = timeStamp; // Manually entered SRAM time
        this.address = address; // The address
    }
    
    /**
     * Update time of usage to current time.
     */
    public void setTimeStamp() {
         this.timeStamp = System.currentTimeMillis();
    }
    
    /**
     * Update time of usage to given time.
     * @param newTimeStamp New custom time stamp
     */
    public void setTimeStamp(long timeStamp) {
         this.timeStamp = timeStamp;
    }
    
    /**
     * Read time stamp.
     */
    public long getTimeStamp() {
        return this.timeStamp;
    }

    // If weird NullPointerException exceptions turn up, switch to Object.
    /**
     * Save the array object + the index to the array value.
     * @param obj The array object
     * @param index Index in the array
     */
    public void setType(Object obj, int index) {
        //this.obj = obj;
        this.obj = new WeakReference(obj);
        this.index = index;
    }

    // If weird NullPointerException exceptions turn up, switch to Object.
    /**
     * Save the class object + the name of the field.
     * @param obj The array object
     * @param fieldname Name of the field in the class
     */
    public void setType(Object obj, String fieldname) {
        //this.obj = obj;
        this.obj = new WeakReference(obj);
        this.fieldname = fieldname;
    }

    // If weird NullPointerException exceptions turn up, switch to Object.
    /**
     * Get object together with field name OR index, depending on the type.
     */
    public Object[] getObjectAndSpecification() {
        return fieldname == null
            //? new Object[]{obj, index}
            //: new Object[]{obj, fieldname};
            ? new Object[]{obj.get(), index}
            : new Object[]{obj.get(), fieldname};
    }

    /**
     * Get the address
     * @return The address
     */
	public final long getAddress() {
		return address;
	}
}
