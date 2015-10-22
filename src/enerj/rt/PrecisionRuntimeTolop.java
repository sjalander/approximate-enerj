
/**
 * Different TODO tags:
 * --
 * #cachelineerrors - related to let a whole cache line be injected with errors
 * upon load
 * 
 * #blockerrors - related to errors that are introduced to a single block upon
 * load
 *
 * #general - general (non-specific) changes/fixes/proposals
 */

package enerj.rt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

import plume.WeakIdentityHashMap;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import enerj.AnnotationType;
import enerj.FieldInfoContainer;
import enerj.MyTuple;

class PrecisionRuntimeTolop implements PrecisionRuntime {

    /********TOLOP INNER CLASSES, VARIABLES AND METHODS********/
    
    /**
     * Tuple for holding when some specific cache line were held in SRAM/DRAM. 
     * @author Gustaf Borgstrom
     */
    private class TimeTuple {
    	long sramTime;
        long dramTime;
        long lruTime;
        boolean approx;
        long lineAddress;
        
        TimeTuple(boolean approx, long lineAddress) {
            sramTime = startup-1;  // Guarantees the lowest (oldest) time stamp
            dramTime = startup-1;  // Guarantees the lowest (oldest) time stamp
            lruTime = startup-1;   // Guarantees the lowest (oldest) time stamp
            this.approx = approx;
            this.lineAddress = lineAddress; // Address of this CL 
        }
        
        long getLruTime() {
            return this.lruTime;
        }
        
        void setLruTime(long lruTime) {
            this.lruTime = lruTime;
        }
        
        long getDramTime() {
            return this.dramTime;
        }
        
        void setDramTime(long dramTime) {
            this.dramTime = dramTime;
        }
        
        long getSramTime() {
            return this.sramTime;
        }
        
        void setSramTime(long sramTime) {
            this.sramTime = sramTime;
        }
        
        boolean isApprox() {
            return approx;
        }
        
        void setApprox(boolean approx) {
            this.approx = approx;
        }
        
        long getLineAddress() {
        	return lineAddress;
        }
        
        void setLineAddress(long lineAddress) {
        	this.lineAddress = lineAddress;
        }
    }

    // File names for data input/output
    protected static final String JSON_INPUT_FILE_NAME = "object_field_info.json";
    protected static final String JSON_OUTPUT_FILE_NAME = "enerjstats.json";

    /**
     *  If true, values may be approximate; else, all values are precise.
     *  This is used to show the difference between a regular- and an
     *  approximative computer architecture.
     */
    private boolean ALLOW_APPROXIMATE = true;

    /**
     * If true, the simulation is specifically a PCM simulation run.
     */
    private boolean PCM_SIMULATION = false;

    /**
     * If true, introduce errors on the whole cache line on evitions/loads; else
     * errors are only introduced on the targeted memory block.
     */
    private boolean ERRORS_ON_CL = true;
    
    /**
     * Size of address on 64 bit machine.
     */
    private static final int POINTER_SIZE = 8; // Byte size
    private static final int POINTER_QYTE_SIZE = 16; // Qyte size

    /**
     *  Address specific variables.
     */
    private long addressGeneratorPrecise = 0; // Address counter of precise memory
    private long addressGeneratorApprox = 0; // Address counter of approximative memory
    private int cacheSize; // Total size of the cache
    private int cacheLineSizeInWords; // Size of a cache line in words
    private int cacheLineSizeInQytes; // Size of a cache line in bytes
    private int nIndexes; // Number of cache indexes

    private Map<String, TimeTuple> memoryTimeStamps
        = new HashMap<String, TimeTuple>(); // Contains last access time of any cache line
    
    /**
     * Maps an address tag to SRAM/DRAM time data.
     */
    private List<HashMap<Long, TimeTuple>> sramContainer;// Which line is in the cache now?
    
    /**
     *  Define fundamental size related to 64 bit addresses
     */
    private static final int byteSizeBits = 8; // 8 bits in one byte
    private static final int wordSize = 4; // 4 bytes in a word (in this simulator, might differ)
    private static final int wordSizeBits = wordSize*byteSizeBits; // A word is defined as 32 bits (here)
    private static final int addressSizeBits = 64;
    private static final int offsetBits = 2; // 2^2 = 4 bytes per word
    private static final long approxMask = (long)1 << 63; // Note: sets the sign bit 
    private int nApproxWordsPerLineBits; // Words per cache line
    private int nCacheLinesBits; // Number of lines in the cache, binary representation
    private int sramAssociativity; // Associativity per cache index
    private long tagMask; // Mask for getting tags from addresses
    private boolean padCacheLines = false; // Whether cache lines should be padded to the end after allocation, or not
    private boolean differentDRAMSpaces = true; // Whether approx/precise lives in different DRAM
    
    /* Indexes represents 2s, 4s, 8s, ... */
    //double[] S2ErrorRateLookup = {24.0, 24.0, 7.2, 5.1, 4.15, 3.7, 3.4, 3.1,
    //    3.0, 2.9, 2.8, 2.7, 2.6, 2.5, 2.4, 2.3, 2.2, 2.1};
    double[] S2ErrorRateLookup = {1e-24, 1.59e-12, 5.85e-6, 7.45e-4, .01, .02,
        .05, .08, .12, .17, .22, .28, .35, .43, .52, .62, .73};

    /* Indexes represents 2s, 4s, 8s, ... */
    //double[] S3ErrorRateLookup = {7.5, 3.6, 2.9, 2.5, 2.25, 2.0, 1.9, 1.8, 1.7,
    //    1.5, 1.2, 1.1, 1.0, 0.9, 0.8, 0.75, 0.7, 0.65};
    double[] S3ErrorRateLookup = {5.85e-6, .02, .12, .28, .52, .85, 1.30, 1.90,
        2.67, 3.64, 4.84, 6.29, 7.99, 9.95, 12.16, 14.61, 17.27};

    /**
     * Maps specific (unique) key representation of some memory block -> its
     * address information.
     */
    private Map<String, AddressInformation> memorySpace =
            new HashMap<String, AddressInformation>(); 
    
    /**
     * Info about accesses in memory hierarchy.
     */
    public MemoryOpInfo memOpInfo = new MemoryOpInfo();


    /**
     * Map to store data extracted from earlier compilation (and now loaded from)
     * a resulting JSON file). Used for setting information about approx/precise
     * fields, etc.
     * Mapping: Class name -> Map, that maps Class field name -> Additional field
     * info, like approximation annotation, etc.
     */
    private Map<String, HashMap<String, FieldInfoContainer>> classInfo =
        new HashMap<String, HashMap<String, FieldInfoContainer>>();
    
    /**
     * Map to representations of cache lines. Used to eventually introduce
     * errors to a full cache line when loaded.
     */
    private Map<Long, ArrayList<String>> cachelineTracker;

    /**
     * Create start address for the wanted amount of memory space.
     * @param nMemory Wanted amount of memory
     * @param approx Whether memory is approximate or not. If true; use
     * approximate memory space; otherwise, use precise.  
     * @return Start address for the wanted amount of memory
     */
    private long createAddress(long nMemory, boolean approx) {
        long address;
        if (approx && differentDRAMSpaces) {
        	// TODO #general Use address mask instead of explicit 'approx' field?  
            address = addressGeneratorApprox;
            addressGeneratorApprox += nMemory;
        }
        else {
        	// If the same DRAM space is used for precise/approx memory,
        	// it doesn't matter technically what generator is used - using
        	// "...Precise" is sufficient.
            address = addressGeneratorPrecise;
            addressGeneratorPrecise += nMemory;
        }
        return address;
    }
    
    /**
     * Gets the cache line address.
     *
     * @param address Some address
     * @return The cache line address
     */
    private long getCacheLineAddress(long address) {
    	long cachelineAddress;
    	boolean approx = (address & approxMask) != 0;
    	if (approx) { // If set: avoid tag mask mess-up
    		cachelineAddress = address & ~approxMask;
    		cachelineAddress = address - (address % cacheLineSizeInQytes);
    		cachelineAddress |= approxMask;
    	}
    	else {
    		cachelineAddress = address - (address % cacheLineSizeInQytes);
    	}
    	return cachelineAddress;
    }

    /**
     * Get the cache line associated with some address.
     *
     * @param address Any address that belongs to the wanted cache line
     * @return the Cache line list, containing all addresses in this cache line
     */
    private ArrayList<String> getFromCacheLineTracker(long address) {
    	long cachelineAddress = getCacheLineAddress(address);
        return cachelineTracker.containsKey(cachelineAddress)
        		? cachelineTracker.get(cachelineAddress)
        		: null;
    }

    /**
     * Add a memory key to a cache line.
     * @param address Any address associated with the cache line
     * @param key The memory key to be added
     * @return true if a new cache line was created, otherwise false
     */
    private boolean addToCachelineTracker(long address, String key) {
        ArrayList<String> cacheline = getFromCacheLineTracker(address);
        boolean newCachelineCreated = false;
        if (cacheline == null) { // Create new cache line
            cacheline = new ArrayList<String>();
            cachelineTracker.put(Long.valueOf(getCacheLineAddress(address)), cacheline);
            newCachelineCreated = true;
        }

        if (cacheline.size() > cacheLineSizeInQytes) { 
            System.err.println(String.format("addToCachelineTracker: too many"
            		+ "addresses (%d) in cache line for address %d",
            		cacheline.size(), address));
            System.exit(1); // This will screw it up too much: exit program...
        }
        cacheline.add(key);

        return newCachelineCreated;
    }

    /**
     * Get current address counter value.
     * @param approx If true, return current approximate memory space counter;
     * otherwise, return precise memory space counter. 
     * @return Current value of some memory counter
     */
    public long peekAddress(boolean approx) {
        return (approx && differentDRAMSpaces)
            ? addressGeneratorApprox
            : addressGeneratorPrecise;
    }
    
    /**
     * Is assigned memory cache lines padded or not?
     * @return If memory cache lines are padded after allocation, return true;
     * else, return false.
     */
    public boolean isPadded() {
        return padCacheLines;
    }
    
    /**
     * Set padding. NOTE: This does NOT change previous memory padding, changes
     * will only manifest from calling this method and onwards. 
     * @param padding If true, memory cache lines will be padded from now on;
     * else, it will not be padded. 
     */
    public void setPadding(boolean padding) {
        padCacheLines = padding;
    }

    /**
     * Memory is stored on different DRAM chips depending on approximative or not?
     * @return If memory is stored on different DRAM if approximative, return true;
     * else, return false.
     */
    public boolean hasDifferentDRAM() {
        return differentDRAMSpaces;
    }
    
    /**
     * Set whether to use different DRAM spaces or not. NOTE: This does NOT
     * change previous memory allocations. Thus, already allocated memory will
     * still be marked as residing in the space that was true then. If used
     * incorrectly, memory cache accuracy may misbehave accordingly.  
     * @param differentDRAMSpaces If true, different DRAM spaces and memory
     * addresses will be used upon allocation, store and loads; else, one
     * memory space will be used.
     */
    public void setDifferentDRAMSpace(boolean differentDRAMSpaces) {
        this.differentDRAMSpaces = differentDRAMSpaces;
    }

    /**
     * Return object size in quad-cache.
     * @param value Name of the object
     * @param approx Whether the value is approximate or not. If true, return
     * approx (regular) size; else return precise (double) size. E.g., a regular
     * double is 16 qytes.
     * @return Object size in qytes
     */
    public static int numQytes(String value, boolean approx) {
        switch (value) {
        case "java.lang.Byte":
        case "Byte":
        case "byte":
            return (approx ? 1 : 2);
        case "java.lang.Short":
        case "Short":
        case "short":
            return (approx ? 2 : 4);
        case "java.lang.Integer":
        case "Integer":
        case "int":
            return (approx ? 4 : 8);
        case "java.lang.Long":
        case "long":
             return (approx ? 8 : 16);
        case "java.lang.Float":
        case "Float":
        case "float":
             return (approx ? 4 : 8);
        case "java.lang.Double":
        case "Double":
        case "double":
             return (approx ? 8 : 16);
        case "java.lang.Character":
        case "Character":
        case "char":
             return (approx ? 2 : 4);
        case "java.lang.Boolean":
        case "Boolean":
        case "boolean":
             return (approx ? 1 : 2);
        default:
            return POINTER_QYTE_SIZE;
        }
    }
    
    /**
     * Return object size in quad-cache.
     * @param value Some object representation of some primitive type  
     * @return Object size in qytes
     */
    private static int numQytes(Object value) {
    	if (value instanceof Byte) return 1;
        else if (value instanceof Short) return 2;
        else if (value instanceof Integer) return 4;
        else if (value instanceof Long) return 8;
        else if (value instanceof Float) return 4;
        else if (value instanceof Double) return 8;
        else if (value instanceof Character) return 2;
        else if (value instanceof Boolean) return 1;
        else assert false;
        return 0;
    }

    /**
     * Give values priority order when ordering object fields.
     * @param value Some value that should be prioritized
     * @return The priority order of some type, pointers are higest, thereafter:
     * the larger the type, the higher the priority. 
     */
    public static int prioritizeType(String value) {
        if      (value.equals("byte") || value.equals("java.lang.Byte")) return 1;
        else if (value.equals("short") || value.equals("java.lang.Short")) return 2;
        else if (value.equals("int") || value.equals("java.lang.Integer")) return 4;
        else if (value.equals("long") || value.equals("java.lang.Long"))  return 8;
        else if (value.equals("float") || value.equals("java.lang.Float"))  return 4;
        else if (value.equals("double") || value.equals("java.lang.Double"))  return 8;
        else if (value.equals("char") || value.equals("java.lang.Character"))  return 2;
        else if (value.equals("boolean") || value.equals("java.lang.Boolean"))  return 1;
        else { /* ...or instances of some general other object */
            return 10;
        }
    }

    /**
     * Solution for iterating over n-dimensional arrays.  
     * Credits: http://stackoverflow.com/a/15949458/1283083
     */
    private interface ElementProcessor {    
        void process(Object e, int index, boolean approx);  
    }
    
    /**
     * Recursively traverse through the array dimensions until the types are
     * "found". Apply a callback function on the each such type element.
     * @param o The array object
     * @param p Callback function to be applied on the final elements
     * @param approx Whether the array elements are approximate or not 
     */
    private static void addressesToArrayElemsAux(Object o, ElementProcessor p,
            boolean approx, boolean isValue) {
        for (int i = 0; i < Array.getLength(o); i++) {
            Object e = Array.get(o, i);
            if (e != null && e.getClass().isArray()) {
                addressesToArrayElemsAux(e, p, approx, isValue);
                if (!isValue)
                    p.process(o, i, approx);
                if (debug) {
                }
            }
            else if (isValue) { // End of array
                p.process(o, i, approx);
            }
        }
    }
    
    /**
     * Assign addresses to array values.
     * @param created The array to assign addresses to
     * @param approx Whether the array contains approximate values or not 
     */
    private <T> void assignAddressesToArrayItems(T created, boolean approx,
            boolean isValue) {
        // ...then, give all values addresses
        Object arr = created;
        ElementProcessor p = new ElementProcessor() {
            @Override
            public void process(Object arr, int index, boolean approx) {
                long tim = System.currentTimeMillis(); // Time stamp of creation
                
                // Compute item size(s)
                int typeSize, approxSize = 0, preciseSize = 0;
                Object obj = Array.get(arr, index);
                String typeName;
                if (obj == null) { // Array contents may be null values; if so, determine array type
                    typeName = arr.getClass().getCanonicalName().replace("[]", "");
                    typeSize = numQytes(typeName, approx);
                }
                else {
                    typeName = obj.getClass().getName();
                    typeSize = numQytes(obj.getClass().getName(), approx);
                }
                
                if (approx)
                    approxSize = typeSize;
                else
                    preciseSize = typeSize;
                
                String key = memoryKey(arr, index);
                long address = createAddress(typeSize, approx); // Address to array reference
                addToCachelineTracker(approx ? address | approxMask : address, key);
                
                AddressInformation ainfo =
                        new AddressInformation(tim, approx, true, preciseSize,
                                approxSize, address, startup-1); // -1: Trick to force oldest possible time stamp
                ainfo.setType(arr, index);
                memorySpace.put(key, ainfo);
                
                if (debug) {
                }
            }
        };
        addressesToArrayElemsAux(arr, p, approx, isValue);
    }

    /**
     * Return the time information associated with a specific cache line
     * address; if it doesn't exist, create a new one.
     * @param ainfo Info needed to determine approximation
     * @param addrNoWordOffset The cache line address
     * @param tim Current time stamp, used for eventually new CL:s
     * @return The times associated with the cache line address
     */
    private TimeTuple getCurrentCachelineTimeTuple(AddressInformation ainfo,
    		long addrNoWordOffset, long tim) {
        TimeTuple currentLine;
        // Create a unique identifier for every cache line
        final String memoryTimeStampsString = (ainfo.approx ? "A" : "P") + addrNoWordOffset;
        // Get cache line times (or create new tuple)
        if (!memoryTimeStamps.containsKey(memoryTimeStampsString)) {
            currentLine = new TimeTuple(ainfo.approx, addrNoWordOffset);
            currentLine.setSramTime(tim); // TODO #blockerrors: Move this?
            memoryTimeStamps.put(memoryTimeStampsString, currentLine);
        }
        else {
            currentLine = memoryTimeStamps.get(memoryTimeStampsString);
        }
        return currentLine;
    }

    /**
     * Introduce errors on LOADED cache lines. Memory block time stamps are
     * updated with currentTime after operation.
     *
     * @param <T> The (generic) return type
     * @param addrTag The address (tag)
     * @param lastTime Last time stamp to calculate from
     * @param currentTime Current time stamp to calculate to
     * @param tim invProb Inverse probability for a bit error to occur
     * @param isDynamic true if a time dependent error model is to be used;
     * false if a static error model is to be used
     */
    private <T> void introduceErrorsOnCacheLine(long addrTag, long lastTime,
                                                long currentTime, long invProb,
                                                boolean isDynamic) {
        //--Introduce errors into the read data if approximate, etc
        ArrayList<String> cacheline = getFromCacheLineTracker(addrTag);
        AddressInformation addressInformation;
        for (String key : cacheline) {
        	addressInformation = memorySpace.get(key);
            loadChangeStore(addressInformation, lastTime, currentTime,
            				invProb, isDynamic);

            // DEBUG
            //int flippedBitString = addressInformation.getFlippedBitString();
            //if (flippedBitString != 0)
            //    System.err.println("Loaded line: flipped string is NOT 0");

            addressInformation.clearFlipped();
        }
    }
    
    /**
     * Introduce errors on EVICTED cache lines. Memory block time stamps are
     * updated with currentTime after operation.
     *
     * @param <T> The (generic) return type
     * @param addrTag The address (tag)
     * @param currentTime Current time stamp to calculate to
     * @param tim invProb Inverse probability for a bit error to occur
     * @param isDynamic true if a time dependent error model is to be used;
     * false if a static error model is to be used
     */
    private <T> void introduceErrorsOnCacheLine(long addrTag, long currentTime,
    											long invProb, boolean isDynamic) {
        //--Introduce errors into the read data if approximate, etc
        ArrayList<String> cacheline = getFromCacheLineTracker(addrTag);
        AddressInformation addressInformation;
        for (String key : cacheline) {
        	addressInformation = memorySpace.get(key);
        	loadChangeStore(addressInformation, addressInformation.getTimeStamp(),
            				currentTime, invProb, isDynamic);
            
            // DEBUG
            //int flippedBitString = addressInformation.getFlippedBitString();
            //if (flippedBitString != 0)
            //    System.err.println("Evicted line: flipped string is NOT 0");

            addressInformation.clearFlipped();
        }
    }

	/**
	 * Load, then change (based on some probability), then store value.
	 * @param lastTime Last relative time stamp to calculate from
	 * @param currentTime Current time stamp
	 * @param invProb Inverse probability of error injection
	 * @param isDynamic If true, dynamic probability of error; else static
	 * @param key The memory key
	 * @param <T> Generic type of value
	 */
	@SuppressWarnings("unchecked")
	private <T> void loadChangeStore(AddressInformation addressInfo, long lastTime,
									 long currentTime, long invProb,
									 boolean isDynamic) {
		Object[] objAndSpec = addressInfo.getObjectAndSpecification();

		if (isPrimitive(objAndSpec[1])) { //--Data is from array index
			int index = ((Integer)objAndSpec[1]).intValue();
		    T value = (T)Array.get(objAndSpec[0], index);
		    if (isDynamic) {
                //--invProb is always the same for dynamic
                if (PCM_SIMULATION)
                    value = driftReadPCM((T)value, addressInfo, lastTime,
                            currentTime);
                else
                    value = driftRead(value, lastTime, currentTime); 
            }
		    else
		    	value = bitError(value, invProb);
            Array.set(objAndSpec[0], index, value);
            addressInfo.setTimeStamp(currentTime);
        }
        else { //--Data is from class field
            try {
                Field field = ((Object)objAndSpec[0]).getClass().
                        getDeclaredField((String)objAndSpec[1]);
                field.setAccessible(true);
                Object value = field.get((Object)objAndSpec[0]);
                if (isDynamic) {
                    //--invProb is always the same for dynamic
                    if (PCM_SIMULATION)
                        value = (Object)driftReadPCM((T)value, addressInfo,
                                lastTime, currentTime);
                    else
                        value = (Object)driftRead((T)value, lastTime,
                                currentTime); 
                }
    		    else
    		    	value = (Object)bitError((T)value, invProb);
                field.set((Object)objAndSpec[0], value);
		        addressInfo.setTimeStamp(currentTime);
			}
			catch (NoSuchFieldException e) {
				System.err.println(String.format("introduceErrorsOnCacheLine:"
						+ "No field: %s; could not introduce errors...",
							(String)objAndSpec[1]));
			}
			catch (IllegalAccessException e) {
				System.err.println("introduceErrorsOnCacheLine: "
						+ "Illegal field access; could not introduce errors...");
			}
		}
	}
	
	/**
	 * Choose error mode for cache lines.
	 *
	 * @param currentTimeTuple The current time tuple
	 * @param currentAddrTag Current address tag
	 * @param currentTimestamp Current time stamp
	 * @param currentAinfo The actual address information
	 * @param evictedTimeTuple Evicted time tuple
	 * @param evictedAddressTag Evicted address tag
	 * @param evictionOccurred Whether eviction occurred
	 */
	private void chooseErrorModeForCacheLines(TimeTuple currentTimeTuple,
			final long currentAddrTag, final long currentTimestamp,
			AddressInformation currentAinfo, TimeTuple evictedTimeTuple,
			final Long evictedAddressTag, Boolean evictionOccurred) {

        //--The loaded CL
        if (ALLOW_APPROXIMATE && currentAinfo.approx) {
	        if (DRAMmode == ErrorModes.DYNAMIC) {
	        	//--Inject with dynamic errors from DRAM
                //System.err.println("Loaded"); //DEBUG
                introduceErrorsOnCacheLine(currentAddrTag, currentTimeTuple.getDramTime(),
                		currentTimestamp, INVPROB_DRAM_FLIP_PER_SECOND, true); // TODO: add independent SRAM_FLIP_PER_SECOND variable
	        }
	        if (SRAMmode == ErrorModes.STATIC) {
	    		//--Also, inject with write errors in SRAM
                introduceErrorsOnCacheLine(currentAddrTag, 0, 0, // Any time will do
                		INVPROB_SRAM_WRITE_FAILURE, false);
	    	}
	        currentTimeTuple.setSramTime(currentTimestamp);
        }
        
        //--The evicted CL
        if (ALLOW_APPROXIMATE && (evictedAddressTag & approxMask) != 0 && evictionOccurred) {
            switch (SRAMmode) {
            case DYNAMIC:
                //--Inject with dynamic read errors at SRAM
            	//--Note: no lastTime sent, as time is computed from respective
            	//--time stamps
                //System.err.println("Evicted"); //DEBUG
                introduceErrorsOnCacheLine(evictedAddressTag,
                		currentTimestamp, INVPROB_DRAM_FLIP_PER_SECOND, true);
                break;
            case STATIC:
            	//--Inject with static read errors at SRAM
            	introduceErrorsOnCacheLine(evictedAddressTag, 0, 0,
            			INVPROB_SRAM_READ_UPSET, false);
            	break;
            default:
                //--NONE: skip error injection (shouldn't be reached)
                assert false;
                System.err.println("ERROR: Approximate value reached NONE - check this...");
                break;
            }
            evictedTimeTuple.setDramTime(currentTimestamp);
        }
	}


    /**
     * Update last SRAM time stamp on new cache line.
     *
     * @param <T> The (generic) return type
     * @param addrTag The address (tag)
     * @param currentTime Current time stamp to calculate to
     */
    /*
    private <T> void updateCacheLineTimeStamp(long addrTag, long currentTime) {
        //--Introduce errors into the read data if approximate, etc
        ArrayList<String> cacheline = getFromCacheLineTracker(addrTag);
        AddressInformation addressInfo;
        for (String key : cacheline) {
        	addressInfo = memorySpace.get(key);
            addressInfo.setTimeStamp(currentTime);
        }
    }
    */
    
    /**
     * Evict a specific cache line from SRAM -> DRAM and write a new cache line
     * in its place. If nothing needs to be evicted, nothing will.
     * Note: in a noisy environment, the read data will have introduced errors.
     * @param indexAssocLine Specific (associative) cache index content. 
     * @param currentTimeTuple Cache line of the current memory block
     * @param tim Current time stamp
     * @param currentAinfo Info about the current memory block
     * @return Address tag of the evicted cache line
     */
    private long evictCacheLine(HashMap<Long, TimeTuple> indexAssocLine,
            TimeTuple currentTimeTuple, long tim, AddressInformation currentAinfo) {
    	final long currentAddrTag = getAddrTag(currentAinfo);
    	long sramTime = 0;
        Long evictedAddressTag = (long)0;
        TimeTuple evictedTimeTuple = null;
        Boolean evictionOccurred = true;
        //--Early in program execution - nothing to evict yet
        if (indexAssocLine.size() < sramAssociativity) {
            indexAssocLine.put(currentAddrTag, currentTimeTuple);
            currentTimeTuple.setLruTime(tim);
            currentTimeTuple.setSramTime(tim);
            evictionOccurred = false;
        }
        else { //--Evict oldest and insert loaded CL
            //--Find oldest line currently in the index
        	long oldestTime = Long.MAX_VALUE;
            TimeTuple tmpTuple;
            for (Map.Entry<Long, TimeTuple> entry : indexAssocLine.entrySet()) {
                tmpTuple = entry.getValue();
                if (oldestTime > tmpTuple.getLruTime()) {
                    evictedTimeTuple = tmpTuple;
                    evictedAddressTag = entry.getKey();
                    oldestTime = tmpTuple.getLruTime();
                }
            }
            
            currentTimeTuple.setLruTime(tim);
            
            //--This data may be used to see drift errors and likewise
            sramTime = tim - evictedTimeTuple.getSramTime();

            //--For computing min, max and average cache time
            memOpInfo.increaseTotalSramTime(sramTime); // TODO: Obsolete, can be removed
            memOpInfo.increaseTotalSramTime(currentAinfo.approx, sramTime);

            if (sramTime != 0) { // Immediate inserts doesn't count
            	memOpInfo.compareAndSetMinSramTime(sramTime); // TODO: Obsolete
            	memOpInfo.compareAndSetMinSramTime(currentAinfo.approx, sramTime);
            }
            memOpInfo.compareAndSetMaxSramTime(sramTime); // TODO: Obsolete
            memOpInfo.compareAndSetMaxSramTime(currentAinfo.approx, sramTime);
            
            //--Switch cache lines
            indexAssocLine.remove(evictedAddressTag); // Remove old value
            indexAssocLine.put(currentAddrTag, currentTimeTuple); // Insert new value
            //updateCacheLineTimeStamp(currentAddrTag, tim); // Set time stamp of new memory
            memOpInfo.increaseEvictions(currentTimeTuple.approx, evictedTimeTuple.approx);
        }

        chooseErrorModeForCacheLines(currentTimeTuple, currentAddrTag, tim,
				currentAinfo, evictedTimeTuple, evictedAddressTag,
				evictionOccurred);
        
        return evictedAddressTag;
    }

    /**
     * Help function for store-/loadIntoMemory; memory evictions from SRAM->DRAM
     * may occur.
     * @param key Key to stored object
     * @param If true, the operation is a store; else, it's a load
     * @param currentTime Current time stamp
     * @return TimeTuple of the actual data block
     */
    private <T> Boolean memoryOp(String key, boolean store, long currentTime) {
    	//--Uninitialized memory - from stdin array?
        if (!memorySpace.containsKey(key)) {
            if (debug) {
                System.err.println("EnerJ: Missed key " + key);
                //debugCounters.get("missingKeyCounter").incrementAndGet();
            }
            return null;
        }

        //--Get memory block
        AddressInformation addressInfo = memorySpace.get(key);
        
        //--Count this memory operation
        if (store) {
            memOpInfo.increaseStores(addressInfo.approx);
            memOpInfo.increaseStoredQytes(addressInfo.approx,
                addressInfo.getSize());
            if (debug) System.err.print("Load - ");
        }
        else {
            memOpInfo.increaseLoads(addressInfo.approx);
            memOpInfo.increaseLoadedQytes(addressInfo.approx,
                addressInfo.getSize());
            if (debug) System.err.print("Store - ");
        }
        if (debug) {
            System.err.println(String.format("%d: %s, %d qytes",
                addressInfo.getAddress(), addressInfo.approx ? "approx" : "precise",
                addressInfo.getSize()));
        }
        
        //--Compute useful variants of the memory block address
        final long addrNoByteOffset = addressInfo.getAddress()
        		>> offsetBits; // Full cache line address (minus byte offset)        
        final long addrNoWordOffset = addrNoByteOffset // Full cache line address (minus byte + word offset)
                >> nApproxWordsPerLineBits;
        final long addrIndex = addrNoWordOffset % nIndexes; // Compute cache index
        final long addrTag = getAddrTag(addressInfo);
        
        //--Get content of index in cache
        HashMap<Long, TimeTuple> indexAssocLine = sramContainer.get((int)addrIndex);
        Boolean evictionOccurred = false;
        if (indexAssocLine.containsKey(addrTag)) { // Tag exists in cache: update
            if (ALLOW_APPROXIMATE && addressInfo.approx) {
                switch (SRAMmode) {
                case DYNAMIC:
                    loadChangeStore(addressInfo, addressInfo.getTimeStamp(),
                    		currentTime, INVPROB_DRAM_FLIP_PER_SECOND, true);
                    break;
                case STATIC:
                    loadChangeStore(addressInfo, 0, currentTime,
                        	(store ? INVPROB_SRAM_WRITE_FAILURE : INVPROB_SRAM_READ_UPSET), false);
                    break;
                case NONE:
                    // TODO #general: Can we use this state instead of "ALLOW_APPROXIMATE"?
                    System.err.println("memoryOp: NONE state reached in approximative context");
                    break;
                default:
                    assert false;
                    System.err.println("memoryOp: Non-reachable state reached");
                }
            }
            TimeTuple lineInSRAM = indexAssocLine.get(addrTag);
            lineInSRAM.setLruTime(currentTime);
            memOpInfo.increaseHits(addressInfo.approx);
        }
        else { // Tag doesn't exist in cache: load from DRAM (including eviction)
            // Load time information 
            final TimeTuple currentLineTimeTuple
                = getCurrentCachelineTimeTuple(addressInfo, addrNoWordOffset, currentTime);
            evictCacheLine(indexAssocLine, currentLineTimeTuple, currentTime, addressInfo);
            evictionOccurred = true;
            memOpInfo.increaseMisses(addressInfo.approx);
        }
        return evictionOccurred;
    }

	/**
	 * Get the address tag from an address. Approximate tags will have the
	 * approx bit set.
	 *
	 * @param ainfo The address info
	 * @return The address tag
	 */
	private long getAddrTag(AddressInformation ainfo) {
		long addrTag = ainfo.getAddress() & tagMask; // Compute address tag
        if (ainfo.approx)
            addrTag |= approxMask; // If approximate: set approximation bit (MSB)
		return addrTag;
	}

    /**
     * Print all steps of handling memory addresses in their binary form
     * @param ainfo Address information
     * @param address Address
     * @param addrNoByteOffset Address, no byte offset
     * @param addrNoWordOffset Address, no word offset
     * @param addrIndex Computed index
     * @param addrTag Comuted address tag
     */
    /*
    private void debug_printBinaryRepresentations(AddressInformation ainfo,
            long address, long addrNoByteOffset, long addrNoWordOffset,
            long addrIndex, long addrTag) {
        System.err.println(ainfo.approx ? "Approx " : "Precise ");
        System.err.println(
            "address:\t\t"+ address + "\t" + Long.toBinaryString(address) + "\n"
            + "addrNoByteOffset:\t" + addrNoByteOffset + "\t" + Long.toBinaryString(addrNoByteOffset)  + "\n" 
            + "addrNoWordOffset:\t" + addrNoWordOffset + "\t" + Long.toBinaryString(addrNoWordOffset) + "\n"
            + "addrIndex:\t\t" + addrIndex + "\t" + Long.toBinaryString(addrIndex) + "\n"
            + "addrTag:\t\t" + addrTag + "\t" + Long.toBinaryString(addrTag));
    }
    */

    /**
     * Load some object from the memory hierarchy. This may cause transactions
     * and/or evictions in SRAM/DRAM. This task must be done synchronously.
     * @param key Key to stored object
     * @param tim Current time stamp
     */
    private synchronized Boolean loadFromMemory(String key, long tim) {
        memOpInfo.increaseTotalMemOps();
        return memoryOp(key, false, tim);
    }
    
    /**
     * Put some object into the memory hierarchy. This may cause transactions
     * and/or evictions in SRAM/DRAM. This task must be done synchronously.
     * @param key Key to stored object
     * @param tim Current time stamp
     */
    private synchronized Boolean storeIntoMemory(String key, long tim) {
    	memOpInfo.increaseTotalMemOps();
    	return memoryOp(key, true, tim);
    }

    /* (TRICK TO DIVIDE NOISY FROM DEFAULT)
     * (THIS DOESN'T COVER FOR MERGED, I.E. PREVIOUSLY OVERRIDEN, METHODS)
     */
    /***************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ***************************************************************************/

    /********NOISY VARIABLES AND METHODS********/

    private static final String STATIC_STRING = "static";

    protected final String CONSTS_FILE = "enerjnoiseconsts.json";

    private enum ErrorModes {
        NONE, STATIC, DYNAMIC
    }
    
    private ErrorModes SRAMmode; // What errors are SRAM suffering from?
    private ErrorModes DRAMmode; // What errors are DRAM suffering from?

    // Probabilities.
    protected long INVPROB_SRAM_WRITE_FAILURE = (long)Math.pow(10, 4.94);
    protected long INVPROB_SRAM_READ_UPSET = (long)Math.pow(10, 7.4);
    // FPU characteristics (mantissa bits).
    protected final int MB_FLOAT_PRECISE = 23;
    protected int MB_FLOAT_APPROX = 8;
    protected final int MB_DOUBLE_PRECISE = 52;
    protected int MB_DOUBLE_APPROX = 16;
    // DRAM storage decay.
    protected long INVPROB_DRAM_FLIP_PER_SECOND = (long)Math.pow(10, 5);
    // Operation timing errors.
    protected int TIMING_ERROR_MODE = 2;
    protected float TIMING_ERROR_PROB_PERCENT = 1.5f;

    // Indicates that the approximation should not be used;
    protected final int DISABLED;

    private Map<String, Long> dataAges = new WeakHashMap<String, Long>();
    
    // Timing errors for arithmetic.
    private HashMap<Class<?>, Number> lastValues = new HashMap<Class<?>, Number>();
    

    // Error injection helpers.

    private long toBits(Object value) {
        if (value instanceof Byte || value instanceof Short ||
                value instanceof Integer || value instanceof Long) {
            return ((Number)value).longValue();
        } else if (value instanceof Float) {
            return Float.floatToRawIntBits((Float)value);
        } else if (value instanceof Double) {
            return Double.doubleToRawLongBits((Double)value);
        } else if (value instanceof Character) {
            return ((Character)value).charValue();
        } else if (value instanceof Boolean) {
            if ((Boolean)value) {
                return -1;
            } else {
                return 0;
            }
        } else {
            // Non-primitive type.
            assert false;
            return 0;
        }
    }

    private Object fromBits(long bits, Object oldValue) {
        if (oldValue instanceof Byte) {
            return (byte)bits;
        } else if (oldValue instanceof Short) {
            return (short)bits;
        } else if (oldValue instanceof Integer) {
            return (int)bits;
        } else if (oldValue instanceof Long) {
            return bits;
        } else if (oldValue instanceof Float) {
            return (Float.intBitsToFloat((int)bits));
        } else if (oldValue instanceof Double) {
            return (Double.longBitsToDouble(bits));
        } else if (oldValue instanceof Character) {
            return (char)bits;
        } else if (oldValue instanceof Boolean) {
            return (bits != 0);
        } else {
            assert false;
            return null;
        }
    }

    /**
     * Introduce bit errors into the given value, based on some given probability.
     * @param value The value to be compromised
     * @param invProb The probability of a bit error
     * @return The value, possibly injected with errors
     */
    @SuppressWarnings("unchecked")
	private <T> T bitError(T value, long invProb) {
    	if (!isPrimitive(value))
            return value;
    	
        long bits = toBits(value);
//        System.err.print("[Before: " + bits);
        int width = numQytes(value);

        // Inject errors.
        long mask;
        for (int bitpos = 0; bitpos < (width << 3); ++bitpos) {
            mask = 1 << bitpos;
            double randNum = Math.random();
//            System.err.print(" (randNum: " + randNum + ""
//            		+ "randNum * invProb: " + randNum * invProb 
//            		+ "(long)(randNum * invProb): " + (long)(randNum * invProb)
//            		+ ") "); //DEBUG
            if ((long)(randNum * invProb) == 0) {
                // Bit error!
            	bits ^= mask;
            }
        }
//        System.err.println("; after: " + bits + "]");

        return (T) fromBits(bits, value);
    }

    /**
     * Introduces bit error into the given value based on time stamp and PCM
     * lookup tables.
     * @param value The value to be compromised
     * @param aInfo Address information object of the corresponding value
     * reference
     * @param age Elapsed time between value last touched and now
     */
    @SuppressWarnings("unchecked")
    private <T> T bitErrorPCM(T value, AddressInformation aInfo,
            long age) {
    	if (!isPrimitive(value))
            return value;
    	
        long bits = toBits(value);
        int width = numQytes(value);
        int index = (int)Math.round((log2(age/1000)))-1;
        if (index < 0) // All under 2000ms is -> index 0
            index = 0;
        //double S2ErrorRate = Math.pow(10, S2ErrorRateLookup[index]);
        //double S3ErrorRate = Math.pow(10, S3ErrorRateLookup[index]); 
        double S2ErrorRate = 1/(.01*S2ErrorRateLookup[index]);
        double S3ErrorRate = 1/(.01*S3ErrorRateLookup[index]);

        for (int flipbitpos=0; flipbitpos < (width*8)>>1; flipbitpos++) { 
            if (!aInfo.isFlipped(flipbitpos)) {
                int valuebitpos = 2*flipbitpos;
                if (((bits >> valuebitpos) & 3) == 1) { // Cell is state S3
                    double randNum = Math.random();
                    if ((long)(randNum * S3ErrorRate) == 0) {
                        bits ^= 1 << valuebitpos;
                        aInfo.setFlipped(flipbitpos);
                    }
                }
                else if (((bits >> 2*flipbitpos) & 3) == 2) { // Cell is state S2
                    double randNum = Math.random();
                    if ((long)(randNum * S2ErrorRate) == 0) {
                        bits ^= 1 << valuebitpos;
                        bits ^= 1 << (valuebitpos+1);
                        aInfo.setFlipped(flipbitpos);
                    }
                }
            }
        }
        return (T) fromBits(bits, value);
    }

    /**
     * Check whether some object represents a primitive type, i.e. a number,
     * a boolean or a character
     * @param o The object to be checked
     * @return True if the object represents a number, a boolean or a character;
     * otherwise false
     */
    private boolean isPrimitive(Object o) {
        return (
            o instanceof Number ||
            o instanceof Boolean ||
            o instanceof Character
        );
    }

    /**
     * Copied from "original" dramAgedRead
     * @param value The value to be "compromised"
     * @param lastTime The last time stamp to compute from
     * @param currentTime Current time stamp for reference
     */
    private <T> T driftRead(T value, long lastTime, long currentTime) {
    	// TODO #blockerrors Keep/change "DISABLED" flags?
    	if (!isPrimitive(value) || INVPROB_DRAM_FLIP_PER_SECOND == DISABLED) {
            return value;
        }

        // How old is the data?
        long age = currentTime - lastTime; 
        if (age == 0) { // Instant occasions will not cause any drift
            return value;
        }
        
        // Error injection
        long invprob = INVPROB_DRAM_FLIP_PER_SECOND * 1000L / age;
        value = bitError(value, invprob);

        return value;
    }

    /**
     * PCM drifting.
     * @param value The value to be "compromised"
     * @param aInfo Address information object of the corresponding value
     * reference
     * @param lastTime The last time stamp to compute from
     * @param currentTime Current time stamp for reference
     */
    private <T> T driftReadPCM(T value, AddressInformation aInfo,
            long lastTime, long currentTime) {
    	if (!isPrimitive(value)) {
            return value;
        }

        // How old is the data?
        long age = currentTime - lastTime; 
        if (age == 0) { // Instant occasions will not cause any drift
            return value;
        }
        
        // Error injection
        value = bitErrorPCM(value, aInfo, age);

        return value;
    }

    /**
     * Create keys for memory accesses of fields = object hashcode + some loaded
     * field.
     * @param obj Object to be touched in memory
     * @param field Field name 
     * @return Key string  
     */
    private String memoryKey(Object obj, String field) {
        String identifier = Integer.toString(System.identityHashCode(obj));
        if (obj == null) {
            if (debug)
                System.err.println("memoryKey: Null object found; field = " + field);
            return STATIC_STRING + field; // Workaround for null objects; (only static fields behave like this)
        }

        // Now, there exists a method doing *almost* this, has to take care of
        // static fields separately, however
        Class<?> objClass = obj.getClass();
        while (true) {
            try { // Handle eventual static fields
                Field f = objClass.getDeclaredField(field); // If failing, "go to" exception
                if (Modifier.isStatic(f.getModifiers())) {
                    identifier = STATIC_STRING + objClass.getName();
                    if (debug)
                        System.err.println("memoryKey: STATIC KEY = " + identifier);
                }
                break;
            }
            catch (NoSuchFieldException e) {
                if (debug)
                    System.err.println("Field " + field + " not found in "
                        + objClass.getName() + "; trying superclass...");
                objClass = objClass.getSuperclass();
                continue;
            }
        }
        return identifier + field;
    }

    /**
     * Create keys for memory accesses of arrays = object hashcode + some array
     * index.
     * @param obj Object to be touched in memory
     * @param index Array index
     * @return Key string
     */
    private String memoryKey(Object array, int index) {
        return "array" + System.identityHashCode(array) + "idx" + index;
    }

    /**
     * Content imported from the constructor of PrecisionRuntimeNoisy.
     * Noisy constants are imported in the following order priority:
     * 1. Read properties at runtime
     * 2. Imported JSON file
     * 3. Hard coded
     */
    private void doNoisyConstructorThings() {
        System.err.println("Initializing noisy EnerJ runtime.");
        
        FileReader fr = null;
        try {
            fr = new FileReader(CONSTS_FILE);
        } catch (IOException exc) {
            System.err.println("\tConstants file not found; using defaults.");
        }

        if (fr != null) {
            try {
            	JSONObject json = new JSONObject(new JSONTokener(fr));
				INVPROB_SRAM_WRITE_FAILURE =
					json.getLong("INVPROB_SRAM_WRITE_FAILURE");
				INVPROB_SRAM_READ_UPSET =
					json.getLong("INVPROB_SRAM_READ_UPSET");
				MB_FLOAT_APPROX = json.getInt("MB_FLOAT_APPROX");
				MB_DOUBLE_APPROX = json.getInt("MB_DOUBLE_APPROX");
				INVPROB_DRAM_FLIP_PER_SECOND =
					json.getLong("INVPROB_DRAM_FLIP_PER_SECOND");
				TIMING_ERROR_MODE = json.getInt("TIMING_ERROR_MODE");
				TIMING_ERROR_PROB_PERCENT =
                    (float)json.getDouble("TIMING_ERROR_PROB_PERCENT");
            } catch (JSONException exc) {
                System.err.println("   JSON not readable!");
            }
        }
        
        //--Read property constants
        //--Hack-ish way to read from properties while still preserving other
        //--values if none set
        INVPROB_SRAM_WRITE_FAILURE
        	= Long.parseLong(System.getProperty("INVPROB_SRAM_WRITE_FAILURE",
        		Long.toString(INVPROB_SRAM_WRITE_FAILURE)));
        INVPROB_SRAM_READ_UPSET
        	= Long.parseLong(System.getProperty("INVPROB_SRAM_READ_UPSET",
        		Long.toString(INVPROB_SRAM_READ_UPSET)));
        MB_FLOAT_APPROX
        	= Integer.parseInt(System.getProperty("MB_FLOAT_APPROX",
        		Integer.toString(MB_FLOAT_APPROX)));
        MB_DOUBLE_APPROX
    		= Integer.parseInt(System.getProperty("MB_DOUBLE_APPROX",
    			Integer.toString(MB_DOUBLE_APPROX)));
        INVPROB_DRAM_FLIP_PER_SECOND
        	= Long.parseLong(System.getProperty("INVPROB_DRAM_FLIP_PER_SECOND",
            	Long.toString(INVPROB_DRAM_FLIP_PER_SECOND)));
        TIMING_ERROR_MODE
			= Integer.parseInt(System.getProperty("TIMING_ERROR_MODE",
				Integer.toString(TIMING_ERROR_MODE)));
        TIMING_ERROR_PROB_PERCENT
	        = Float.parseFloat(System.getProperty("TIMING_ERROR_PROB_PERCENT",
					Float.toString(TIMING_ERROR_PROB_PERCENT)));

        System.err.println("\tSRAM WF: " + INVPROB_SRAM_WRITE_FAILURE);
        System.err.println("\tSRAM RU: " + INVPROB_SRAM_READ_UPSET);
        System.err.println("\tfloat bits: " + MB_FLOAT_APPROX);
        System.err.println("\tdouble bits: " + MB_DOUBLE_APPROX);
        System.err.println("\tDRAM decay: " + INVPROB_DRAM_FLIP_PER_SECOND);
        System.err.println("\ttiming error mode: " + TIMING_ERROR_MODE);
        System.err.println("\ttiming error prob: " + TIMING_ERROR_PROB_PERCENT);
    }

    /* (TRICK TO DIVIDE NOISY FROM DEFAULT)
     * (THIS DOESN'T COVER FOR MERGED, I.E. PREVIOUSLY OVERRIDEN, METHODS)
     */
    /***************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ****************************************************************************
    ***************************************************************************/

    /********DEFAULT VARIABLES********/

    // This map *only* contains approximate objects. That is,
    // info.get(???).approx == true
    private Map<Object, ApproximationInformation> info
        = new WeakIdentityHashMap<Object, ApproximationInformation>();

    // This "parallel" map is used just to receive object finalization events.
    // Phantom references can't be dereferenced, so they can't be used to look
    // up information. But there are the only way to truly know exactly when
    // an object is about to be deallocated. This map contains *all* objects,
    // even precise ones.
    private Map<PhantomReference<Object>, ApproximationInformation> phantomInfo =
        new HashMap<PhantomReference<Object>, ApproximationInformation>();
    private ReferenceQueue<Object> referenceQueue = new ReferenceQueue<Object>();

    /**
     * Time of creation of 'this' object
     */
    long startup;

    /**
     * Keeps counters for runtime operations, e.g. '+', on precise objects   
     */
    private Map<String, Integer> preciseOpCounts = new HashMap<String, Integer>();
    
    /**
     * Keeps counters for runtime operations, e.g. '+', on approximative objects
     */
    private Map<String, Integer> approxOpCounts  = new HashMap<String, Integer>();
    
    /**
     * Count how much approximative data has been kept in memory during the execution
     */
    private Map<String, Long> approxFootprint = new HashMap<String, Long>();
    
    /**
     * Count how much precise data has been kept in memory during the execution
     */
    private Map<String, Long> preciseFootprint = new HashMap<String, Long>();

    /**
     * If true, additional debug info will be shown during execution
     */
    private static boolean debug = Boolean.parseBoolean(System.getenv("EnerJDebug"));

    /**
     * Mapping Thread IDs to stacks of CreationInfo objects.
     */
    Map<Long, Stack<CreationInfo>> creations = new HashMap<Long, Stack<CreationInfo>>();

    /**
     * Debug related counters.  
     */
    private static Map<String, AtomicInteger> debugCounters
        = new HashMap<String, AtomicInteger>();

    /**
     * Set approximation (meta) data for the object.
     * @param o The object
     * @param approx Whether object is approximate or not
     * @param heap True if object is on heap; false false if object is on stack
     * @param preciseSize Precise data size
     * @param approxSize Approximative data size
     * @return The phantom reference to the (enqueued) data
     */
    @Override
    public PhantomReference<Object> setApproximate(
        Object o, boolean approx, boolean heap, int preciseSize, int approxSize
    ) {
        if (debug) {
            System.out.println("EnerJ: Add object " + System.identityHashCode(o)
                + " to system.");
            
            // if (o instanceof Reference) {
            //     @SuppressWarnings("unchecked")
            //     Reference<Object> R = (Reference<Object>) o;
            //     System.out.println("\tReference: "
            //         + (R.value == null ? "NULL" : R.value.getClass().getName()));
            // }
            // else {
            //     System.out.println("\tClass: " + o.getClass().getName());
            // }
        }

        long time = System.currentTimeMillis();
        ApproximationInformation infoObj =
            new ApproximationInformation(time, approx, heap,
                                         preciseSize, approxSize);
        PhantomReference<Object> phantomRef = new PhantomReference<Object>(o, referenceQueue);

        // Add to bookkeeping maps.
        synchronized (this) {
            if (approx)
                info.put(o, infoObj);
            phantomInfo.put(phantomRef, infoObj);
        }

        return phantomRef;
    }

    /**
     * True if the some object is approximate, else false (if precise)
     * @return true if the Object o is approximate
     */
    @Override
    public boolean isApproximate(Object o) {
        if (debug) {
            System.out.println("EnerJ: Determine whether \"" 
                + (o != null ? System.identityHashCode(o) : "null")
                + "\" is approximate");
        }
        boolean approx;
        synchronized (this) { // If it's approximate, then it must be in the 'info' hashmap
            approx = info.containsKey(o);
        }
        return approx;
    }

    /**
     * Get log2(x) of some x.
     * @param num Input to logarithmic function
     * @return log2(x) of some x
     */
    private double log2(double num) {
        return Math.log(num)/0.6931471805599453; // Math.log(2);
    }

    /**
     * Return the correct AnnotationType of the corresponding string.
     * @param annotation Annotation type describing string
     * @return The corresponding AnnotationType
     */
    private AnnotationType mapAnnotationType(String annotation) {
        switch (annotation) {
        case "Approx":
            return AnnotationType.Approx;
        case "Context":
            return AnnotationType.Context;
        case "Precise":
            return AnnotationType.Precise;
        default:
            assert false; // Should be any of the other three
            return null;
        }
    }

    /**
     * Import information about classes gathered at compile time. This file
     * must exist for the operation to go further.
     * @param fileName Name of the JSON file to be imported
     */
    @SuppressWarnings("unchecked")
	private void importClassInfoAndInsertStaticData(String fileName) {
        JSONObject jsonClasses, jsonFields, jsonField;
        HashMap<String, FieldInfoContainer> fieldsInfo = null;
        FieldInfoContainer fic;
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            StringBuffer sb = new StringBuffer();
            for(String line; (line = br.readLine()) != null; ) { // Loop over all lines
                sb.append(line);
            }
            jsonClasses = new JSONObject(sb.toString());
            
            for (Iterator<String> itClasses = jsonClasses.keys(); itClasses.hasNext();) {
                String keyClass = itClasses.next();
                
                if (debug)
                    System.out.println(keyClass);
                
                jsonFields = jsonClasses.getJSONObject(keyClass);
                fieldsInfo = new HashMap<String, FieldInfoContainer>();
                for (Iterator<String> itField = jsonFields.keys();
                        itField.hasNext();) {
                    fic = new FieldInfoContainer();
                    String keyField = itField.next();
                    jsonField = jsonFields.getJSONObject(keyField);
                    fic.annotation = ALLOW_APPROXIMATE ?
                            mapAnnotationType(jsonField.getString("annotation")) :
                                AnnotationType.Precise;
                    fic.isStatic = jsonField.getBoolean("static");
                    fic.isFinal = jsonField.getBoolean("final");
                    fic.fieldType = jsonField.getString("type");
                    if (debug) {
                        System.out.print("\t" + keyField + " - ");
                        System.out.println(fic.toString());
                    }
                    fieldsInfo.put(keyField, fic);
                    
                    // Put static members into static memory area.
                    // Context is set to always be precise, as setting it as
                    // Context would be meaningless and we also want to be sure
                    // it doesn't behavior in some unintended way.
                    
                    // TODO: wrote this late at night; might need some cleanup,
                    // code factorization and checks if it's really consistent   
                    // Put static members into static memory area
                    if (fic.isStatic) {
                        // Whether or not the field is a reference and therefore
                        // should be placed in the precise memory area should
                        // have been determined earlier. 
                        boolean approx = fic.annotation == AnnotationType.Approx; // Checking for Context is meaningless
                        long address = allocateMemoryAux(fic.fieldType, approx);
                        long tim = System.currentTimeMillis();
                        int preciseSize = 0, approxSize = 0, fieldSize = numQytes(fic.fieldType, approx);
                        if (approx)
                            approxSize = fieldSize;
                        else
                            preciseSize = fieldSize;
                        AddressInformation addressInfo =
                                new AddressInformation(tim, approx, true, preciseSize,
                                        approxSize, address, startup-1); // -1: Trick to force oldest possible time stamp
                        // TODO #bug: How to set static objects?
                        String key = STATIC_STRING + keyField; // Workaround...
                        memorySpace.put(key, addressInfo);
                        addToCachelineTracker(approx ? address | approxMask : address, key);
                    }
                }
                classInfo.put(keyClass, fieldsInfo);
            }
            br.close();
        }
        catch (JSONException e) {
            System.err.println("Error while parsing JSONObject.");
            System.err.println(e.getMessage());
            // e.printStackTrace();
            System.exit(1); // No idea to continue beyond this point
        }
        catch (IOException e) {
            System.err.println("Error while reading class data from file.");
            System.exit(1); // No idea to continue beyond this point
        }
    }

    /**
     * Start threads that perform logging and cleanup tasks during runtime.
     * Add hook thread for final cleanup stage when JVM shuts down.
     */
    private void startCleanUpThreads() {
        final Thread deallocPollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Loop that continuously removes items from PhantomReference queue
                deallocPoll(); 
            }
        });
        deallocPollThread.setDaemon(true); // Automatically shut down.
        deallocPollThread.start();

        // Perform cleanup operations when JVM shuts down
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                cleanUpObjects();
                dumpCounts();
            }
        }));
    }

    public PrecisionRuntimeTolop(int cacheSize,
                                 int cacheLineSize,
                                 int sramAssociativity,
                                 String classInfoFilename) {
        super();

        startup = System.currentTimeMillis();

        /* Initialize cache hierarchy
         * Line size for caches are 64 bytes = 16 words in i7, etc
         * http://stackoverflow.com/a/15333156/1283083
         * 2048 is a guessed number for L1
         * (Found 32768 for local machine, but still)
         */
        this.cacheSize = cacheSize;
        this.cacheLineSizeInWords = cacheLineSize;
        this.cacheLineSizeInQytes = cacheLineSizeInWords*wordSize;
        this.sramAssociativity = sramAssociativity;
        
        if (cacheSize % cacheLineSizeInWords != 0) {
            System.err.println("Unallowed cache- or cacheline size");
            System.err.println("Cache size must be congruent the cacheline size");
            System.exit(1);
        }

        // Only direct mapped or associativity in multiples of 2 are allowed
        if (sramAssociativity < 0 || sramAssociativity != 1 && sramAssociativity % 2 != 0) {
            System.err.println("Unallowed associativity");
            System.err.println("Cache associativity value must be positive and "
                    + "direct mapped or a multiple of 2");
            System.exit(1);
        }
        
        nIndexes = (cacheSize/sramAssociativity) / (cacheLineSizeInQytes); // Default = 8
        nCacheLinesBits = (int)log2((double)nIndexes); // Default = 4
        nApproxWordsPerLineBits = (int)log2((double)cacheLineSizeInWords); // Default = 4

        if (debug) {
            System.out.println(String.format("cacheSize: %d\ncacheLineSize: %d\n"+
                    "sramAssociativity: %d\nnIndexes: %d\nnApproxWordsPerLineBits: %d\n",
                    cacheSize, cacheLineSizeInWords, sramAssociativity,
                    nIndexes, nApproxWordsPerLineBits));
        }
        
        // Array for cache lines; inner hash map for the n different associative ways
        // Maps address tag -> time data
        sramContainer = new ArrayList<HashMap<Long, TimeTuple>>();
        for (int i = 0; i < nIndexes; i++) {
            sramContainer.add(new HashMap<Long, TimeTuple>()); // New hash map from 0 -> nIndexes    
        }

        // For keeping track of which data "belongs" to which cache line
        // Needed for introducing errors upon cache loads/stores
        cachelineTracker = new HashMap<Long, ArrayList<String>>();
        
        // Compute mask used for getting address tags
        int tagSize = addressSizeBits - (nCacheLinesBits + nApproxWordsPerLineBits + offsetBits);
        tagMask = ((long)Math.pow(2, tagSize)-1) << (addressSizeBits-tagSize);
        
        // In debug mode: initialize debug counters
        if (debug) {
            debugCounters.put("beforeCounter", new AtomicInteger());
            debugCounters.put("enterCounter", new AtomicInteger());
            debugCounters.put("afterCounter", new AtomicInteger());
            debugCounters.put("insertedArraysCounter", new AtomicInteger());
            debugCounters.put("missingKeyCounter", new AtomicInteger());
        }
        
        ALLOW_APPROXIMATE = Boolean.parseBoolean(System.getProperty("AllowApproximate", "true"));
        DISABLED = ALLOW_APPROXIMATE ? 0 : 1;
        ERRORS_ON_CL = Boolean.parseBoolean(System.getProperty("ErrorsOnCL", "true"));

        // What sort of errors will the memory suffer from?
        String SRAMMod = System.getProperty("SRAMMode", "dynamic").toLowerCase();
        switch (SRAMMod) {
		case "static":
			SRAMmode = ErrorModes.STATIC;
			break;
		case "none":
			SRAMmode = ErrorModes.NONE; // Force anything else as dynamic
		default:
			SRAMmode = ErrorModes.DYNAMIC; // Any "erroneous" value yields dynamic
			break;
		}
        
        // DRAM shouldn't suffer from static errors
        String DRAMMod = System.getProperty("DRAMMode", "dynamic").toLowerCase();
        switch (DRAMMod) {
		case "none":
			DRAMmode = ErrorModes.NONE; // Force anything as dynamic
		default:
			DRAMmode = ErrorModes.DYNAMIC; // Any "erroneous" value yields dynamic
			break;
		}

        // PCM specific simulation
        String PCMSimulation = System.getProperty("PCMSimulation", "false").toLowerCase();
        switch (PCMSimulation) {
        case "true":
            PCM_SIMULATION = true;
            break;
        default:
            PCM_SIMULATION = false;
            break;
        }
        if (PCM_SIMULATION)
            System.err.println("NOTE: This is a PCM simulation run.");
        
        // Import class information, such as approx/precise annotations
        // Also, insert any static members to memory
        importClassInfoAndInsertStaticData(classInfoFilename);

        // After inserting static memory, pad both memory spaces so that
        // other memory doesn't go into the same cache lines as static
        // padMemory(true);
        padMemory(false);

        startCleanUpThreads();

	if (ALLOW_APPROXIMATE)
		doNoisyConstructorThings();
	else
        	System.err.println("Initializing precise EnerJ runtime - approximativeness disabled.");
    }

    /**
     * Starts a cleanup loop upon construction for the PhantomReferences.
     * Also, it creates a shutdown hook (a thread that starts when the JVM shuts
     * down), that cleans up objects and dumps count measurements.
     * @param cacheSize Total size of the cache
     * @param cacheLineSize Size of one cache line
     * @param sramAssociativity Cache associativity 
     */
    public PrecisionRuntimeTolop(int cacheSize,
                                 int cacheLineSize,
                                 int sramAssociativity) {
        this(cacheSize, cacheLineSize, sramAssociativity, JSON_INPUT_FILE_NAME);
    }

    /**
     * Starts a cleanup loop upon construction for the PhantomReferences.
     * Also, it creates a shutdown hook (a thread that starts when the JVM shuts
     * down), that cleans up objects and dumps count measurements.
     * @param classInfoFilename JSON file to import class info data from
     */
    public PrecisionRuntimeTolop(String classInfoFilename) {
        this(Integer.parseInt(System.getProperty("CacheSize", "2048")), // In qytes, not bytes
                Integer.parseInt(System.getProperty("CacheLineSize", "16")), // Quad-words, not byte-words
                        Integer.parseInt(System.getProperty("CacheAssociativity", "4")), //Default: 4-way
                            classInfoFilename);
        System.err.println(String.format("CacheSize: %d, CacheLineSize: %d, "
                    + "CacheAssociativity: %d",
                    Integer.parseInt(System.getProperty("CacheSize", "2048")),
                    Integer.parseInt(System.getProperty("CacheLineSize", "16")),
                    Integer.parseInt(System.getProperty("CacheAssociativity", "4"))
                    )); //DEBUG
    }

    /**
     * Starts a cleanup loop upon construction for the PhantomReferences.
     * Also, it creates a shutdown hook (a thread that starts when the JVM shuts
     * down), that cleans up objects and dumps count measurements.
     */
    public PrecisionRuntimeTolop() {
        this(System.getProperty("jsonInputName", JSON_INPUT_FILE_NAME));
    }

    /**
     * Sort all member fields in an object in decreasing size order.  
     * @param created The object, whose fields are about to be sorted
     * @return A list of the sorted fields
     */
    // private List<Map.Entry<Field, Integer>> sortClassFields(Object created) {
    private List<Map.Entry<MyTuple<String, Field>, Integer>> sortClassFields(Object created) {
        Map<MyTuple<String, Field>, Integer> unSortedClassFieldTups
            = new HashMap<MyTuple<String, Field>, Integer>();
        
        // Sort fields in descending size order
        Class<?> createdClass = created.getClass();
        while (createdClass != null) {
            Field[] theFields = createdClass.getDeclaredFields();
            for (Field someField : theFields) {
                unSortedClassFieldTups.put(
                		new MyTuple<String,Field>(createdClass.getName(), someField),
                        prioritizeType(someField.getType().getName()));
            }
            createdClass = createdClass.getSuperclass();    // Traverse unto superclass
        }
        List<Map.Entry<MyTuple<String, Field>, Integer>> sortedClassFields =
                new LinkedList<Map.Entry<MyTuple<String, Field>, Integer>>(unSortedClassFieldTups.entrySet());
        Collections.sort(sortedClassFields, new Comparator<Map.Entry<MyTuple<String, Field>, Integer>>() {
            @Override
            public int compare(Entry<MyTuple<String, Field>, Integer> obj1,
                    Entry<MyTuple<String, Field>, Integer> obj2) {
                return (obj2.getValue()).compareTo(obj1.getValue());
            }
        });
        
        return sortedClassFields;
    }

    /**
     * Allocate memory for the specified data type.
     * @param typeName Name of the type to allocate
     * @param approx Whether the allocated memory is approximate or not
     * @return Starting address of the allocated space
     */
    private long allocateMemoryAux(String typeName, boolean approx) {
        int fieldSize = numQytes(typeName, approx);
        // Check that we don't span over two cache lines for an object
        long cacheLineSpaceLeft = cacheLineSizeInQytes
            - (peekAddress(approx) % cacheLineSizeInQytes);
        // If so, add padding to preserve alignment
        if (cacheLineSpaceLeft < fieldSize) {
            createAddress(cacheLineSpaceLeft, approx);
        }
        return createAddress(fieldSize, approx);
    }

    /**
     * Pad out the rest of a cache line size to enforce memory alignment.
     * @param approx Whether to pad the approximate memory space or not
     * @return Starting address of the aligned memory 
     */
    private long padMemory(boolean approx) {
        int locCacheLineSize = cacheLineSizeInWords * wordSize;
        long cacheLineByte = peekAddress(approx) % locCacheLineSize;
        long cacheLineSpaceLeft;
        if (0 != cacheLineByte) { // If we are not aligned: pad out the rest of the cache line
            cacheLineSpaceLeft = locCacheLineSize - peekAddress(approx) % locCacheLineSize;
            createAddress(cacheLineSpaceLeft, approx);
        }
        return peekAddress(approx);
    }

    private boolean fieldIsApprox(FieldInfoContainer fic,  CreationInfo c) {
        if (ALLOW_APPROXIMATE) {
            switch (fic.annotation) {
                case Approx:
                    return true;
                case Precise:
                    return false;
                default: // Context
                    return c.approx;
                }
        }
        return false; // Force to always be precise
    }

    private synchronized void addClassFieldsToMemory(
	        List<Map.Entry<MyTuple<String, Field>, Integer>> sortedClassFields,
	        CreationInfo c, Object created) {
    	HashMap<String, FieldInfoContainer> fieldsInfo;
    	FieldInfoContainer fic;
        boolean approx;
        int approxSize = 0, preciseSize = 0;
        String className = null;
        long tim = System.currentTimeMillis(); // Time stamp of creation
        long address;
        int fieldSize;
        AddressInformation ainfo;
        String key = null;
        for (Map.Entry<MyTuple<String, Field>, Integer> e : sortedClassFields) {
            //--Static fields lives in the static area and should'nt be allocated
            MyTuple<String, Field> classFieldTup = e.getKey();

            //--Inner classes may manifest as names containing "$"
            className = classFieldTup.x.contains("$") ?
                classFieldTup.x.replace('$','.') :
                classFieldTup.x;

            //--Skip static fields
            if (Modifier.isStatic(classFieldTup.y.getModifiers()))
                continue;

            String fieldname = classFieldTup.y.getName();
            //System.err.println("className: "+className+"; fieldname: "+fieldname);

            //--Get class annotation info
            //--This must exist, otherwise the program execution cannot proceed
            if (!classInfo.containsKey(className)) {
                System.err.println("PANIC: " + className + " doesn't exist.");
                System.exit(1);
            }
            
            fieldsInfo = classInfo.get(className);
                        
            //--If there's no available class info from previous compilation,
            //--there's no reason to continue: crash and burn!
            if (!fieldsInfo.containsKey(fieldname)) {
                System.err.println("Class " + className
                    + " doesn't contain field " + fieldname
                    + "; please check the inout JSON file.");
                System.err.println("fieldsInfo: " + fieldsInfo.toString());
                System.exit(1);
            }

            fic = fieldsInfo.get(fieldname);

            approx = fieldIsApprox(fic, c);
            
            //--Allocate the memory (i.e. get simulated address for this data)
            address = allocateMemoryAux(fic.fieldType, approx);
            key = memoryKey(created, fieldname);
            addToCachelineTracker(approx ? address | approxMask : address, key);

            //--Compute approximate or precise size
            fieldSize = numQytes(fic.fieldType, approx);
            if (approx) {
                approxSize = fieldSize;
            }
            else {
                preciseSize = fieldSize;
            }
            
            //--Register allocated memory
            ainfo = new AddressInformation(tim, approx, true, preciseSize,
                                              approxSize, address, startup-1); // -1: Trick to force oldest possible time stamp
            ainfo.setType(created, fieldname);
            memorySpace.put(key, ainfo);
            if (debug) {
                System.out.println(
                      "\t" + Modifier.toString(e.getKey().y.getModifiers())
                    + " " + e.getKey().y.getType().getName()
                    + " " + fieldname); //DEBUG
            }
        }
    }
    
    /** 
     * Called immediately before the creation of a new object.
     * @param creator Creating (parent) object
     * @param approx Whether the object is approximative or not
     * @param preciseSize Size of a precise object 
     * @param approxSize Size of an approximative object
     * @return true if operation went well, else false; although, this implementation
     * always returns true
     */
    @Override
    public boolean beforeCreation(Object creator, boolean approx,
                                  int preciseSize, int approxSize) {
        if (debug) {
            System.out.println("EnerJ: before creator \""
                    + System.identityHashCode(creator)
                    + "\" creates new " + (approx ? "approximate" : "precise")
                    + " object of sizes P" + preciseSize + "/A" + approxSize);
            // Creator happens often (almost) always to be 'java.lang.Thread' 
            debugCounters.get("beforeCounter").incrementAndGet(); // DEBUG
        }
        // To pass info on to enterConstructor
        CreationInfo c = new CreationInfo(creator, approx, preciseSize, approxSize);

        long tid = Thread.currentThread().getId(); // Current thread id

        Stack<CreationInfo> stack = creations.get(tid); // Get info stack based on current thread ID
        if (stack==null) { // ...or create a new if first time accessed
            stack = new Stack<CreationInfo>();
        }
        stack.push(c); // Insert the new object info onto the stack
        creations.put(tid, stack); // ...and save it into the hash map

        return true;
    }

    /**
     * Called when the objects' constructor is called and logs this call.
     * @param The object whose constructor is entered
     * @return true if the operation went well, else false
     */
    @Override
    public boolean enterConstructor(Object created) {
        if (debug) {
            System.out.println("EnerJ: enter constructor for object \""
                + System.identityHashCode(created) + "\"");
            System.out.println("\t" + created.getClass().getName());
            debugCounters.get("enterCounter").incrementAndGet(); // DEBUG
        }

        Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());

        // Handle non-EnerJ behavior
        if (stack==null) {
            if (debug) {
                System.out.println("EnerJ: enter constructor for object \""
                    + System.identityHashCode(created)
                    + "\" found a null stack.");
            }
            // probably instantiated from non-EnerJ code
            return false;
        }
        if (stack.size()<=0) {
            if (debug) {
                System.out.println("EnerJ: enter constructor for object \""
                    + System.identityHashCode(created)
                    + "\" found an empty stack.");
            }
            // probably instantiated from non-EnerJ code
            return false;
        }

        CreationInfo c = stack.pop(); // Get the lastly pushed object info

        /* We cannot compare c.creator; we assume that there is no thread interleaving
           between the call of beforeCreation and enterConstructor.
           TODO: Some methods should probably be synchronized, but I think this
           wouldn't help against this particular problem.
        */ 
        this.setApproximate(created, c.approx, true, c.preciseSize, c.approxSize);

        // Sort all fields in decreasing size order
        List<Map.Entry<MyTuple<String, Field>, Integer>> sortedClassFields
            = sortClassFields(created);

        // Now, register all class fields in sorted order
        addClassFieldsToMemory(sortedClassFields, c, created);

        return true;
    }

    /**
     * (Can be) called immediately after an object was created.
     * @param creator The creating object (parent instance)
     * @param created Some created object (child instance)
     * @return true if there are objects created in this thread; otherwise false
     */
    @Override
    public boolean afterCreation(Object creator, Object created) {
        if (debug) {
            System.out.println("EnerJ: after creator \"" + System.identityHashCode(creator)
                    + "\" created new object \"" + System.identityHashCode(created) + "\"");
            System.out.println(String.format("EnerJ: %s -> %s",
                    creator.getClass().getName(), created.getClass().getName())); //DEBUG
            debugCounters.get("afterCounter").incrementAndGet(); //DEBUG
        }

        Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());
        // Could stack ever be null? I guess not, b/c "afterC" is only called,
        // if "beforeC" was called earlier.

        if (stack.size()<=0) {
            if (debug) {
                System.out.println("EnerJ: after creator \""
                        + System.identityHashCode(creator)
                        + "\" created new object \""
                        + System.identityHashCode(created)
                        + "\" found an empty stack.");
            }
            // no worries, the stack must have been emptied in enterConstructor
            return false;
        }

        CreationInfo c = stack.peek();

        if (c.creator == creator) {
            this.setApproximate(created, c.approx, true, c.preciseSize, c.approxSize);
            if (c.approx) {
                stack.pop();
            }
        } else {
            if (debug) {
                System.out.println("EnerJ: after creator \""
                        + System.identityHashCode(creator)
                        + "\" created new object \""
                        + System.identityHashCode(created)
                        + "\" found mismatched creator \"" + c.creator + "\".");
            }
            // if the creators do not match, the entry was already removed.
        }

        // DEBUG
        if (debug) {
            System.out.println(
                    String.format("afterCreation: %s: preciseSize = %d - approxSize = %d",
                            created.getClass().getName(), c.preciseSize, c.approxSize));
            if (c.preciseSize != 0 && c.approxSize !=0) { // Mixed setting - interesting
                System.out.println("Note: both precise/approx values present");
            }
            
            // Fields inside 'created'
            Field[] allFields = created.getClass().getDeclaredFields();
            for (Field f : allFields) {
                System.out.println("\t" + Modifier.toString(f.getModifiers())
                    + " " + f.getType().getName() + " " + f.getName()); //DEBUG
            }
        }

        return true;
    }

    /**
     * Wrapper method for afterCreation.
     * @param before (Not in use)
     * @param created Some created object
     * @param creator The creator of the (newly) created object
     * @return created
     */
    @Override
    public <T> T wrappedNew(boolean before, T created, Object creator) {
        afterCreation(creator, created);
        return created;
    }

    /**
     * Gather info about a created array.
     * @param created The created array
     * @param dims Array dimensions (?)
     * @param approx Whether the array is approximate or not
     * @param preciseElSize Size of precise elements
     * @param approxElSize Size of approximate elements
     * @return created The created array (i.e. input argument 'created')
     */
    @Override
    public <T> T newArray(T created, int dims, boolean approx,
                          int preciseElSize, int approxElSize) {
        int elems = 1;
        Object arr = created;
        if (!ALLOW_APPROXIMATE) // Turn off approximativity if precise objects only
            approx = false;

        // Calculate total size from all dimensions
        for (int i = 0; i < dims; ++i) {
            elems *= Array.getLength(arr);
            if (Array.getLength(arr) == 0)
                break;
            if (i < dims - 1)
                arr = Array.get(arr, 0);
        }
        this.setApproximate(created, false, true,
                            preciseElSize*elems, approxElSize*elems);

        if (!approx) {
            preciseElSize += approxElSize;
            approxElSize = 0;
        }

        // Create addresses - first pointers (references), then values
        // References are _always_ precise => approx == false
        assignAddressesToArrayItems(created, false, false); // References
        
        // If the array is approximative, pad out after the references to make
        // make space between this array and any followin approximative data.
        // If the array is precise, don't pad, as the precise content data could
        // be put after after these.
        int wastedSpace;
        if (approx) {
            wastedSpace = (int)(peekAddress(false) % cacheLineSizeInQytes);
            if (wastedSpace != 0 && padCacheLines) {
                createAddress(cacheLineSizeInQytes - wastedSpace, approx);  
            }
        }
        
        assignAddressesToArrayItems(created, approx, true);  // Values
        // Eventually pad the rest of a non-filled cache-line
        wastedSpace = (int)(peekAddress(approx) % cacheLineSizeInQytes);
        if (wastedSpace != 0 && padCacheLines) {
            createAddress(cacheLineSizeInQytes - wastedSpace, approx);  
        }

        if (debug) {
            System.out.println("EnerJ: created array \"" +
                System.identityHashCode(created) +
                "\" with size " + elems);
            debugCounters.get("insertedArraysCounter").incrementAndGet();
        }

        return created;
    }

    /**
     * Counting infrastructure, keeps track of number of operations 
     * @param name Name of the operation, namely what arithmetic operation took
     * place with what type
     * @param approx Whether operation is approximate or not
     */
    private synchronized void countOperation(String name, boolean approx) {
        Map<String, Integer> map = null;
        if (approx)
            map = approxOpCounts;
        else
            map = preciseOpCounts;
        if (map.containsKey(name)) {
            map.put(name, map.get(name) + 1); // m[name] += 1
        } else {
            map.put(name, 1);
        }
    }

    /**
     * Add values to data counters. Must be run synchronously. 
     * @param name Name of post, e.g. "heap-objects" or "stack-bytes" 
     * @param approx Whether object is approximate or not
     * @param amount (Additional) value of name
     */
    private synchronized void countFootprint(String name, boolean approx,
                                             long amount) {
        Map<String, Long> map = null;
        // Precise or approximate object? Choose map accordingly. 
        if (approx)
            map = approxFootprint;
        else
            map = preciseFootprint;
        if (map.containsKey(name)) {
            map.put(name, map.get(name) + amount);
        } else {
            map.put(name, amount);
        }
    }

    /**
     * Gathers all data about created objects, sizes etc about all precise and
     * approximate data and writes the results as the JSON open standard format
     * to the file "enerjstats.json".
     */
    private synchronized void dumpCounts() {
        JSONStringer stringer = new JSONStringer();
        try {
            stringer.object();

            // Output operation counts.
            Set<String> ops = new HashSet<String>();
            ops.addAll(approxOpCounts.keySet());
            ops.addAll(preciseOpCounts.keySet());
            stringer.key("operations");
            stringer.object();
            for (String op : ops) {
                int approxCount = 0;
                if (approxOpCounts.containsKey(op))
                    approxCount = approxOpCounts.get(op);
                int preciseCount = 0;
                if (preciseOpCounts.containsKey(op))
                    preciseCount = preciseOpCounts.get(op);

                stringer.key(op);
                stringer.array();
                stringer.value(preciseCount);
                stringer.value(approxCount);
                stringer.endArray();
            }
            stringer.endObject();

            // Output footprint counts.
            Set<String> footprints = new HashSet<String>();
            footprints.addAll(approxFootprint.keySet());
            footprints.addAll(preciseFootprint.keySet());
            stringer.key("footprint");
            stringer.object();
            for (String sec : footprints) {
                long approxAmt = approxFootprint.containsKey(sec) ?
                                 approxFootprint.get(sec) : 0;
                long preciseAmt = preciseFootprint.containsKey(sec) ?
                                  preciseFootprint.get(sec) : 0;

                stringer.key(sec);
                stringer.array();
                stringer.value(preciseAmt);
                stringer.value(approxAmt);
                stringer.endArray();
            }
            stringer.endObject();

            stringer.endObject();
        } catch (JSONException exc) {
            System.out.println("JSON writing failed!");
        }

        String out = stringer.toString();

        if (debug) {
            System.out.println(out);
            for (Map.Entry<String,AtomicInteger> entry : debugCounters.entrySet()) { //DEBUG
                System.out.println(String.format("%s - %d",
                        (String)entry.getKey(),
                        ((AtomicInteger)entry.getValue()).get()));
            }
        }
        
        // Write TOLOP related stats to file
        try {
           BufferedWriter bw = new BufferedWriter(new FileWriter("tolop_stats.txt"));
           bw.write(memOpInfo.toString());
           bw.close();
        }
        catch (IOException e) {
            System.err.println("Error while writing (new) EnerJ results to file...");
            //e.printStackTrace();
        }
        if (debug) { // Same as dump to file above
            System.out.println();
            memOpInfo.printMemOpCounters();
        }

        // Write classical EnerJ stats to file
        // TODO: remove this (and much else)
        // if (debug) {
        //     System.out.println(out);
        // }
        // try {
        //     FileWriter fstream = new FileWriter(JSON_OUTPUT_FILE_NAME);
        //     fstream.write(out);
        //     fstream.close();
        // } catch (IOException exc) {
        //     System.out.println("couldn't write stats file!");
        // }
    }

    /**
     * Object finalization calls. Log object lifetime, etc.
     * @param ref The collected object reference (was collected in deallocPoll).    
     */
    @Override
    public synchronized void endLifetime(PhantomReference<Object> ref) {
        ApproximationInformation infoObj;
        if (phantomInfo.containsKey(ref)) {
            infoObj = phantomInfo.get(ref);
        } else {
            // Already collected! Do nothing.
            return;
        }
        infoObj.collected = System.currentTimeMillis();

        // Log this lifetime at an object granularity.
        String memPart = infoObj.heap ? "heap" : "stack";
        long duration = infoObj.collected - infoObj.created;
        countFootprint(memPart + "-objects", infoObj.approx, duration);

        // Log memory usage in byte-seconds.
        countFootprint(memPart + "-bytes", false,
            infoObj.preciseSize * duration);
        countFootprint(memPart + "-bytes", true,
            infoObj.approxSize  * duration);

        if (debug) {
            System.out.println("EnerJ: object collected after " + duration +
                        " (" + (infoObj.approx ? "A" : "P") + ", " +
                        memPart + ")");
        }
    }

    /**
     * A thread that waits for finalizations. 
     */
    @SuppressWarnings("unchecked")
    private void deallocPoll() {
        while (true) {
            PhantomReference<Object> ref = null;
            try {
                ref = (PhantomReference<Object>) referenceQueue.remove();
            } catch (InterruptedException exc) {
                // Thread shut down.
                if (debug)
                    System.out.println("EnerJ: dealloc thread interrupted!");
                return;
            }

            endLifetime(ref);
        }
    }

    /**
     * Called on shutdown to collect all remaining objects.
     */
    private synchronized void cleanUpObjects() {
        if (debug)
            System.out.println("EnerJ: objects remaining at shutdown: " +
                               phantomInfo.size());
        for (Map.Entry<PhantomReference<Object>, ApproximationInformation> kv :
                                            phantomInfo.entrySet()) {
            endLifetime(kv.getKey());
        }
    }

    /**
     * Convert an arithmetic representation to its equivalent string.
     * @param op Enum representation of a arithmetic operator.
     * @return String of corresponding operator.
     */
    protected String opSymbol(ArithOperator op) {
        switch (op) {
        case PLUS: return "+";
        case MINUS: return "-";
        case MULTIPLY: return "*";
        case DIVIDE: return "/";
        case BITXOR: return "^";
        }
        return "(unknown)";
    }

    // Simulated operations.

    /**
     * This is a little incongruous, but this just counts some integer
     * operations that we don't want to instrument but are always done
     * precisely.
     */
    @Override
    public <T> T countLogicalOp(T value) {
        countOperation("INTlogic", false);
        return value;
    }

    @Override
    public Number binaryOp(Number lhs,
                           Number rhs,
                           ArithOperator op,
                           NumberKind nk,
                           boolean approx) {

        // DEFAULT
        countOperation(nk + opSymbol(op), approx);

        Number num = null;
        // Prevent divide-by-zero on approximate data.
        if (approx && op == ArithOperator.DIVIDE && rhs.equals(0)) {
            switch (nk) {
            case DOUBLE:
                num = Double.NaN;
                break;
            case FLOAT:
                num = Float.NaN;
                break;
            case LONG:
                num = Long.valueOf(0);
                break;
            case INT:
            case BYTE:
            case SHORT:
                num = Integer.valueOf(0);
                break;
            }
        }
        else {
            switch (nk) {
            case DOUBLE:
                switch (op) {
                case PLUS:
                    num = (lhs.doubleValue() + rhs.doubleValue());
                    break;
                case MINUS:
                    num = (lhs.doubleValue() - rhs.doubleValue());
                    break;
                case MULTIPLY:
                    num = (lhs.doubleValue() * rhs.doubleValue());
                    break;
                case DIVIDE:
                    num = (lhs.doubleValue() / rhs.doubleValue());
                    break;
                }
                break;
            case FLOAT:
                switch (op) {
                case PLUS:
                    num = (lhs.floatValue() + rhs.floatValue());
                    break;
                case MINUS:
                    num = (lhs.floatValue() - rhs.floatValue());
                    break;
                case MULTIPLY:
                    num = (lhs.floatValue() * rhs.floatValue());
                    break;
                case DIVIDE:
                    num = (lhs.floatValue() / rhs.floatValue());
                    break;
                }
                break;
            case LONG:
                switch (op) {
                case PLUS:
                    num = (lhs.longValue() + rhs.longValue());
                    break;
                case MINUS:
                    num = (lhs.longValue() - rhs.longValue());
                    break;
                case MULTIPLY:
                    num = (lhs.longValue() * rhs.longValue());
                    break;
                case DIVIDE:
                    num = (lhs.longValue() / rhs.longValue());
                    break;
                }
                break;
            case INT:
            case BYTE:
            case SHORT:
                switch (op) {
                case PLUS:
                    num = (lhs.intValue() + rhs.intValue());
                    break;
                case MINUS:
                    num = (lhs.intValue() - rhs.intValue());
                    break;
                case MULTIPLY:
                    num = (lhs.intValue() * rhs.intValue());
                    break;
                case DIVIDE:
                    num = (lhs.intValue() / rhs.intValue());
                    break;
                case BITXOR:
                    num = (lhs.intValue() ^ rhs.intValue());
                    break;
                }
                break;
            default:
                System.err.println("binary operation failed!");
                num = null;
            }
        }

        return num;
    }


    /**
     * Look for a field in a class hierarchy.
     * @param class_ The class
     * @param name Field name
     * @return Field representation of the class field.
     */
    protected Field getField(Class<?> class_, String name) {
    	// TODO #general: This can surely be re-used on some places
        while (class_ != null) {
            try {
                return class_.getDeclaredField(name);
            } catch (NoSuchFieldException x) {
                class_ = class_.getSuperclass();
            }
        }
        System.err.println("reflection error! field not found: " + name);
        return null;
    }

    /**
     * Simulated accesses
     */

    /**
     * Simulated operation of storing a value of some (specified) kind.
     * @param value The stored value
     * @param approx Whether the value is approximate or not
     * @param kind Any of three kinds of stored values
     * @return The stored (unaltered) value
     */
    @Override
    public <T> T storeValue(T value, boolean approx, MemKind kind) {
        countOperation("store" + kind, approx);
        return value;
    }

    /**
     * Simulated operation of loading a value of some (specified) kind.
     * @param value The loaded value
     * @param approx Whether the value is approximate or not
     * @param kind Any of three kinds of stored values
     * @return The loaded value
     */
    @Override
    public <T> T loadValue(T value, boolean approx, MemKind kind) {
        countOperation("load" + kind, approx);
        return value;
    }

    /**
     * Load a local value.
     * @param ref Value reference
     * @param approx Whether the value is approximate or not
     */
    @Override
    public <T> T loadLocal(Reference<T> ref, boolean approx) {
        memOpInfo.increaseLocalLoads(ALLOW_APPROXIMATE ? approx : false);
        T val = loadValue(ref.value, approx, MemKind.VARIABLE);
        return val;
    }

    /**
     * Load a value from an array.
     * @param array The array
     * @param index The corresponding index to be loaded
     * @param approx Whether the value is approximate or not
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T loadArray(Object array, int index, boolean approx) {
        memOpInfo.increaseArrayLoads(ALLOW_APPROXIMATE ? approx : false);
        String key = memoryKey(array, index);
        long tim = System.currentTimeMillis();
        loadFromMemory(key, tim);
        
        T val = loadValue((T) Array.get(array, index), approx, MemKind.ARRAYEL);

        return val;
    }

    /**
     * Load a class field.
     * @param obj The object to get the field from
     * @param fieldname Name of the field
     * @param approx Whether the value is approximate or not
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T loadField(Object obj, String fieldname, boolean approx) {
        memOpInfo.increaseFieldLoads(ALLOW_APPROXIMATE ? approx : false);
        T val;
        long tim = System.currentTimeMillis();
        Boolean evictionOccurred = false;
        try {
            // In static context, allow client to call this method with a Class
            // object instead of an instance.
            Class<?> class_;
            if (obj instanceof Class) {
                class_ = (Class<?>) obj;
                obj = null;
            } else {
                class_ = obj.getClass();
            }
            Field field = getField(class_, fieldname);
            field.setAccessible(true);

            //--TOLOP
            //--Load from simulated memory hierarchy
            //if (null != obj) {
                String key = memoryKey(obj, fieldname);
                evictionOccurred = loadFromMemory(key, tim);
            //}

            val = loadValue((T) field.get(obj), approx, MemKind.FIELD);


        } catch (IllegalArgumentException x) {
            System.err.println("reflection error!");
            val = null;
        } catch (IllegalAccessException x) {
            System.err.println("reflection error!");
            val = null;
        }

        return val;
    }

    /**
     * Store a local value.
     * @param ref Value reference, will be updated by rhs
     * @param approx Whether the value is approximate or not
     * @param rhs The value to be stored
     * @return The stored value
     */
    @Override
    public <T> T storeLocal(Reference<T> ref, boolean approx, T rhs) {
    	// TODO #general: If static - allow local errors after all?
        memOpInfo.increaseLocalStores(ALLOW_APPROXIMATE ? approx : false);
        ref.value = storeValue(rhs, approx, MemKind.VARIABLE);
        return ref.value;
    }

    /**
     * Store a value in some array.
     * @param array The array
     * @param index The corresponding index of where to store the new value
     * @param approx Whether the value is approximate or not
     * @param rhs The value to be stored
     * @return The stored value
     */
    @Override
    public <T> T storeArray(Object array, int index, boolean approx, T rhs) {
        memOpInfo.increaseArrayStores(ALLOW_APPROXIMATE ? approx : false);
        T val = storeValue(rhs, approx, MemKind.ARRAYEL);
        Array.set(array, index, val);

        //--TOLOP
        //--Store into simulated memory hierarchy
        String key = memoryKey(array, index);
        long tim = System.currentTimeMillis();
        storeIntoMemory(key, tim);

        return val;
    }

    /**
     * Store a class field.
     * @param obj The object into which the field should be stored 
     * @param fieldname Name of the field
     * @param approx Whether the value is approximate or not
     * @param rhs The value to be stored
     * @return The stored value
     */
    @Override
    public <T> T storeField(Object obj,
                            String fieldname,
                            boolean approx,
                            T rhs) {
        // T val = storeValue(rhs, approx, MemKind.FIELD);
        memOpInfo.increaseFieldStores(ALLOW_APPROXIMATE ? approx : false);
        Field field;
        try {
            // In static context, allow client to call this method with a Class
            // object instead of an instance.
            Class<?> class_;
            if (obj instanceof Class) {
                class_ = (Class<?>)obj;
                obj = null;
            } else {
                class_ = obj.getClass();
            }
            field = getField(class_, fieldname);
            field.setAccessible(true);

            // obj.fieldname = val;
            // field.set(obj, val);
            field.set(obj, rhs);

        }
        catch (IllegalArgumentException x) {
            System.out.println("reflection error: illegal argument");
            return null;
        }
        catch (IllegalAccessException x) {
            System.out.println("reflection error: illegal access");
            return null;
        }

        //--TOLOP
        //--Store into simulated memory hierarchy
        //--Insight! The code above (finding/setting fields) MUST be available,
        // as this method may be used when setting fields for the first time
        String key = memoryKey(obj, fieldname);
        // System.err.println("storeField: memoryKey: " + key); //DEBUG
        long tim = System.currentTimeMillis();
        // System.err.println("storeField: tim: " + tim); //DEBUG
        Boolean evictionOccurred = storeIntoMemory(key, tim);
        // System.err.println("storeField: evictionOccurred: " + evictionOccurred); //DEBUG
        
        //--NOISY
        // if (!evictionOccurred) {
            // memoryRefresh(key, val);
        // }

        // return val;
        return rhs;
    }

    /**
     * Compute var and rhs using op. 
     * @param var Left hand side value
     * @param op Mathematical operator 
     * @param rhs Right hand side value
     * @param returnOld true - return old value from val; false - return
     * computation from {var}{op}{rhs}.
     * @param nk Specifies the type of the variables
     * @param approx Whether the operation is approximate or not
     * @return If returnOld is true, return computed value; else, return
     * the old value from var
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Number> T assignopLocal(
        Reference<T> var,
        ArithOperator op,
        Number rhs,
        boolean returnOld,
        NumberKind nk,
        boolean approx
    ) {
        T tmp = loadLocal(var, approx);
        T res = (T) binaryOp(var.value, rhs, op, nk, approx);
        storeLocal(var, approx, res);
        if (returnOld)
            return tmp;
        else
            return res;
    }

    /**
     * Return the value interpreted as its defined corresponding type. 
     * @param num The number
     * @param nk The defined type (INT, FLOAT, etc)
     * @return The value (of type specified by nk)
     */
    private Number makeKind(Number num, NumberKind nk) {
        Number converted = null;
        switch (nk) {
        case DOUBLE:
            converted = num.doubleValue();
            break;
        case FLOAT:
            converted = num.floatValue();
            break;
        case LONG:
            converted = num.longValue();
            break;
        case INT:
            converted = num.intValue();
            break;
        case SHORT:
            converted = num.shortValue();
            break;
        case BYTE:
            converted = num.byteValue();
            break;
        default:
            assert false; // Not an elementary type
        }
        return converted;
    }

    /**
     * Compute the value of {array[index]} {op} {rhs}. Store result at {array[index]}. 
     * @param array Array to access 
     * @param index Array index to access 
     * @param op Arithmetical operation to use on array value
     * @param rhs Right hand side of arithmetic operation (array value is lhs)
     * @param returnOld If true, return old array value, else return result of operation
     * @param nk The defined type (INT, FLOAT, etc)
     * @param approx Whether the operation is approximate or not
     * @return If returnOld is true, return computed value; else, return
     * the old value from array[index]
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Number> T assignopArray(
        Object array,
        int index,
        ArithOperator op,
        Number rhs,
        boolean returnOld,
        NumberKind nk,
        boolean approx
    ) {
        T tmp = (T) loadArray(array, index, approx);
        T res = (T) binaryOp(tmp, rhs, op, nk, approx);
        storeArray(array, index, approx, (T) makeKind(res, nk));
        if (returnOld)
            return tmp;
        else
            return res;
    }

    /**
     * Compute the value of {array[index]} {op} {rhs}. Store result at corresponding field.
     * @param obj Object where the field resides
     * @param fieldname Name of the field
     * @param op Arithmetical operation to use on field value
     * @param rhs Right hand side of arithmetic operation (field value is lhs)
     * @param returnOld If true, return old field value, else return result of operation
     * @param nk The defined type (INT, FLOAT, etc)
     * @param approx Whether the operation is approximate or not
     * @return If returnOld is true, return computed value; else, return
     * the old value from the field
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Number> T assignopField(
        Object obj,
        String fieldname,
        ArithOperator op,
        Number rhs,
        boolean returnOld,
        NumberKind nk,
        boolean approx
    ) {
        T tmp = (T) loadField(obj, fieldname, approx);
        T res = (T) binaryOp(tmp, rhs, op, nk, approx);
        storeField(obj, fieldname, approx, (T) makeKind(res, nk));
        if (returnOld)
            return tmp;
        else
            return res;
    }
}
