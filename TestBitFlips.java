
class TestBitFlips {

    private static int flipped = 0;

    private static double[] S2ErrorRateLookup = {24.0, 24.0, 7.2, 5.1, 4.15, 3.7, 3.4, 3.1,
        3.0, 2.9, 2.8, 2.7, 2.6, 2.5, 2.4, 2.3, 2.2, 2.1};
    private static double[] S3ErrorRateLookup = {7.5, 3.6, 2.9, 2.5, 2.25, 2.0, 1.9, 1.8, 1.7,
        1.5, 1.2, 1.1, 1.0, 0.9, 0.8, 0.75, 0.7, 0.65};

    public static boolean isFlipped(int pos) { 
        return ((flipped >> pos) & 1) != 0;
    }

    public static void setFlipped(boolean newStatus, int pos) { 
        if (newStatus)
            flipped |= 1 << pos;
        else
            flipped &= ~(1 << pos);
    }

    public static void setFlipped(int pos) {
        if (!isFlipped(pos))
            setFlipped(true, pos);
    }
    
    public static void main(String[] argv) {
        int input = 53645;  // 1101000110001101
        System.out.println("input: "+input);
        int verify = 49228; // 1100000001001100
        //int[] ages = {9, 2000, 8000, 8192000};
        int[] ages = {8192000};
        //int[] ages = {9};
        for (int age : ages) {
            //flipped = 47799; // 1011010110110101
            flipped = 0;
            System.out.println("Results: "+bitErrorPCM(input, age));
            System.out.println("Flipped str: "+Integer.toBinaryString(flipped));
        }
    }

    private static double log2(double num) {
        return Math.log(num)/0.6931471805599453; // Math.log(2);
    }

    private static boolean isPrimitive(Object o) {
        return (
            o instanceof Number ||
            o instanceof Boolean ||
            o instanceof Character
        );
    }

    private static <T> T bitErrorPCM(T value, long age) {
    	if (!isPrimitive(value))
            return value;

        //System.out.println("input values: value="+value+", age="+age);
        long bits = toBits(value);
        int width = numQytes(value);
        int index = (int)Math.round((log2((double)age/1000)))-1;
        if (index < 0) // All under 2000ms is -> index 0
            index = 0;
        //System.out.println("index: " + index);
        double S2ErrorRate = Math.pow(10, S2ErrorRateLookup[index]);
        double S3ErrorRate = Math.pow(10, S3ErrorRateLookup[index]); 
        //System.out.println("S2ErrorRate: "+S2ErrorRate );
        //System.out.println("S3ErrorRate: "+S3ErrorRate );

        for (int flipbitpos=0; flipbitpos < (width*8)>>1; flipbitpos++) { 
            //System.out.println("flipbitpos: "+flipbitpos);
            if (!isFlipped(flipbitpos)) {
                int valuebitpos = 2*flipbitpos;
                if (((bits >> valuebitpos) & 3) == 1) { // Cell is state S3
                    //System.out.println("  valuebitpos "+valuebitpos+": S3");
                    double randNum = Math.random();
                    //System.out.println("S3 randNum: "+ randNum);
                    //System.out.println("S3 mult result: "+(long)(randNum * S3ErrorRate));
                    if ((long)(randNum * S3ErrorRate) == 0) {
                        //System.out.println("S3 flip occurred");
                        bits ^= 1 << valuebitpos;
                        //System.out.println("  current value:"+fromBits(bits,value));
                        setFlipped(flipbitpos);
                    }
                }
                else if (((bits >> 2*flipbitpos) & 3) == 2) { // Cell is state S2
                    //System.out.println("valuebitpos "+valuebitpos+": S2");
                    double randNum = Math.random();
                    //System.out.println("S2 randNum: "+ randNum);
                    //System.out.println("S2 mult result: "+(long)(randNum * S3ErrorRate));
                    if ((long)(randNum * S2ErrorRate) == 0) {
                        //System.out.println("  S2 flip occurred");
                        bits ^= 1 << valuebitpos;
                        bits ^= 1 << (valuebitpos+1);
                        //System.out.println("  current value:"+fromBits(bits,value));
                        setFlipped(flipbitpos);
                    }
                }
            }
        }
        return (T) fromBits(bits, value);
    }

    private static Object fromBits(long bits, Object oldValue) {
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

    private static long toBits(Object value) {
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
    
}
