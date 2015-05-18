package enerj.rt;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import plume.WeakIdentityHashMap;

import java.util.List;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

class CreationInfo {
    public Object creator;
    public boolean approx;
    public int preciseSize;
    public int approxSize;
}

class ApproximationInformation {
    public long created;
    public List<Long> read;
    public List<Long> write;
    public long collected;
    public boolean approx;
    public boolean heap;
    public int preciseSize;
    public int approxSize;

    // to safe a bit of space we only store the creation time.
    ApproximationInformation(long t, boolean approx, boolean heap,
                             int preciseSize, int approxSize) {
        created = t;
        read = new LinkedList<Long>();
        write= new LinkedList<Long>();
        this.approx = approx;
        this.heap = heap;
        this.preciseSize = preciseSize;
        this.approxSize = approxSize;
    }
}

class PrecisionRuntimeTolop implements PrecisionRuntime {

    /********NOISY VARIABLES AND METHODS********/

    protected final String CONSTS_FILE = "enerjnoiseconsts.json";

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
    protected final int DISABLED = 0;

    private Map<String, Long> dataAges = new WeakHashMap<String, Long>();
    
    // Timing errors for arithmetic.
    private HashMap<Class<?>, Number> lastValues = new HashMap<Class<?>, Number>();
    

    // Error injection helpers.

    private int numBytes(Object value) {
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

    private <T> T bitError(T value, long invProb) {
        if (!isPrimitive(value))
            return value;

        long bits = toBits(value);
        int width = numBytes(value);

        // Inject errors.
        for (int bitpos = 0; bitpos < width * 8; ++bitpos) {
            long mask = 1 << bitpos;
            if ((long)(Math.random() * invProb) == 0) {
                // Bit error!
                bits = bits ^ mask;
            }
        }

        return (T) fromBits(bits, value);
    }

    private Number narrowMantissa(Number num, NumberKind nk) {
        if (nk == NumberKind.FLOAT) {

            if (MB_FLOAT_APPROX == DISABLED)
                return num;

            int bits = Float.floatToRawIntBits(num.floatValue());
            int mask = 0;
            for (int i = 0; i < MB_FLOAT_PRECISE - MB_FLOAT_APPROX; ++i) {
                mask |= (1 << i);
            }
            return Float.intBitsToFloat(bits & ~mask);

        } else if (nk == NumberKind.DOUBLE) {

            if (MB_DOUBLE_APPROX == DISABLED)
                return num;

            long bits = Double.doubleToRawLongBits(num.doubleValue());
            long mask = 0;
            for (int i = 0; i < MB_DOUBLE_PRECISE - MB_DOUBLE_APPROX; ++i) {
                mask |= (1L << i);
            }
            return Double.longBitsToDouble(bits & ~mask);
            // bits 51 and down

        } else {
            assert false;
            return null;
        }
    }

    private boolean isPrimitive(Object o) {
        return (
            o instanceof Number ||
            o instanceof Boolean ||
            o instanceof Character
        );
    }

    private void dramRefresh(String key, Object value) {
        if (isPrimitive(value)) {
            dataAges.put(key, System.currentTimeMillis());
        }
    }

    private <T> T dramAgedRead(String key, T value) {
        if (!isPrimitive(value) || INVPROB_DRAM_FLIP_PER_SECOND == DISABLED) {
            return value;
        }

        // How old is the data?
        long age;
        if (dataAges.containsKey(key)) {
            age = System.currentTimeMillis() - dataAges.get(key);
            if (age == 0) {
                return value;
            }
        } else {
            return value;
        }

        // Inject error.
        long invprob = INVPROB_DRAM_FLIP_PER_SECOND * 1000L / age;
        value = bitError(value, invprob);

        // Data is refreshed.
        dramRefresh(key, value);

        return value;
    }

    private String dramKey(Object obj, String field) {
        return System.identityHashCode(obj) + field;
    }

    private String dramKey(Object array, int index) {
        return "array" + System.identityHashCode(array) + "idx" + index;
    }

    private Number timingError(Number num) {
        long bits;
        if (Math.random()*100 < TIMING_ERROR_PROB_PERCENT) {
            switch (TIMING_ERROR_MODE) {
            case DISABLED:
                return num;

            case 1: // Single bit flip.
                bits = toBits(num);
                int errpos = (int)(Math.random() * numBytes(num) * 8);
                bits = bits ^ (1 << errpos);
                return (Number)fromBits(bits, num);

            case 2: // Random value.
                bits = 0;
                for (int i = 0; i < numBytes(num); ++i) {
                    byte b = (byte)(Math.random() * Byte.MAX_VALUE);
                    bits |= ((long)b) << (i*8);
                    if (Math.random() < 0.5)
                        bits |= 1L << ((i+1)*8-1); // Sign bit.
                }
                return (Number)fromBits(bits, num);

            case 3: // Last value.
                if (lastValues.containsKey(num.getClass()))
                    return lastValues.get(num.getClass());
                else
                    return (Number)fromBits(0, num);

            default:
                assert false;
                return null;
            }
        } else {
            return num;
        }
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
    private Map<Object, ApproximationInformation> info = new WeakIdentityHashMap<Object, ApproximationInformation>();

    // This "parallel" map is used just to receive object finalization events.
    // Phantom references can't be dereferenced, so they can't be used to look
    // up information. But there are the only way to truly know exactly when
    // an object is about to be deallocated. This map contains *all* objects,
    // even precise ones.
    private Map<PhantomReference<Object>, ApproximationInformation> phantomInfo =
        new HashMap<PhantomReference<Object>, ApproximationInformation>();
    private ReferenceQueue<Object> referenceQueue = new ReferenceQueue<Object>();

    long startup;

    private Map<String, Integer> preciseOpCounts = new HashMap<String, Integer>();
    private Map<String, Integer> approxOpCounts  = new HashMap<String, Integer>();
    private Map<String, Long> approxFootprint = new HashMap<String, Long>();
    private Map<String, Long> preciseFootprint = new HashMap<String, Long>();

    private static boolean debug = "true".equals(System.getenv("EnerJDebug"));

    @Override
    public PhantomReference<Object> setApproximate(
        Object o, boolean approx, boolean heap, int preciseSize, int approxSize
    ) {
        if (debug) {
            System.out.println("EnerJ: Add object " + System.identityHashCode(o) + " to system.");
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

    @Override
    public boolean isApproximate(Object o) {
        if (debug) {
            System.out.println("EnerJ: Determine whether \"" + (o!=null ? System.identityHashCode(o):"null") + "\" is approximate");
        }
        boolean approx;
        synchronized (this) {
            approx = info.containsKey(o);
        }
        return approx;
    }

    /**
     * Imported from constructor of PrecisionRuntimeNoisy
     */
    private void doNoisyConstructorThings() {
        System.err.println("Initializing noisy EnerJ runtime.");

        FileReader fr = null;
        try {
            fr = new FileReader(CONSTS_FILE);
        } catch (IOException exc) {
            System.err.println("   Constants file not found; using defaults.");
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

        System.err.println("   SRAM WF: " + INVPROB_SRAM_WRITE_FAILURE);
        System.err.println("   SRAM RU: " + INVPROB_SRAM_READ_UPSET);
        System.err.println("   float bits: " + MB_FLOAT_APPROX);
        System.err.println("   double bits: " + MB_DOUBLE_APPROX);
        System.err.println("   DRAM decay: " + INVPROB_DRAM_FLIP_PER_SECOND);
        System.err.println("   timing error mode: " + TIMING_ERROR_MODE);
        System.err.println("   timing error prob: " + TIMING_ERROR_PROB_PERCENT);
    }

    public PrecisionRuntimeTolop() {
        super();

        startup = System.currentTimeMillis();

        final Thread deallocPollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                deallocPoll();
            }
        });
        deallocPollThread.setDaemon(true); // Automatically shut down.
        deallocPollThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                cleanUpObjects();
                dumpCounts();
            }
        }));

        doNoisyConstructorThings();
    }

    /**
     * Mapping Thread IDs to stacks of CreationInfo objects.
     */
    Map<Long, Stack<CreationInfo>> creations = new HashMap<Long, Stack<CreationInfo>>();

    @Override
    public boolean beforeCreation(Object creator, boolean approx,
                                  int preciseSize, int approxSize) {
        if (debug) {
            System.out.println("EnerJ: before creator \"" + System.identityHashCode(creator)
                    + "\" creates new " + (approx ? "approximate" : "precise")
                    + " object");
        }
        CreationInfo c = new CreationInfo();
        c.creator = creator;
        c.approx = approx;
        c.preciseSize = preciseSize;
        c.approxSize = approxSize;

        long tid = Thread.currentThread().getId();

        Stack<CreationInfo> stack = creations.get(tid);
        if (stack==null) {
            stack = new Stack<CreationInfo>();
        }
        stack.push(c);
        creations.put(tid, stack);

        return true;
    }

    @Override
    public boolean enterConstructor(Object created) {
        if (debug) {
            System.out.println("EnerJ: enter constructor for object \"" + System.identityHashCode(created) + "\"");
        }
        Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());

        if (stack==null) {
            if (debug) {
                System.out.println("EnerJ: enter constructor for object \"" + System.identityHashCode(created) + "\" found a null stack.");
            }
            // probably instantiated from non-EnerJ code
            return false;
        }

        if (stack.size()<=0) {
            if (debug) {
                System.out.println("EnerJ: enter constructor for object \"" + System.identityHashCode(created) + "\" found an empty stack.");
            }
            // probably instantiated from non-EnerJ code
            return false;
        }

        CreationInfo c = stack.pop();

        // we cannot compare c.creator; we assume that there is no thread interleaving
        // between the call of beforeCreation and enterConstructor.
        // TODO: some methods should probably be synchronized, but I think this
        // wouldn't help against this particular problem.
        this.setApproximate(created, c.approx, true,
                            c.preciseSize, c.approxSize);

        return true;
    }

    @Override
    public boolean afterCreation(Object creator, Object created) {
        if (debug) {
            System.out.println("EnerJ: after creator \"" + System.identityHashCode(creator)
                    + "\" created new object \"" + System.identityHashCode(created) + "\"");
        }
        Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());
        // Could stack ever be null? I guess not, b/c "afterC" is only called, if "beforeC" was called earlier.

        if (stack.size()<=0) {
            if (debug) {
                System.out.println("EnerJ: after creator \"" + System.identityHashCode(creator)
                        + "\" created new object \"" + System.identityHashCode(created) + "\" found an empty stack.");
            }
            // no worries, the stack must have been emptied in enterConstructor
            return false;
        }

        CreationInfo c = stack.peek();

        if (c.creator == creator) {
            this.setApproximate(created, c.approx, true,
                                c.preciseSize, c.approxSize);
            if (c.approx) {
                stack.pop();
            }
        } else {
            if (debug) {
                System.out.println("EnerJ: after creator \"" + System.identityHashCode(creator)
                        + "\" created new object \"" + System.identityHashCode(created)
                        + "\" found mismatched creator \"" + c.creator + "\".");
            }
            // if the creators do not match, the entry was already removed.
        }

        return true;
    }

    @Override
    public <T> T wrappedNew(boolean before, T created, Object creator) {
        afterCreation(creator, created);
        return created;
    }

    @Override
    public <T> T newArray(T created, int dims, boolean approx,
                          int preciseElSize, int approxElSize) {
        int elems = 1;
        Object arr = created;
        for (int i = 0; i < dims; ++i) {
            elems *= Array.getLength(arr);
            if (Array.getLength(arr) == 0)
                break;
            if (i < dims - 1)
                arr = Array.get(arr, 0);
        }

        if (!approx) {
            preciseElSize += approxElSize;
            approxElSize = 0;
        }

        this.setApproximate(created, false, true,
                            preciseElSize*elems, approxElSize*elems);

        if (debug) {
            System.out.println("EnerJ: created array \"" +
                System.identityHashCode(created) +
                "\" with size " + elems);
        }

        return created;
    }

    /*
    @Override
    public boolean isApproximate(Object o, String field) {
        PrecisionInformation entry = info.get(o);
        return entry != null && entry.isApproximate(field);
    }

    @Override
    public PrecisionInformation createPrecisionInfo() {
        // alternatively, create the pi object in addObject and let the user
        // modify the values afterward
        return new PrecisionInformationDefault();
    }
    */


    // Counting infrastucture.
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

    private synchronized void countFootprint(String name, boolean approx,
                                             long amount) {
        Map<String, Long> map = null;
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
        }
        try {
            FileWriter fstream = new FileWriter("enerjstats.json");
            fstream.write(out);
            fstream.close();
        } catch (IOException exc) {
            System.out.println("couldn't write stats file!");
        }
    }

    // Object finalization calls.
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

    // A thread that waits for finalizations.
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

    // Called on shutdown to collect all remaining objects.
    private synchronized void cleanUpObjects() {
        if (debug)
            System.out.println("EnerJ: objects remaining at shutdown: " +
                               phantomInfo.size());
        for (Map.Entry<PhantomReference<Object>, ApproximationInformation> kv :
                                            phantomInfo.entrySet()) {
            endLifetime(kv.getKey());
        }
    }

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


    // This is a little incongruous, but this just counts some integer
    // operations that we don't want to instrument but are always done
    // precisely.
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
        
        // NOISY - part 1
        // Floating point width.
        if (approx && (nk == NumberKind.DOUBLE || nk == NumberKind.FLOAT)) {
            lhs = narrowMantissa(lhs, nk);
            rhs = narrowMantissa(rhs, nk);
        }

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

        // NOISY, part 2
        // Floating point width again.
        if (approx && (nk == NumberKind.DOUBLE || nk == NumberKind.FLOAT)) {
            num = narrowMantissa(num, nk);
        }

        // Timing errors on ALU.
        if (approx/* && !(nk == NumberKind.DOUBLE || nk == NumberKind.FLOAT)*/) {
            num = timingError(num);
            if (TIMING_ERROR_MODE == 3) // Last value mode.
                lastValues.put(num.getClass(), num);
        }

        return num;
    }


    // Look for a field in a class hierarchy.
    protected Field getField(Class<?> class_, String name) {
        while (class_ != null) {
            try {
                return class_.getDeclaredField(name);
            } catch (NoSuchFieldException x) {
                class_ = class_.getSuperclass();
            }
        }
        System.out.println("reflection error! field not found: " + name);
        return null;
    }

    /**
     * Simulated accesses
     */
    @Override
    public <T> T storeValue(T value, boolean approx, MemKind kind) {
        // NOISY
        if (kind == MemKind.VARIABLE && approx &&
                INVPROB_SRAM_WRITE_FAILURE != DISABLED) {
            // Approximate access to local variable. Inject SRAM write
            // failures.
            value = bitError(value, INVPROB_SRAM_WRITE_FAILURE);
        }

        // DEFAULT
        countOperation("store" + kind, approx);
        
        return value;
    }

    @Override
    public <T> T loadValue(T value, boolean approx, MemKind kind) {
        countOperation("load" + kind, approx);
        return value;
    }

    /**
     * SRAM read upsets
     */
    @Override
    public <T> T loadLocal(Reference<T> ref, boolean approx) {
        T val = loadValue(ref.value, approx, MemKind.VARIABLE);

        if (approx && INVPROB_SRAM_READ_UPSET != DISABLED) {
            // Approximate read from local variable. Inject SRAM read upsets.
            val = bitError(val, INVPROB_SRAM_READ_UPSET);
            ref.value = val;
        }
        return val;
    }

    @Override
    public <T> T loadArray(Object array, int index, boolean approx) {
        T val = loadValue((T) Array.get(array, index), approx, MemKind.ARRAYEL);

        // NOISY
        if (approx) {
            T aged = dramAgedRead(dramKey(array, index), val);
            if (aged != val) {
                val = aged;

                Array.set(array, index, aged);
            }
        }
        return val;
    }

    @Override
    public <T> T loadField(Object obj, String fieldname, boolean approx) {
        T val;
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

            // in = obj.fieldname;
            val = loadValue((T) field.get(obj), approx, MemKind.FIELD);

        } catch (IllegalArgumentException x) {
            System.err.println("reflection error!");
            val = null;
        } catch (IllegalAccessException x) {
            System.err.println("reflection error!");
            val = null;
        }

        // NOISY
        if (approx) {
            T aged = dramAgedRead(dramKey(obj, fieldname), val);
            if (aged != val) {
                val = aged;

                Class<?> class_;
                if (obj instanceof Class) {
                    class_ = (Class<?>) obj;
                    obj = null;
                } else {
                    class_ = obj.getClass();
                }
                Field field = getField(class_, fieldname);
                field.setAccessible(true);
                try {
                    field.set(obj, val);
                } catch (IllegalArgumentException x) {
                    System.err.println("reflection error!");
                    return null;
                } catch (IllegalAccessException x) {
                    System.err.println("reflection error!");
                    return null;
                }
            }
        }
        return val;
    }

    @Override
    public <T> T storeLocal(Reference<T> ref, boolean approx, T rhs) {
        ref.value = storeValue(rhs, approx, MemKind.VARIABLE);
        return ref.value;
    }

    @Override
    public <T> T storeArray(Object array, int index, boolean approx, T rhs) {
        T val = storeValue(rhs, approx, MemKind.ARRAYEL);
        Array.set(array, index, val);

        // NOISY
        dramRefresh(dramKey(array, index), val);

        return val;
    }

    @Override
    public <T> T storeField(Object obj,
                            String fieldname,
                            boolean approx,
                            T rhs) {
        T val = storeValue(rhs, approx, MemKind.FIELD);

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
            Field field = getField(class_, fieldname);
            field.setAccessible(true);

            // obj.fieldname = val;
            field.set(obj, val);

        } catch (IllegalArgumentException x) {
            System.out.println("reflection error: illegal argument");
            return null;
        } catch (IllegalAccessException x) {
            System.out.println("reflection error: illegal access");
            return null;
        }

        // NOISY
        dramRefresh(dramKey(obj, fieldname), val);

        return val;
    }

    // Fancier assignments.
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
            assert false;
        }
        return converted;
    }

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
