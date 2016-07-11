package enerj.rt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class RunInfo {
    /**
     * Keeps counters for runtime operations, e.g. '+'
     */
    private Map<String, AtomicInteger> approxOpCounts0  = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxOpCounts8  = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxOpCounts16 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxOpCounts24 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxOpCounts32 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> preciseOpCounts  = new HashMap<String, AtomicInteger>();

    private Map<String, AtomicInteger> approxErrorCounts0  = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxErrorCounts8  = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxErrorCounts16 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxErrorCounts24 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> approxErrorCounts32 = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicInteger> preciseErrorCounts  = new HashMap<String, AtomicInteger>();

    /**
     * Counters for data results.
     */
    private Map<String,AtomicInteger> memoryOpCounters
	= new HashMap<String,AtomicInteger>();
    private Map<String, AtomicLong> memoryTimeCounters
	= new HashMap<String, AtomicLong>();
    private Map<String, AtomicLong> memorySizeCounters
	= new HashMap<String, AtomicLong>();
	
    public RunInfo() {
        // General counters
	memoryOpCounters.put("totalMemOps", new AtomicInteger()); 
	memoryOpCounters.put("approxLoads", new AtomicInteger());
	memoryOpCounters.put("preciseLoads", new AtomicInteger());
	memoryOpCounters.put("approxStores", new AtomicInteger());
	memoryOpCounters.put("preciseStores", new AtomicInteger());
        
        // Counters for cache hits/misses
	memoryOpCounters.put("approxHits", new AtomicInteger());
	memoryOpCounters.put("preciseHits", new AtomicInteger());
	memoryOpCounters.put("approxMisses", new AtomicInteger());
	memoryOpCounters.put("preciseMisses", new AtomicInteger());

        // Counters for the three different memory operations
	memoryOpCounters.put("approxLocalLoads", new AtomicInteger());
	memoryOpCounters.put("approxArrayLoads", new AtomicInteger());
	memoryOpCounters.put("approxFieldLoads", new AtomicInteger());
	memoryOpCounters.put("approxLocalStores", new AtomicInteger());
	memoryOpCounters.put("approxArrayStores", new AtomicInteger());
	memoryOpCounters.put("approxFieldStores", new AtomicInteger());
	memoryOpCounters.put("preciseLocalLoads", new AtomicInteger());
	memoryOpCounters.put("preciseArrayLoads", new AtomicInteger());
	memoryOpCounters.put("preciseFieldLoads", new AtomicInteger());
	memoryOpCounters.put("preciseLocalStores", new AtomicInteger());
	memoryOpCounters.put("preciseArrayStores", new AtomicInteger());
	memoryOpCounters.put("preciseFieldStores", new AtomicInteger());
	    
	// Syntax of "{A}{B}Evictions" -> An "A" cache line evicts a "B" cache line
	memoryOpCounters.put("precisePreciseEvictions", new AtomicInteger());
	memoryOpCounters.put("preciseApproxEvictions", new AtomicInteger());
	memoryOpCounters.put("approxPreciseEvictions", new AtomicInteger());
	memoryOpCounters.put("approxApproxEvictions", new AtomicInteger());
	    
	// For timer values
	//memoryTimeCounters.put("totalTime", new AtomicLong());
	memoryTimeCounters.put("approxCumulativeTime", new AtomicLong());
	memoryTimeCounters.put("preciseCumulativeTime", new AtomicLong());
	memoryTimeCounters.put("minSramTime", new AtomicLong(Long.MAX_VALUE));
	memoryTimeCounters.put("approxMinSramTime", new AtomicLong(Long.MAX_VALUE));
	memoryTimeCounters.put("preciseMinSramTime", new AtomicLong(Long.MAX_VALUE));
	memoryTimeCounters.put("maxSramTime", new AtomicLong());
	memoryTimeCounters.put("approxMaxSramTime", new AtomicLong());
	memoryTimeCounters.put("preciseMaxSramTime", new AtomicLong());
	memoryTimeCounters.put("totalRunTime", new AtomicLong());

	// For counting loaded/stored memory size
	memorySizeCounters.put("storedApproxData", new AtomicLong());
	memorySizeCounters.put("loadedApproxData", new AtomicLong());
	memorySizeCounters.put("storedPreciseData", new AtomicLong());
	memorySizeCounters.put("loadedPreciseData", new AtomicLong());
    }
	
    /**
     * Counting infrastructure, keeps track number of operations 
     * @param name Name of the operation, namely what arithmetic operation took
     * place with what type
     * @param approx Whether operation is approximate or not
     */
    void countOperation(String name, boolean approx, int approximativeBits) {
        Map<String, AtomicInteger> map = null;
        if (approx)
	    switch(approximativeBits) {
	    case 0:
		map = approxOpCounts0;
		break;
	    case 8:
		map = approxOpCounts8;
		break;
	    case 16:
		map = approxOpCounts16;
		break;
	    case 24:
		map = approxOpCounts24;
		break;
	    default:
		map = approxOpCounts32;
		break;
	    }
        else
            map = preciseOpCounts;

        if (map.containsKey(name)) {
            map.get(name).incrementAndGet();
        } else {
            map.put(name, new AtomicInteger(1));
        }
    }

    /**
     * Counting infrastructure, keeps track number of errros 
     * @param name Name of the operation, namely what arithmetic operation took
     * place with what type
     * @param approx Whether operation is approximate or not
     */
    void countError(String name, boolean approx, int approximativeBits) {
        Map<String, AtomicInteger> map = null;
        if (approx)
	    switch(approximativeBits) {
	    case 0:
		map = approxErrorCounts0;
		break;
	    case 8:
		map = approxErrorCounts8;
		break;
	    case 16:
		map = approxErrorCounts16;
		break;
	    case 24:
		map = approxErrorCounts24;
		break;
	    default:
		map = approxErrorCounts32;
		break;
	    }
        else
            map = preciseOpCounts;

        if (map.containsKey(name)) {
            map.get(name).incrementAndGet();
        } else {
            map.put(name, new AtomicInteger(1));
        }
    }

    public int getTotalMemOps() {
	return memoryOpCounters.get("totalMemOps").get();
    }
	
    public int increaseTotalMemOps() {
	return memoryOpCounters.get("totalMemOps").incrementAndGet();
    }
	
    public int getLoads(boolean approx) {
	return memoryOpCounters.get(approx ? "approxLoads" : "preciseLoads").get();
    }
	
    public int increaseLoads(boolean approx) {
	return memoryOpCounters.get(approx ? "approxLoads" : "preciseLoads").incrementAndGet();
    }
	
    public int getStores(boolean approx) {
	return memoryOpCounters.get(approx ? "approxStores" : "preciseStores").get();
    }
	
    public int increaseStores(boolean approx) {
	return memoryOpCounters.get(approx ? "approxStores" : "preciseStores").incrementAndGet();
    }
	
    public int getHits(boolean approx) {
	return memoryOpCounters.get(approx ? "approxHits" : "preciseHits").get();
    }
	
    public int increaseHits(boolean approx) {
	return memoryOpCounters.get(approx ? "approxHits" : "preciseHits").incrementAndGet();
    }
	
    public int getMisses(boolean approx) {
	return memoryOpCounters.get(approx ? "approxMisses" : "preciseMisses").get();
    }
	
    public int increaseMisses(boolean approx) {
	return memoryOpCounters.get(approx ? "approxMisses" : "preciseMisses").incrementAndGet();
    }

    public int increaseLocalLoads(boolean approx) {
        return memoryOpCounters.get(approx ? "approxLocalLoads" : "preciseLocalLoads").incrementAndGet();
    }

    public int increaseArrayLoads(boolean approx) {
        return memoryOpCounters.get(approx ? "approxArrayLoads" : "preciseArrayLoads").incrementAndGet();
    }

    public int increaseFieldLoads(boolean approx) {
        return memoryOpCounters.get(approx ? "approxFieldLoads" : "preciseFieldLoads").incrementAndGet();
    }

    public int increaseLocalStores(boolean approx) {
        return memoryOpCounters.get(approx ? "approxLocalStores" : "preciseLocalStores").incrementAndGet();
    }

    public int increaseArrayStores(boolean approx) {
        return memoryOpCounters.get(approx ? "approxArrayStores" : "preciseArrayStores").incrementAndGet();
    }

    public int increaseFieldStores(boolean approx) {
        return memoryOpCounters.get(approx ? "approxFieldStores" : "preciseFieldStores").incrementAndGet();
    }

    // Had to be inventive of argument names here... *cough*
    /**
     * Get the number of evictions caused by a cache line of some approximation
     * type of another cache line of some type.
     * @param evicterIsApprox Whether the evicting cache line is approx or not
     * @param evicteeIsApprox Whether the evicted cache line is approx or not
     * @return Number of evicted cache lines
     */
    public int getEvictions(boolean evicterIsApprox, boolean evicteeIsApprox) {
	if (evicterIsApprox) {
	    if (evicteeIsApprox)
		return memoryOpCounters.get("approxApproxEvictions").get(); // 1 1
	    else
		return memoryOpCounters.get("approxPreciseEvictions").get(); // 1 0
	}
	else {
	    if (evicteeIsApprox)
		return memoryOpCounters.get("preciseApproxEvictions").get(); // 0 1
	    else
		return memoryOpCounters.get("precisePreciseEvictions").get(); // 0 0
	}
    }
	
    /**
     * Get the total number of evictions.
     * @return Total number of evictions
     */
    public int getTotalEvictions() {
	int total = getEvictions(false, false)
	    + getEvictions(false, true)
	    + getEvictions(true, false)
	    + getEvictions(true, true);
	return total;
    }
	
    /**
     * Increase the number of evictions caused by a cache line of some
     * approximation type of another cache line of some type.
     * @param evicterIsApprox Whether the evicting cache line is approx or not
     * @param evicteeIsApprox Whether the evicted cache line is approx or not
     * @return The current number of evicted cache lines, including the added
     * one
     */
    public int increaseEvictions(boolean evicterIsApprox, boolean evicteeIsApprox) {
	if (evicterIsApprox) {
	    if (evicteeIsApprox)
		return memoryOpCounters.get("approxApproxEvictions").incrementAndGet(); // 1 1
	    else
		return memoryOpCounters.get("approxPreciseEvictions").incrementAndGet(); // 1 0
	}
	else {
	    if (evicteeIsApprox)
		return memoryOpCounters.get("preciseApproxEvictions").incrementAndGet(); // 0 1
	    else
		return memoryOpCounters.get("precisePreciseEvictions").incrementAndGet(); // 0 0
	}
    }
	
    /**
     * Get average time of how long data has resided in SRAM memory.
     * @return Time value
     */
    //public double getAverageSramTime() {
	//return getTotalEvictions() == 0
	//    ? 0 // If everything fitted in the cache
	//    : (double)memoryTimeCounters.get("totalTime").get() / (double)getTotalEvictions();
    //}

    /**
     * Get average time of how long data has resided in SRAM memory.
     * @param approx Whether or not the data is approximative
     * @return Time value
     */
    public double getAverageSramTime(boolean approx) {
        if (getTotalEvictions() == 0) {
            return 0; // If everything fitted in the cache
        }
        double totalTime = (double)memoryTimeCounters.get
            (approx ? "approxCumulativeTime" : "preciseCumulativeTime").get();
        double numOfEvictions = (double)(
					 approx ? getEvictions(true, false) + getEvictions(true, true)
					 : getEvictions(false, false) + getEvictions(false, true)
					 );
        return totalTime / numOfEvictions;
    }
	
    /**
     * Increase the total time of how long data has resided in SRAM memory.
     * @param time Increased time
     * @return Previous time value
     */
    //public long increaseTotalSramTime(long time) {
	//return memoryTimeCounters.get("totalTime").getAndAdd(time);
    //}
    
    /**
     * Increase the total time of how long data has resided in SRAM memory.
     * @param approx Whether or not the data is approximative
     * @param time Increased time
     * @return Previous time value
     */
    public long increaseTotalSramTime(boolean approx, long time) {
        return memoryTimeCounters.get(
                approx ? "approxCumulativeTime" : "preciseCumulativeTime"
            ).getAndAdd(time);
    }
	
    /**
     * Get the shortest amount of time where data resided in SRAM memory. 
     * @return Time value
     */
    public double getMinSramTime() {
        return (double)memoryTimeCounters.get("minSramTime").get();
    }
	
    /**
     * Get the shortest amount of time where data resided in SRAM memory. 
     * @param approx Whether or not the data is approximative
     * @return Time value
     */
    public double getMinSramTime(boolean approx) {
        return (double)memoryTimeCounters.get(
            approx ? "approxMinSramTime" : "preciseMinSramTime").get();
    }
	
    /**
     * Compare time with the current SRAM time value. If time is the smaller
     * value, then update current value with time. 
     * @param time Cache line time stamp
     */
    public void compareAndSetMinSramTime(long time) {
	if (time < getMinSramTime())
	    memoryTimeCounters.get("minSramTime").set(time);
    }
	
    /**
     * Compare time with the current SRAM time value. If time is the smaller
     * value, then update current value with time. 
     * @param approx Whether or not the data is approximative
     * @param time Cache line time stamp
     */
    public void compareAndSetMinSramTime(boolean approx, long time) {
	if (time < getMinSramTime(approx))
	    memoryTimeCounters.get(approx ? "approxMinSramTime" : "preciseMinSramTime").set(time);
    }
	
    /**
     * Get the longest amount of time where data resided in SRAM memory. 
     * @return Time value
     */
    public double getMaxSramTime() {
	return (double)memoryTimeCounters.get("maxSramTime").get();
    }

    /**
     * Get the longest amount of time where data resided in SRAM memory. 
     * @param approx Whether or not the data is approximative
     * @return Time value
     */
    public double getMaxSramTime(boolean approx) {
	return (double)memoryTimeCounters.get(approx ? "approxMaxSramTime" : "preciseMaxSramTime").get();
    }	

    /**
     * Set the total runtime as the time from some start to some stop.
     * @param start Start time
     * @param stop Stop time
     */
    public void setTotalRuntime(long start, long stop) {
        memoryTimeCounters.get("totalRunTime").set(stop-start);
    }
    
    /**
     * Get the (set) total runtime; this must have been previously set in order
     * to work properly.
     * @return The (set) total program runtime.
     */
    public long getTotalRuntime() {
        return memoryTimeCounters.get("totalRunTime").get();
    }

    /**
     * Compare time with the current SRAM time value. If time is the larger
     * value, then update current value with time. 
     * @param time Time to compare with
     */
    public void compareAndSetMaxSramTime(long time) {
	if (time > getMaxSramTime())
	    memoryTimeCounters.get("maxSramTime").set(time);
    }

    /**
     * Compare time with the current SRAM time value. If time is the larger
     * value, then update current value with time. 
     * @param approx Whether or not the data is approximative
     * @param time Time to compare with
     */
    public void compareAndSetMaxSramTime(boolean approx, long time) {
	if (time > getMaxSramTime(approx))
	    memoryTimeCounters.get(approx ? "approxMaxSramTime" : "preciseMaxSramTime").set(time);
    }

    /**
     * Increase the total amount of stored qytes.
     * @param approx Whether or not the data is approximative
     * @param size Stored block size.
     */
    public long increaseStoredQytes(boolean approx, long size) {
	return memorySizeCounters.get(approx ? "storedApproxData" : "storedPreciseData").getAndAdd(size);
    }
	
    /**
     * Increase the total amount of loaded qytes.
     * @param approx Whether or not the data is approximative
     * @param size Loaded block size.
     */
    public long increaseLoadedQytes(boolean approx, long size) {
	return memorySizeCounters.get(approx ? "loadedApproxData" : "loadedPreciseData").getAndAdd(size);
    }

    /**
     * Print counter values for memory operations.
     */
    public void printMemOpCounters() {
	if (getTotalMemOps() == 0) // Don't print anything, as no other operation has been performed
	    return;
	System.out.println(toString());
    }
	
	
    @Override
    public String toString() {
	StringBuffer sb = new StringBuffer();

	sb.append("---Arithmetic operations---\n");
	sb.append("---Approx0---\n");
	SortedSet<String> keys = new TreeSet<String>(approxOpCounts0.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)approxOpCounts0.get(key)).get()));	
	}
	sb.append("---Approx8---\n");
	keys = new TreeSet<String>(approxOpCounts8.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)approxOpCounts8.get(key)).get()));	
	}
	sb.append("---Approx16---\n");
	keys = new TreeSet<String>(approxOpCounts16.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)approxOpCounts16.get(key)).get()));	
	}
	sb.append("---Approx24---\n");
	keys = new TreeSet<String>(approxOpCounts24.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)approxOpCounts24.get(key)).get()));	
	}
	sb.append("---Approx32---\n");
	keys = new TreeSet<String>(approxOpCounts32.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)approxOpCounts32.get(key)).get()));	
	}
	float hitRate;
	if (approxOpCounts32.get("CacheTotal") != null) {
	    hitRate = (float)(approxOpCounts32.get("Cache-Hit").get())/(float)(approxOpCounts32.get("CacheTotal").get()); // * 100;
	    sb.append(String.format("%-25s%10f\n",
				    "HitRate",
				    hitRate));
	}

	sb.append("---Precise---\n");
	keys = new TreeSet<String>(preciseOpCounts.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)preciseOpCounts.get(key)).get()));	
	}
	if (preciseOpCounts.get("CacheTotal") != null) {
	    hitRate = (float)(preciseOpCounts.get("Cache-Hit").get())/(float)(preciseOpCounts.get("CacheTotal").get()); // * 100;
	    sb.append(String.format("%-25s%10f\n",
				    "HitRate",
				    hitRate));
	}

	sb.append("---Summary---\n");
	Map<String, Integer> summary  = new HashMap<String, Integer>();
	Map<String, Integer> approxSummary  = new HashMap<String, Integer>();

	List<String> list = new ArrayList<String>();
	list.add("RFTotal");
	list.add("Cache-Hit");
	list.add("Cache-Miss");
	list.add("Cache-Miss-Cold");
	list.add("CacheLoad");
	list.add("CacheStore");
	list.add("CacheTotal");
	list.add("OpsINT+");
	list.add("OpsINT-");
	list.add("OpsTotal");
	list.add("OpsTotal+/-");
	for (String key : list) {
	    int value = 0;
	    if (approxOpCounts0.containsKey(key))
		value +=approxOpCounts0.get(key).get();
	    if (approxOpCounts8.containsKey(key))
		value += approxOpCounts8.get(key).get();
	    if (approxOpCounts16.containsKey(key))
		value +=approxOpCounts16.get(key).get();
	    if (approxOpCounts24.containsKey(key))
		value += approxOpCounts24.get(key).get();
	    if (approxOpCounts32.containsKey(key))
		value +=approxOpCounts32.get(key).get();
	    // Store the sum of all approximate operations
	    approxSummary.put(key, new Integer(value));
	    if (preciseOpCounts.containsKey(key))
		value += preciseOpCounts.get(key).get();

	    summary.put(key, new Integer(value));
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    value));
	}

	hitRate = (float)summary.get("Cache-Hit")/(float)summary.get("CacheTotal"); // * 100;
	sb.append(String.format("%-25s%10f\n",
				"HitRate",
				hitRate));

	float opsRate = ((float)summary.get("OpsINT+")+(float)summary.get("OpsINT-"))/(float)summary.get("OpsTotal"); //* 100;
	sb.append(String.format("%-25s%10f\n",
				"INT+/- rate",
				opsRate));

	float approxOpsRate =(float)approxSummary.get("OpsTotal+/-")/(float)summary.get("OpsTotal"); // * 100;
	sb.append(String.format("%-25s%10f\n",
				"ApproxOps rate",
				approxOpsRate));

	float approxRFRate =(float)approxSummary.get("RFTotal")/(float)summary.get("RFTotal"); // * 100;
	sb.append(String.format("%-25s%10f\n",
				"ApproxRF rate",
				approxRFRate));

	float approxCacheRate =(float)approxSummary.get("CacheTotal")/(float)summary.get("CacheTotal"); // * 100;
	sb.append(String.format("%-25s%10f\n",
				"ApproxCache rate",
				approxCacheRate));

	sb.append("---Energy---\n");

	float approxCacheEnergy = approxCacheRate * (float)0.64; 
	sb.append(String.format("%-25s%10f\n",
				"ApproxCache ",
				approxCacheEnergy));

	float preciseCacheEnergy = ((float)1.0 - approxCacheRate) * (float)1.04;
	sb.append(String.format("%-25s%10f\n",
				"PreciseCache ",
				preciseCacheEnergy));

	float totalCacheEnergy = approxCacheEnergy + preciseCacheEnergy;
	sb.append(String.format("%-25s%10f\n",
				"TotalCache ",
				totalCacheEnergy));


	float approxRFEnergy = approxRFRate * (float)0.64; 
	sb.append(String.format("%-25s%10f\n",
				"ApproxRF ",
				approxRFEnergy));

	float preciseRFEnergy = ((float)1.0 - approxRFRate) * (float)1.04;
	sb.append(String.format("%-25s%10f\n",
				"PreciseRF ",
				preciseRFEnergy));

	float totalRFEnergy = approxRFEnergy + preciseRFEnergy;
	sb.append(String.format("%-25s%10f\n",
				"TotalRF ",
				totalRFEnergy));

	float approxOpsEnergy = approxOpsRate * (float)0.62; 
	sb.append(String.format("%-25s%10f\n",
				"ApproxOps ",
				approxOpsEnergy));

	float preciseOpsEnergy = ((float)1.0 - approxOpsRate) * (float)1.0;
	sb.append(String.format("%-25s%10f\n",
				"PreciseOps ",
				preciseOpsEnergy));

	float totalOpsEnergy = approxOpsEnergy + preciseOpsEnergy;
	sb.append(String.format("%-25s%10f\n",
				"TotalOps ",
				totalOpsEnergy));

	float approxMiss = 0;
	if (approxSummary.containsKey("Cache-Miss"))
	    approxMiss = (float)approxSummary.get("Cache-Miss");
	if (approxSummary.containsKey("Cache-Miss-Cold"))
	    approxMiss += (float)approxSummary.get("Cache-Miss-Cold");
	float preciseMiss = 0;
	if (preciseOpCounts.containsKey("Cache-Miss"))
	    preciseMiss = (float)preciseOpCounts.get("Cache-Miss").get();
	if (preciseOpCounts.containsKey("Cache-Miss-Cold"))
	    preciseMiss += (float)preciseOpCounts.get("Cache-Miss-Cold").get();
	float totalMiss   = 0;
	if (summary.containsKey("Cache-Miss"))
	    totalMiss = (float)summary.get("Cache-Miss");
	if (summary.containsKey("Cache-Miss-Cold"))
	    totalMiss += (float)summary.get("Cache-Miss-Cold");
	float approxMemEnergy  = approxMiss/2/totalMiss;
	float preciseMemEnergy = preciseMiss/totalMiss;
	sb.append(String.format("%-25s%10f\n",
				"ApproxMem ",
				approxMemEnergy));
	sb.append(String.format("%-25s%10f\n",
				"PreciseMem ",
				preciseMemEnergy));
	sb.append(String.format("%-25s%10f\n",
				"TotalMem ",
				approxMemEnergy + preciseMemEnergy));


	/*
	sb.append("---Memory operations---\n");
	keys = new TreeSet<String>(memoryOpCounters.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicInteger)memoryOpCounters.get(key)).get()));	
	}

        keys = new TreeSet<String>(memoryTimeCounters.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicLong)memoryTimeCounters.get(key)).get()));	
	}


	keys = new TreeSet<String>(memorySizeCounters.keySet());
	for (String key : keys) { 
	    sb.append(String.format("%-25s%10d\n",
				    key,
				    ((AtomicLong)memorySizeCounters.get(key)).get()));	
	}

	sb.append("---Memory Summary---\n");
	sb.append("Evictions: " + getTotalEvictions() + "\n");
	//sb.append(String.format(Locale.UK, "Average time in SRAM: %1.2f\n",
	//            getAverageSramTime()));
	sb.append(String.format(Locale.UK, "Average time in SRAM (approx): %1.2f\n",
				getAverageSramTime(true)));
	sb.append(String.format(Locale.UK, "Average time in SRAM (precise): %1.2f\n",
				getAverageSramTime(false)));
	*/
	sb.append("---Errors---\n");
	sb.append("---Approx0---\n");
	for (Map.Entry<String,AtomicInteger> entry : approxErrorCounts0.entrySet()) {
	    sb.append(String.format("%-25s%10d\n",
				    (String)entry.getKey(),
				    ((AtomicInteger)entry.getValue()).get()));
	}
	sb.append("---Approx8---\n");
	for (Map.Entry<String,AtomicInteger> entry : approxErrorCounts8.entrySet()) {
	    sb.append(String.format("%-25s%10d\n",
				    (String)entry.getKey(),
				    ((AtomicInteger)entry.getValue()).get()));
	}  
	sb.append("---Approx16---\n");
	for (Map.Entry<String,AtomicInteger> entry : approxErrorCounts16.entrySet()) {
	    sb.append(String.format("%-25s%10d\n",
				    (String)entry.getKey(),
				    ((AtomicInteger)entry.getValue()).get()));
	}
	sb.append("---Approx24---\n");
	for (Map.Entry<String,AtomicInteger> entry : approxErrorCounts24.entrySet()) {
	    sb.append(String.format("%-25s%10d\n",
				    (String)entry.getKey(),
				    ((AtomicInteger)entry.getValue()).get()));
	}
	sb.append("---Approx32---\n");
	for (Map.Entry<String,AtomicInteger> entry : approxErrorCounts32.entrySet()) {
	    sb.append(String.format("%-25s%10d\n",
				    (String)entry.getKey(),
				    ((AtomicInteger)entry.getValue()).get()));
	}

	return sb.toString();
    }
}
