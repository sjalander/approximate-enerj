package enerj;

public class FieldInfoContainer {
    public  AnnotationType annotation;
    public  String fieldType;
    public  boolean isStatic;
    public  boolean isFinal;
    
    @Override
    public String toString() {
    	return annotation + " " + isStatic + " " + isFinal + " " + fieldType;
    }
}
