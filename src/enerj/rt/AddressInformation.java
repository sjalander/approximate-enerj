package enerj.rt;

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

    /**
     * May be an array- or some class object.
     */
    private Object obj = null;

    /**
     * If obj is a class object, field name is != null.
     */
    private String fieldname = null;

    /**
     * If obj is an array, index != null.
     */
    private Integer index = null;

    // Unusable: has to know application start time to set time properly
    /*
    AddressInformation(long t, boolean approx, boolean heap, int preciseSize,
               int approxSize, long address) {
        this(t, approx, heap, preciseSize, approxSize, address, -1);
    }
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

    /**
     * Save the array object + the index to the array value.
     * @param obj The array object
     * @param index Index in the array
     */
    public void setType(Object obj, int index) {
        this.obj = obj;
        this.index = index;
    }

    /**
     * Save the class object + the name of the field.
     * @param obj The array object
     * @param fieldname Name of the field in the class
     */
    public void setType(Object obj, String fieldname) {
        this.obj = obj;
        this.fieldname = fieldname;
    }

    /**
     * Get object together with field name OR index, depending on the type.
     */
    public Object[] getObjectAndSpecification() {
        return fieldname == null
            ? new Object[]{obj, index}
            : new Object[]{obj, fieldname};
    }

    /**
     * Get the address
     * @return The address
     */
	public final long getAddress() {
		return address;
	}
}
