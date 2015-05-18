package enerj;

/**
 * Class to implement a general tuple structure. Used in e.g. addToClassMap
 * to pair class names together with its fields.
 */
public class MyTuple<X, Y> {
    public final X x;
    public final Y y;
    
    public MyTuple(X x, Y y) {
        this.x = x; 
        this.y = y;
    }
}
