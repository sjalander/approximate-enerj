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
	
	public MemoryOpInfo() {
		memoryOpCounters.put("totalMemOps", new AtomicInteger()); 
	    memoryOpCounters.put("approxLoads", new AtomicInteger());
	    memoryOpCounters.put("preciseLoads", new AtomicInteger());
	    memoryOpCounters.put("approxStores", new AtomicInteger());
	    memoryOpCounters.put("preciseStores", new AtomicInteger());
	    memoryOpCounters.put("approxHits", new AtomicInteger());
	    memoryOpCounters.put("preciseHits", new AtomicInteger());
	    memoryOpCounters.put("approxMisses", new AtomicInteger());
	    memoryOpCounters.put("preciseMisses", new AtomicInteger());
	    
	    // Syntax of "{A}{B}Evictions" -> An "A" cache line evicts a "B" cache line
	    memoryOpCounters.put("precisePreciseEvictions", new AtomicInteger());
	    memoryOpCounters.put("preciseApproxEvictions", new AtomicInteger());
	    memoryOpCounters.put("approxPreciseEvictions", new AtomicInteger());
	    memoryOpCounters.put("approxApproxEvictions", new AtomicInteger());
	    
	    // For timer values
	    memoryTimeCounters.put("totalTime", new AtomicLong());
	    memoryTimeCounters.put("minSramTime", new AtomicLong(Long.MAX_VALUE));
	    memoryTimeCounters.put("maxSramTime", new AtomicLong());
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
	
	// Had to be inventive of argument names here… *cough*
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
	
	public int getTotalEvictions() {
		int total = getEvictions(false, false)
				  + getEvictions(false, true)
				  + getEvictions(true, false)
				  + getEvictions(true, true);
		return total;
	}
	
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
	 * Increase the total time of how long data has resided in SRAM memory.
	 * @param time Increased time
	 * @return Previous time value
	 */
	public long increaseTotalSramTime(long time) {
		return memoryTimeCounters.get("totalTime").getAndAdd(time);
	}
	
	/**
	 * Get the shortest amount of time where data resided in SRAM memory. 
	 * @return Time value
	 */
	public double getMinSramTime() {
		return (double)memoryTimeCounters.get("minSramTime").get();
	}
	
	/**
	 * Compare time with the current SRAM time value. If time is the smaller
	 * value, then update current value with time. 
	 */
	public void compareAndSetMinSramTime(long time) {
		if (time < getMinSramTime())
			memoryTimeCounters.get("minSramTime").set(time);
	}
	
	/**
	 * Get the longest amount of time where data resided in SRAM memory. 
	 * @return Time value
	 */
	public double getMaxSramTime() {
		return (double)memoryTimeCounters.get("maxSramTime").get();
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
		
		sb.append("---Summary---\n");
		sb.append("Evictions: " + getTotalEvictions() + "\n");
		sb.append(String.format(Locale.UK, "Average time in SRAM: %1.2f",
				getAverageSramTime()));
		return sb.toString();
	}
}
