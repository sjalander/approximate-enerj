package enerj;

import java.lang.annotation.Annotation;
import java.lang.StringBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import checkers.basetype.BaseTypeVisitor;
import checkers.quals.TypeQualifiers;
import checkers.runtime.InstrumentingChecker;
import checkers.runtime.instrument.InstrumentingTranslator;
import checkers.source.SupportedLintOptions;
import checkers.types.*;
import checkers.types.AnnotatedTypeMirror.AnnotatedDeclaredType;
import checkers.types.AnnotatedTypeMirror.AnnotatedExecutableType;
import checkers.util.AnnotationUtils;
import checkers.util.ElementUtils;
import checkers.util.GraphQualifierHierarchy;

// Imports for master thesis {koltrast} //
import checkers.util.TreeUtils; // For extracting data about fields
import org.json.JSONObject;     // For creating JSON formatted data strings
import org.json.JSONStringer;   // For creating JSON formatted data strings
import org.json.JSONException;  // For creating JSON formatted data strings
import java.io.FileWriter;      // For writing extracted data to file
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;     // For writing extracted data to file
import java.io.File;
// ------------------------------------ //

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;

import enerj.instrument.MethodBindingTranslator;
import enerj.instrument.RuntimePrecisionTranslator;
import enerj.instrument.SimulationTranslator;
import enerj.instrument.ConstructorTranslator;
import enerj.lang.*;
import enerj.rt.Reference;

/**
 * The precision type checker.
 */
@TypeQualifiers({Approx.class, Precise.class, Top.class, Context.class})
@SupportedLintOptions( { PrecisionChecker.STRELAXED,
	PrecisionChecker.MBSTATIC, PrecisionChecker.MBDYNAMIC,
	PrecisionChecker.SIMULATION } )
/* A note about how to pass these options:
 * Do not use:
 *   -Alint=strelaxed -Alint=mbdynamic
 * but instead use:
 *   -Alint=strelaxed,mbdynamic
 * You will not get a warning about this...
 */
/*
 * Hallelujah!
 * 1) https://deors.wordpress.com/2011/10/08/annotation-processors/
 * 2) http://types.cs.washington.edu/checker-framework/current/api/
 * ...and the fact that "enerjc" adds its custom Xbootclasspath, processorpath,
 * etc, explains it all!
 *#/gustaf
 */
public class PrecisionChecker extends InstrumentingChecker {
	// Subtyping lint options
	// We currently only have one option, STRELAXED.
	// If the option is not present, we use the stricter subtyping
	// hierarchy.
	public static final boolean STRELAXED_DEFAULT = false;
	public static final String STRELAXED = "strelaxed";

	// Method binding lint options
	// We have two options: MBSTATIC and MBDYNAMIC
	// If neither option is given, we do not modify method calls
	public static final boolean MBSTATIC_DEFAULT = false;
	public static final String MBSTATIC = "mbstatic";

	public static final boolean MBDYNAMIC_DEFAULT = false;
	public static final String MBDYNAMIC = "mbdynamic";

	// Whether to simulate the approximate execution of the program
	public static final boolean SIMULATION_DEFAULT = false;
	public static final String SIMULATION = "simulation";


	// The method name post-fixes that are used for approximate/precise methods
	public static final String MB_APPROX_POST = "_APPROX";
	public static final String MB_PRECISE_POST = "_PRECISE";

    // File name to save gathered data to
    public static final String JSON_OUTPUT_FILE_NAME = "object_field_info.json";

    public AnnotationMirror APPROX, PRECISE, TOP, CONTEXT;


    @Override
    public void initChecker(ProcessingEnvironment env) {
        AnnotationUtils annoFactory = AnnotationUtils.getInstance(env);
        APPROX = annoFactory.fromClass(Approx.class);
        PRECISE = annoFactory.fromClass(Precise.class);
        TOP = annoFactory.fromClass(Top.class);
        CONTEXT = annoFactory.fromClass(Context.class);

        super.initChecker(env);
    }

    /**
     * Returning null here turns off instrumentation in the superclass.
     */
    @Override
    public InstrumentingTranslator<PrecisionChecker> getTranslator(TreePath path) {
        return null;
    }

    // Hook to run tree translators (AST transformation step).
    @Override
    public void typeProcess(TypeElement e, TreePath p) {
        JCTree tree = (JCTree) p.getCompilationUnit(); // or maybe p.getLeaf()?

        if (debug()) {
            System.out.println("Translating from:");
            System.out.println(tree);
        }

		// first: determine what method to call
		if (getLintOption(PrecisionChecker.MBSTATIC, PrecisionChecker.MBSTATIC_DEFAULT)
			|| getLintOption(PrecisionChecker.MBDYNAMIC, PrecisionChecker.MBDYNAMIC_DEFAULT)) {
			tree.accept(new MethodBindingTranslator(this, processingEnv, p));
		}

        // Run the checker next and ensure everything worked out.
        super.typeProcess(e, p);

		if (getLintOption(PrecisionChecker.MBSTATIC, PrecisionChecker.MBSTATIC_DEFAULT)
				|| getLintOption(PrecisionChecker.MBDYNAMIC, PrecisionChecker.MBDYNAMIC_DEFAULT)
				|| getLintOption(PrecisionChecker.SIMULATION, PrecisionChecker.SIMULATION_DEFAULT)) {

			// then add instrumentation for bookkeeping
			// TODO: do we need the runtime system in the MBSTATIC case? Maybe for endorsements.
			tree.accept(new RuntimePrecisionTranslator(this, processingEnv, p));

			// finally look what to simulate
			if (getLintOption(PrecisionChecker.SIMULATION, PrecisionChecker.SIMULATION_DEFAULT)) {
				tree.accept(new SimulationTranslator(this, processingEnv, p));
                // tree.accept(new ConstructorTranslator(this, processingEnv, p));
			}
		}

        if (debug()) {
            System.out.println("Translated to:");
            System.out.println(tree);
        }
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        Set<Class<? extends Annotation>> typeQualifiers
            = new HashSet<Class<? extends Annotation>>();

        typeQualifiers.add(Precise.class);
        typeQualifiers.add(Approx.class);
        typeQualifiers.add(Context.class);

        // Always allow Top as top modifier, regardless of subtyping hierarchy
		// if(!getLintOption(STRELAXED, STRELAXED_DEFAULT)) {
        typeQualifiers.add(Top.class);

        return Collections.unmodifiableSet(typeQualifiers);
    }

    @Override
    protected QualifierHierarchy createQualifierHierarchy() {
        AnnotationUtils annoFactory = AnnotationUtils.getInstance(env);

        GraphQualifierHierarchy.GraphFactory factory = new GraphQualifierHierarchy.GraphFactory(this);

        AnnotationMirror typeQualifierAnno, superAnno;

		if(getLintOption(STRELAXED, STRELAXED_DEFAULT)) {
			typeQualifierAnno= annoFactory.fromClass(Precise.class);
			superAnno = annoFactory.fromClass(Approx.class);
			factory.addSubtype(typeQualifierAnno, superAnno);

			typeQualifierAnno= annoFactory.fromClass(Context.class);
			factory.addSubtype(typeQualifierAnno, superAnno);

			// To allow one annotation for classes like Endorsements, we still
			// add the Top qualifier.
			typeQualifierAnno= annoFactory.fromClass(Top.class);
			factory.addSubtype(superAnno, typeQualifierAnno);
		} else {
			typeQualifierAnno= annoFactory.fromClass(Precise.class);
			superAnno = annoFactory.fromClass(Top.class);
			factory.addSubtype(typeQualifierAnno, superAnno);

			typeQualifierAnno= annoFactory.fromClass(Approx.class);
			factory.addSubtype(typeQualifierAnno, superAnno);

			typeQualifierAnno= annoFactory.fromClass(Context.class);
			factory.addSubtype(typeQualifierAnno, superAnno);
		}

        QualifierHierarchy hierarchy = factory.build();
        if (hierarchy.getTypeQualifiers().size() < 2) {
            throw new IllegalStateException("Invalid qualifier hierarchy: hierarchy requires at least two annotations: " + hierarchy.getTypeQualifiers());
        }
        return hierarchy;
    }

    // Removed (moved, really) in Checker Framework 1.3.0, but I'm leaving this
    // here for earlier versions.
    public boolean isValidUse(AnnotatedDeclaredType declarationType,
            AnnotatedDeclaredType useType) {
		// The checker calls this method to compare the annotation used in a
		// type to the modifier it adds to the class declaration. As our default
		// modifier is Precise, this results in an error when Approx is used
    	// as type annotation. Just ignore this check here and do them manually
    	// in the visitor.
    	return true;
    }

    /**
     * For primitive types we allow an assignment from precise types to
     * approximate types.
     * In the other cases, follow the specified hierarchy in the qualifiers.
     */
    @Override
    public boolean isSubtype(AnnotatedTypeMirror sub, AnnotatedTypeMirror sup) {
    	// System.out.println("Call of isSubtype; sub: " + sub + ", sup: " + sup);

    	// Null is always a subtype of any reference type.
    	if (sub.getKind() == TypeKind.NULL &&
    			(sup.getKind() == TypeKind.DECLARED || sup.getKind() == TypeKind.TYPEVAR)) {
    		return true;
    	}

    	// TODO: I think this special case is only needed for the strict subtyping
    	// option. But it also shouldn't break the relaxed subtyping hierarchy.
    	if( sub.getUnderlyingType().getKind().isPrimitive() &&
    		sup.getUnderlyingType().getKind().isPrimitive() ) {
    		if ( sup.getAnnotations().contains(APPROX) ||
    			 sup.getAnnotations().contains(TOP) ||
    			 (sup.getAnnotations().contains(CONTEXT) &&
    			  sub.getAnnotations().contains(PRECISE)) ||
    			 sup.getAnnotations().equals(sub.getAnnotations())){
    			return true;
    		} else {
    			return false;
    		}
    	}
    	return super.isSubtype(sub, sup);
    }

	/**
	 * Determine whether the two given methods are substitutable, that is, every
	 * possible call of origexe will also succeed if we substitute newexe for
	 * it.
	 *
	 * @param origexe The original method.
	 * @param newexe The new method that we want to substitute.
	 * @return True, iff we can safely substitute the method.
	 */
    public boolean isCompatible(AnnotatedExecutableType origexe, AnnotatedExecutableType newexe) {
    	if (origexe.getParameterTypes().size() != newexe.getParameterTypes().size()) {
    		return false;
    	}

    	/* TODO: when depending on the context, this is not good... should we always
    	 * check this? Is this only a problem with strict subtyping?
    	if (!isSubtype( origexe.getReturnType(), newexe.getReturnType() ) ) {
    		return false;
    	}
    	*/

    	List<AnnotatedTypeMirror> origparams = origexe.getParameterTypes();
    	List<AnnotatedTypeMirror> newparams = newexe.getParameterTypes();

    	for(int i=0; i<origparams.size(); ++i) {
    		if(!isSubtype(origparams.get(i), newparams.get(i))) {
    			return false;
    		}
    	}

    	// TODO: type parameter, exceptions?
		return true;
	}

	/**
	 * Explicitly construct components that weren't being automatically
	 * detected by introspection for some reason. (Seems to be necessary
     * on at least some Mac OS X systems.)
	 */
	@Override
	public AnnotatedTypeFactory createFactory(CompilationUnitTree root) {
	    return new PrecisionAnnotatedTypeFactory(this, root);
	}

	@Override
	protected BaseTypeVisitor<?> createSourceVisitor(CompilationUnitTree root) {
	    return new PrecisionVisitor(this, root);
	}

	/**
	 * Determines whether a given declared type (i.e., class) is approximable
	 * (has an @Approximable annotation) or whether the whole package is approximable.
	 */
	public static boolean isApproximable(DeclaredType type) {
		Element tyelem = type.asElement();
		Approximable ann = tyelem.getAnnotation(Approximable.class);
	    if (ann != null) {
	    	return true;
	    }
	    tyelem = ElementUtils.enclosingPackage(tyelem);
	    if (tyelem!=null) {
	    	ann = tyelem.getAnnotation(Approximable.class);
		    if (ann != null) {
		    	return true;
		    }
	    }
	    return false;
	}


	/**** Object size calculation. ****/

	private static final int POINTER_SIZE = 8; // on 64-bit VM
	private static final int LINE_SIZE = 64; // x86

	/**
     * Get the size of a Reference (local variable) at runtime.
     * @param ref "Reference object" to the object that one wants to know the size of
     * @return [Precise size, Approximative size] array
     */
	public static <T> int[] referenceSizes(Reference<T> ref) {
	    int preciseSize = 0;
	    int approxSize = 0;

        if (ref.primitive) {
            int size = 0;
            if (ref.value instanceof Byte) size = 1;
    	    else if (ref.value instanceof Short) size = 2;
    	    else if (ref.value instanceof Integer) size = 4;
    	    else if (ref.value instanceof Long) size = 8;
    	    else if (ref.value instanceof Float) size = 4;
    	    else if (ref.value instanceof Double) size = 8;
    	    else if (ref.value instanceof Character) size = 2;
    	    else if (ref.value instanceof Boolean) size = 1; // not defined
    	    else assert false;

    	    if (ref.approx)
                approxSize = size;
            else
                preciseSize = size;

        } else { // Object or array type.
            preciseSize = POINTER_SIZE;
        }
            return new int[] {preciseSize, approxSize};
	}

	/** Get the size of a particular static type
     * @param type Type representation
     * @param apprCtx If this is true and the annotation is "context", then the
     * data is approximative
     * @param checker Checker representation of (among others) EnerJ annotations
     * @return Tuple [preciseSize, approxSize] of the represented type
     */
	public static int[] typeSizes(AnnotatedTypeMirror type, boolean apprCtx,
                                  PrecisionChecker checker) {
        int preciseSize = 0;
	    int approxSize = 0;

	    if (type.getKind() == TypeKind.DECLARED) {
            // References are always precise.
            preciseSize += POINTER_SIZE;
        } else {

            int size = 0;
            switch (type.getKind()) {
    	    case ARRAY: size = 0; break; // FIXME deal with arrays!
    	    case BOOLEAN: size = 1; break; // not defined
    	    case BYTE: size = 1; break;
    	    case CHAR: size = 2; break;
    	    case DOUBLE: size = 8; break;
    	    case FLOAT: size = 4; break;
    	    case INT: size = 4; break;
    	    case LONG: size = 8; break;
    	    case SHORT: size = 2; break;
    	    default: assert false;
    	    }
            
            if (type.hasEffectiveAnnotation(checker.APPROX) ||
                    (apprCtx && type.hasEffectiveAnnotation(checker.CONTEXT)))
	            approxSize += size;
	        else
	            preciseSize += size;
        }

        return new int[]{preciseSize, approxSize};
	}

    private static String annotationMap(AnnotationType annotation) {
        switch (annotation) {
        case Approx:
            return "Approx";
        case Context:
            return "Context";
        case Precise:
            return "Precise";
        default:
            assert false;
            return null;
        }
    }

    /**
     * Write the collected object, its fields, etc to the json file.
     * @param objectName Name of the collected object about to be written
     */
    private static void writeToJSONFile(String objectName) {
        // Import previously written file
        Map<String, FieldInfoContainer> fields = classMap.get(objectName);
        JSONObject jsonObject = null;
        try {
            File f = new File(JSON_OUTPUT_FILE_NAME); // Create new file if not exists
            if(!f.exists())
                f.createNewFile();

            BufferedReader br = new BufferedReader(new FileReader(JSON_OUTPUT_FILE_NAME));
            
            StringBuffer sb = new StringBuffer();
            for(String line; (line = br.readLine()) != null; ) {
                sb.append(line);
            }
            jsonObject = sb.toString().isEmpty() ? new JSONObject() : // Imported JSON data, to be appended to 
                new JSONObject(sb.toString());
                
            JSONObject newJSONField, newJsonClass = new JSONObject();
            FieldInfoContainer fic;
            for (Map.Entry<String, FieldInfoContainer> entry : fields.entrySet()) {
                fic = entry.getValue();
                newJSONField = new JSONObject();
                newJSONField.put("annotation", fic.annotation);
                newJSONField.put("type", fic.fieldType);
                newJSONField.put("static", fic.isStatic);
                newJSONField.put("final", fic.isFinal);
                newJsonClass.put(entry.getKey(), newJSONField);
            }
            jsonObject.put(objectName, newJsonClass);

            br.close();
        }
        catch (IOException e) {
            System.err.println("Error while opening file!");
            e.printStackTrace();
            System.exit(1); // No idea to continue
        }
        catch (JSONException e) {
            System.err.println("Error when writing stats file!");
            e.printStackTrace();
            System.exit(1); // No idea to continue
        }
        
        try {
            FileWriter fstream = new FileWriter(JSON_OUTPUT_FILE_NAME);
            fstream.write(jsonObject.toString());
            fstream.write("\n");
            fstream.close();
        } catch (IOException e) {
            System.err.println("Error when writing stats file!");
        }
    }

    private static void putIntoClassInfoMap(List<? extends Element> inheritedMemberList,
                                            AnnotatedTypeFactory factory,
                                            PrecisionChecker checker,
                                            Map<String, FieldInfoContainer> classInfo) {
        FieldInfoContainer fic;
        for (VariableElement field : ElementFilter.fieldsIn(inheritedMemberList)) {
            AnnotatedTypeMirror fieldType = factory.getAnnotatedType(field);
            fic = new FieldInfoContainer();
            
            fic.fieldType = fieldType.getUnderlyingType().toString();
            
            if (fieldType.hasEffectiveAnnotation(checker.APPROX))
                fic.annotation = AnnotationType.Approx;
            else if (fieldType.hasEffectiveAnnotation(checker.CONTEXT))
                fic.annotation = AnnotationType.Context;
            else
                fic.annotation = AnnotationType.Precise;

            fic.isStatic = ElementUtils.isStatic(field);
            fic.isFinal = ElementUtils.isFinal(field);            
            classInfo.put(field.toString(), fic);
            // bitmap = 0;
        }
    }

    /**
     * Gather information about some objects' fields and add that information +
     * their types and annotations to a map.
     * @param type The object (manifestation)
     * @param factory Tools for extracting valid information from some field
     * (in this case)
     * @param typeutils Tools to extract the fields from the object (in this
     * case) 
     * @param checker Used to check for the sought annotions
     */
    private static void addToClassMap(AnnotatedTypeMirror type,
                                      AnnotatedTypeFactory factory,
                                      Types typeutils,
                                      PrecisionChecker checker) {

        String objectName = type.getUnderlyingType().toString();
        
        TypeElement current =
            (TypeElement)typeutils.asElement(type.getUnderlyingType());
        List<? extends Element> members = current.getEnclosedElements();
        List< MyTuple<String, List<? extends Element>>> inheritedMembers =
            new ArrayList< MyTuple< String, List<? extends Element>>>();
        MyTuple<String, List<? extends Element>> currentTuple = 
            new MyTuple<String, List<? extends Element>>(objectName, members);
        inheritedMembers.add(currentTuple); // Add first object

        // System.err.print(current.toString()); //DEBUG
        // System.err.println("current.toString(): " + current.toString()); //DEBUG

        TypeMirror supertypeMirror = current.getSuperclass(); // Start climbing upwards
        // if (supertypeMirror.getKind() != TypeKind.NONE) { // Only traverse if declared object != java.lang.Object
            // int countSuperClasses = 0;  //DEBUG
            TypeElement supertypeElem;
            // while (!supertypeMirror.toString().equals("java.lang.Object")) {
            
            // Keep going until java.long.Object is reached or until a parent
            // that is already collected is found
            while (supertypeMirror.getKind() != TypeKind.NONE &&    // Comparing type kind is faster
                    !classMap.containsKey(supertypeMirror.toString())) { // TODO: include, but fix bug <--
                // System.err.println("supertypeMirror.toString(): " + supertypeMirror.toString()); //DEBUG
                // System.err.println("supertypeMirror.getKind() != TypeKind.NONE: " + (supertypeMirror.getKind() != TypeKind.NONE));    // Comparing type kind is faster
                // System.err.println("!classMap.containsKey(supertypeMirror.toString()): " + (!classMap.containsKey(supertypeMirror.toString())));
                // System.err.print(" -> (" + ++countSuperClasses + ") " + supertypeMirror.toString()); //DEBUG
                supertypeElem = (TypeElement)((DeclaredType)supertypeMirror).asElement();   // Get supertype element
                List<? extends Element> superMembers = supertypeElem.getEnclosedElements();
                inheritedMembers.add(
                    new MyTuple<String, List<? extends Element>>(supertypeMirror.toString(), superMembers));    // Extend the members list (TODO: might cause override collisions)
                supertypeMirror = supertypeElem.getSuperclass();    // Get next superclass
            }
            // Reason of loop exit
            // System.err.println("supertypeMirror.getKind() != TypeKind.NONE: " + (supertypeMirror.getKind() != TypeKind.NONE));    // Comparing type kind is faster
            // System.err.println("!classMap.containsKey(supertypeMirror.toString()): " + (!classMap.containsKey(supertypeMirror.toString())));
            // System.err.println();
        // }
        // System.err.println(); //DEBUG
        
        // Add support for (multiple) interfaces
        // Later: isn't this implied from the implementing classes?
        // for (TypeMirror supertypeInterface : current.getInterfaces()) { //DEBUG
        //     System.err.print(" -> (" + ++countSuperClasses + ") [Interf.] " + supertypeInterface.toString());
        //     supertypeElem = (TypeElement)((DeclaredType)supertypeInterface).asElement();   // Get supertype element
        //     List<? extends Element> superMembers = supertypeElem.getEnclosedElements();
        //     inheritedMembers.add(superMembers);
        // }
        // System.err.println(); //DEBUG

        // Used to classify fields
        // 16-static; 8-final; 4-approx; 2-context; 1-precise
        // byte bitmap = 0;
        
        for (MyTuple<String, List<? extends Element>> inheritedMemberNameList : inheritedMembers) {
            Map<String, FieldInfoContainer> classInfo =
                new HashMap<String, FieldInfoContainer>();

            putIntoClassInfoMap(inheritedMemberNameList.y, factory, checker, classInfo);
            
            // Put the info into the "global" info map        
            classMap.put(inheritedMemberNameList.x, classInfo);
            // Write the gathered data to a JSON output file
            writeToJSONFile(inheritedMemberNameList.x);
        }

    }

	/**
     * Get the size of an instance of a given class type (at compile time)
     * @param type Represented data type
     * @param factory Used for getting annotated type
     * @param typeutils Used for getting enclosed elements from a type
     * @param checker Checker representation of (among others) EnerJ annotations
     * @return 
     */
	public static int[] objectSizes(AnnotatedTypeMirror type,
	                                AnnotatedTypeFactory factory,
	                                Types typeutils,
	                                PrecisionChecker checker) {
	    boolean approx = type.hasEffectiveAnnotation(checker.APPROX);
	    int preciseSize = 0;
	    int approxSize = 0;

        // Gather information about all members and their notations
        if (!classMap.containsKey(type.getUnderlyingType().toString())) {
            // DEBUG
            // if (type.hasEffectiveAnnotation(checker.APPROX))
            //     System.err.print("Approx ");
            // else if (type.hasEffectiveAnnotation(checker.CONTEXT))
            //     System.err.print("Context ");
            // else
            //     System.err.print("Precise ");
            // System.err.println(type.getUnderlyingType().toString());
            
            addToClassMap(type, factory, typeutils, checker);
        }

        List<? extends Element> members =
            ((TypeElement)typeutils.asElement(type.getUnderlyingType())).getEnclosedElements();

        for (VariableElement field : ElementFilter.fieldsIn(members)) {
            AnnotatedTypeMirror fieldType = factory.getAnnotatedType(field);
            int[] sizes = typeSizes(fieldType, approx, checker);
            preciseSize += sizes[0];
            approxSize  += sizes[1];
        }

	    preciseSize += POINTER_SIZE; // vtable

        /* Enerj-pldi2011.pdf, p.6:
         * "Some of this data may be placed in a precise line[...]"
         * Thus, some approximative data is placed on a precise line.
         */
	    int wastedApprox = Math.min(
	    	LINE_SIZE - (preciseSize % LINE_SIZE), // remainder of last precise line
	    	approxSize // all the approximate data
	    );
	    preciseSize += wastedApprox;
	    approxSize -= wastedApprox;

	    if (wastedApprox != 0 || approxSize != 0) {
	    	System.out.println(preciseSize + " " + approxSize + "; " + wastedApprox);
	    }
	    return new int[]{preciseSize, approxSize};
	}
    
    /**
     * Maps class name -> Set of underlying members and their notations
     */
    public static Map<String, Map<String, FieldInfoContainer>> classMap =
        new HashMap<String, Map<String, FieldInfoContainer>>();
    // (This could maybe have be done with a simple set, but I may be find a
    // less nasty way of gatherign this data in the future - this map may become
    // handy if so
}
