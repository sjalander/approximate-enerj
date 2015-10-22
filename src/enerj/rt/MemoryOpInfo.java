package enerj.rt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class MemoryOpInfo {
	/**
	 * Counters for data results.
	 */
	private Map<String,AtomicInteger> memoryOpCounters
		= new HashMap<String,AtomicInteger>();
	private Map<String, AtomicLong> memoryTimeCounters
		= new HashMap<String, AtomicLong>();
	private Map<String, AtomicLong> memorySizeCounters
		= new HashMap<String, AtomicLong>();
	
	public MemoryOpInfo() {
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
	    memoryTimeCounters.put("totalTime", new AtomicLong());
	    memoryTimeCounters.put("approxTotalTime", new AtomicLong());
	    memoryTimeCounters.put("preciseTotalTime", new AtomicLong());
	    memoryTimeCounters.put("minSramTime", new AtomicLong(Long.MAX_VALUE));
	    memoryTimeCounters.put("approxMinSramTime", new AtomicLong(Long.MAX_VALUE));
	    memoryTimeCounters.put("preciseMinSramTime", new AtomicLong(Long.MAX_VALUE));
	    memoryTimeCounters.put("maxSramTime", new AtomicLong());
	    memoryTimeCounters.put("approxMaxSramTime", new AtomicLong());
	    memoryTimeCounters.put("preciseMaxSramTime", new AtomicLong());

	    // For counting loaded/stored memory size
		memorySizeCounters.put("storedApproxData", new AtomicLong());
	    memorySizeCounters.put("loadedApproxData", new AtomicLong());
	    memorySizeCounters.put("storedPreciseData", new AtomicLong());
	    memorySizeCounters.put("loadedPreciseData", new AtomicLong());
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
	public double getAverageSramTime() {
		return getTotalEvictions() == 0
			? 0 // If everything fitted in the cache
			: (double)memoryTimeCounters.get("totalTime").get() / (double)getTotalEvictions();
	}

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
            (approx ? "approxTotalTime" : "preciseTotalTime").get();
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
	public long increaseTotalSramTime(long time) {
		return memoryTimeCounters.get("totalTime").getAndAdd(time);
	}
    
	/**
	 * Increase the total time of how long data has resided in SRAM memory.
     * @param approx Whether or not the data is approximative
	 * @param time Increased time
	 * @return Previous time value
	 */
	public long increaseTotalSramTime(boolean approx, long time) {
		return memoryTimeCounters.get(approx ? "approxTotalTime" : "preciseTotalTime").getAndAdd(time);
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
		return (double)memoryTimeCounters.get(approx ? "approxMinSramTime" : "preciseMinSramTime").get();
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
		for (Map.Entry<String,AtomicInteger> entry : memoryOpCounters.entrySet()) {
			sb.append(String.format("%s - %d\n",
					(String)entry.getKey(),
					((AtomicInteger)entry.getValue()).get()));	
		}
		for (Map.Entry<String,AtomicLong> entry : memoryTimeCounters.entrySet()) {
			sb.append(String.format("%s - %d\n",
					(String)entry.getKey(),
					((AtomicLong)entry.getValue()).get()));	
		}
		for (Map.Entry<String,AtomicLong> entry : memorySizeCounters.entrySet()) {
			sb.append(String.format("%s - %d\n",
					(String)entry.getKey(),
					((AtomicLong)entry.getValue()).get()));	
		}
		
		sb.append("---Summary---\n");
		sb.append("Evictions: " + getTotalEvictions() + "\n");
		sb.append(String.format(Locale.UK, "Average time in SRAM: %1.2f\n",
				getAverageSramTime()));
		sb.append(String.format(Locale.UK, "Average time in SRAM (approx): %1.2f\n",
				getAverageSramTime(true)));
		sb.append(String.format(Locale.UK, "Average time in SRAM (precise): %1.2f",
				getAverageSramTime(false)));
		return sb.toString();
	}
}
