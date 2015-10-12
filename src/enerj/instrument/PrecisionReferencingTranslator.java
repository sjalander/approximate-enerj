package enerj.instrument;

import enerj.PrecisionChecker;
import enerj.lang.Approx;
import enerj.lang.Approx0;
import enerj.lang.Approx8;
import enerj.lang.Approx16;
import enerj.lang.Approx24;
import enerj.lang.Context;

import com.sun.source.util.TreePath;
import javax.annotation.processing.ProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import java.util.Set;
import java.util.HashSet;

import checkers.runtime.instrument.ReferencingTranslator;
import checkers.types.AnnotatedTypeMirror;

// Tightly couples with the SimulationTranslator, this pass replaces all local
// variable references (and method parameters) with reference objects. This
// allows us to simulate pass-by-reference for instrumentation of local variable
// reads and writes.
public class PrecisionReferencingTranslator extends ReferencingTranslator<PrecisionChecker> {
    public PrecisionReferencingTranslator(PrecisionChecker checker,
                                 ProcessingEnvironment env,
                                 TreePath p) {
        super(checker, env, p);

        // Use our references (which include an "approx" flag) instead of the
        // provided reference class. Should change this eventually (FIXME).
        REFERENCE_CLASS = enerj.rt.Reference.class.getName();
    }

    // An *extremely hacky* way to make a few more trees behave approximately
    // than are those that annotated by the atypeFactory.
    protected static Set<JCTree> approxTrees = new HashSet<JCTree>();

    protected boolean isApprox(JCTree tree) {
        AnnotatedTypeMirror treeType = atypeFactory.getAnnotatedType(tree);
        if (treeType.hasAnnotation(Approx.class)   ||
            treeType.hasAnnotation(Approx0.class)  ||
            treeType.hasAnnotation(Approx8.class)  ||
            treeType.hasAnnotation(Approx16.class) ||
            treeType.hasAnnotation(Approx24.class)) {
        	return true;
        } else if (treeType.hasAnnotation(Context.class)) {
        	return true; // TODO! Look up precision from runtime index.
        } else if (approxTrees.contains(tree)) {
        	return true;
        } else {
        	return false;
        }
    }

    protected int approximativeBits(JCTree tree) {
        if (!isApprox(tree)) // If precise
            return 0;

        AnnotatedTypeMirror treeType = atypeFactory.getAnnotatedType(tree);
        if (treeType.hasAnnotation(Approx.class))
            return 32;
        else if (treeType.hasAnnotation(Approx0.class))
            return 0;
        else if (treeType.hasAnnotation(Approx8.class))
            return 8;
        else if (treeType.hasAnnotation(Approx16.class))
            return 16;
        else if (treeType.hasAnnotation(Approx24.class))
            return 24;
        else
            return 0;
    }

    @Override
    public JCTree.JCExpression createNewInitializer(JCTree.JCVariableDecl tree, JCTree.JCExpression boxedOldType,
            JCTree.JCExpression newType, JCTree.JCExpression init, boolean primitive) {
        // Was the old variable approximate?
        boolean approx = isApprox(tree);
	int approxBits = approximativeBits(tree);

        JCTree.JCExpression newInit = maker.NewClass(
            null,
            List.of(boxedOldType),
            newType,
            List.<JCTree.JCExpression>of(
                init,
                boolExp(approx),
                boolExp(primitive),
		maker.Literal(approxBits)
            ),
            null
        );

        return newInit;
    }
}
