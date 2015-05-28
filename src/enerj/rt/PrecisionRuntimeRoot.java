package enerj.rt;

public class PrecisionRuntimeRoot {

	public static final PrecisionRuntime impl;

	static {
		System.out.println("Loading PrecisionRuntimeRoot");
		
		String runtimeClass = System.getProperty("PrecisionRuntime");
		PrecisionRuntime newimpl;
		if (runtimeClass != null) {
			/* try to create an instance of this class */
			try {
				newimpl = (PrecisionRuntime)
					Class.forName(runtimeClass).newInstance();
			} catch (Exception e) {
				System.err.println("WARNING: the specified Precision Runtime Implementation class ("
								+ runtimeClass
								+ ") could not be instantiated, using the default instead.");
				// System.err.println(e);
				System.out.println("Loading PrecisionRuntimeTolop (anyway)");
				newimpl = new PrecisionRuntimeTolop();
			}
		} else {
			System.out.println("Loading PrecisionRuntimeTolop");
			newimpl = new PrecisionRuntimeTolop();
		}
		impl = newimpl;
	}

}
