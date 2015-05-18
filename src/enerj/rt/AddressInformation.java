package enerj.rt;

/**
 *Information related to specific data blocks and their time of operation
 */
class AddressInformation extends ApproximationInformation {
    
    /**
     * Address in simulated address space
     */
    public long address;
    
    /**
     * Last time seen in SRAM
     */
    private long timeStamp;

    AddressInformation(long t, boolean approx, boolean heap, int preciseSize,
               int approxSize, long address) {
        this(t, approx, heap, preciseSize, approxSize, address, -1);
    }
    
    AddressInformation(long t, boolean approx, boolean heap, int preciseSize,
               int approxSize, long address, long timeStamp) {
        super(t, approx, heap, preciseSize, approxSize);
        this.timeStamp = timeStamp; // Manually entered SRAM time
        this.address = address; // The address
    }
    
    /**
     * Update time of usage
     */
    public void updateTimeStamp() {
         this.timeStamp = System.currentTimeMillis();
    }
    
    /**
     * Update time of usage
     * @param newTimeStamp New custom time stamp
     */
    public void updateTimeStamp(long timeStamp) {
         this.timeStamp = timeStamp;
    }
    
    /**
     * Read time stamp
     */
    public long readTimeStamp() {
        return this.timeStamp;
    }
}
