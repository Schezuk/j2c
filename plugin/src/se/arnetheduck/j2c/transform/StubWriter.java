package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class StubWriter {
	private static final String i1 = TransformUtil.indent(1);

	private final IPath root;
	private final Transformer ctx;
	private final ITypeBinding type;

	private final DepInfo deps;

	private Collection<IMethodBinding> constructors = new ArrayList<IMethodBinding>();

	private final Impl impl;
	private final String qcname;
	private final String name;

	private PrintWriter out;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;

		deps = new DepInfo(ctx);
		impl = new Impl(ctx, type, deps);
		qcname = CName.qualified(type, false);
		name = CName.of(type);
	}

	public void write(boolean natives, boolean privates) throws Exception {
		String body = getBody(natives, privates);
		String extras = getPrefix(natives);
		String suffix = getSuffix(natives);

		if (natives) {
			ctx.addNative(type);
			impl.write(root, extras + body + suffix, TransformUtil.NATIVE,
					null, null, natives);
		} else {
			ctx.addStub(type);
			impl.write(root, extras + body + suffix, TransformUtil.STUB, null,
					null, natives);
		}
	}

	private String getPrefix(boolean natives) {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		for (Snippet snippet : ctx.snippets) {
			if (!snippet.prefix(ctx, this, natives)) {
				return "";
			}
		}

		out.println("extern void unimplemented_(const char16_t* name);");

		if (!natives) {
			printDefaultInitCtor();
			printCtors();
		}

		out.close();
		out = null;

		return sw.toString();
	}

	private String getSuffix(boolean natives) {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		for (Snippet snippet : ctx.snippets) {
			if (!snippet.suffix(ctx, this, natives)) {
				return "";
			}
		}

		out.close();
		out = null;

		return sw.toString();
	}

	private String getBody(boolean natives, boolean privates) throws Exception {
		StringWriter ret = new StringWriter();
		out = new PrintWriter(ret);

		if (!natives) {
			for (IVariableBinding vb : type.getDeclaredFields()) {
				printField(vb);
			}
		}

		println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers()) == natives) {
				printMethod(mb, privates);
			}
		}

		out.close();

		out = null;

		return ret.toString();
	}

	private void printCtors() {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		if (type.isAnonymous()) {
			Header.getAnonCtors(type, constructors);
		}

		boolean hasEmpty = false;
		for (IMethodBinding mb : constructors) {
			print(qcname + "::" + name + "(");

			String sep = TransformUtil.printExtraCtorParams(ctx, out, type,
					null, deps, false, mb);

			if (mb.getParameterTypes().length > 0) {
				print(sep);
				TransformUtil.printParams(out, type, mb, false, deps);
			} else {
				hasEmpty = true;
			}

			println(")");

			print(i1 + ": " + name + "(");
			sep = "";
			if (TransformUtil.hasOuterThis(type)) {
				print(TransformUtil.outerThisName(type));
				sep = ", ";
			}

			println(sep + TransformUtil.makeDefaultInitTag() + ")");

			println("{");
			print(i1 + CName.CTOR + "(");

			sep = TransformUtil.printEnumCtorCallParams(out, type, "");
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep + TransformUtil.paramName(mb, i));
				sep = ", ";
			}

			println(");");

			println("}");
			println();
		}

		if (TransformUtil.needsEmptyCtor(hasEmpty, false, type)) {
			printEmptyCtor();
		}
	}

	private void printDefaultInitCtor() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		print(qcname + "::" + name + "(");
		TransformUtil.printExtraCtorParams(ctx, out, type, null, deps, true,
				null);
		println(")");

		printFieldInit(": ");

		println("{");
		printClInitCall();
		println("}");
		println();

	}

	private void printClInitCall() {
		println(i1 + CName.STATIC_INIT + "();");
	}

	private void printEmptyCtor() {
		print("void " + qcname + "::" + CName.CTOR + "(");
		TransformUtil.printEnumCtorParams(ctx, out, type, "", deps);
		println(")");
		println("{");
		if (type.getSuperclass() != null) {
			print(i1 + "super::");
			TransformUtil.printEmptyCtorCall(out, type);
			println(";");
		}

		println("}");
		println();
	}

	private void printFieldInit(String sep) {
		ITypeBinding sb = type.getSuperclass();
		if (sb != null) {
			print(i1 + sep);
			print("super(");
			if (TransformUtil.hasOuterThis(sb)) {
				String sepx = "";
				for (ITypeBinding tb = type; tb.getDeclaringClass() != null; tb = tb
						.getDeclaringClass().getErasure()) {
					print(sepx);
					sepx = "->";
					hardDep(tb.getDeclaringClass());
					print(TransformUtil.outerThisName(tb));
					if (tb.getDeclaringClass()
							.getErasure()
							.isSubTypeCompatible(
									sb.getDeclaringClass().getErasure())) {
						break;
					}

				}

				print(", ");
			}

			println(TransformUtil.makeDefaultInitTag() + ")");
			sep = ", ";
		}

		if (TransformUtil.hasOuterThis(type)
				&& (sb == null || sb.getDeclaringClass() == null || !type
						.getDeclaringClass().getErasure()
						.isEqualTo(sb.getDeclaringClass().getErasure()))) {
			print(i1 + sep);
			hardDep(type.getDeclaringClass());
			printInit(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		for (IVariableBinding vb : type.getDeclaredFields()) {
			if (TransformUtil.isStatic(vb)) {
				continue;
			}

			if (TransformUtil.initialValue(vb) != null) {
				continue;
			}

			// TODO Init string literals
			print(i1 + sep + CName.of(vb));

			println("()");
			sep = ", ";

		}
	}

	private void printInit(String n) {
		println(n + "(" + n + ")");
	}

	private void printField(IVariableBinding vb) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.field(ctx, this, vb)) {
				return;
			}
		}

		if (!TransformUtil.isStatic(vb)) {
			return;
		}

		Object cv = TransformUtil.constexprValue(vb);
		boolean asMethod = TransformUtil.asMethod(vb);

		ITypeBinding vt = vb.getType();
		String vname = CName.of(vb);
		String qvtname = TransformUtil
				.varTypeCName(vb.getModifiers(), vt, deps);
		if (asMethod) {
			print(qvtname);
			println("& " + qcname + "::" + vname + "()");
			println("{");
			printClInitCall();
			println(i1 + "return " + vname + "_;");
			println("}");
		}

		print(TransformUtil.fieldModifiers(type, vb.getModifiers(), false,
				cv != null && !(cv instanceof String)));

		print(qvtname + " " + qcname + "::" + vname);
		println(asMethod ? "_;" : ";");
	}

	private void printMethod(IMethodBinding mb, boolean privates)
			throws Exception {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.method(ctx, this, mb)) {
				return;
			}
		}

		if (Modifier.isAbstract(mb.getModifiers())) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers()) && !privates) {
			print("/* private: ");
			TransformUtil.printSignature(ctx, out, type, mb, deps, true);
			println(" */");
			return;
		}

		impl.method(mb);

		if (mb.isConstructor()) {
			constructors.add(mb);
		}

		TransformUtil.printSignature(ctx, out, type, mb, deps, true);

		println();
		print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			println(" /* native */");
		} else {
			println(" /* stub */");
		}

		if (TransformUtil.isStatic(mb)) {
			printClInitCall();
		}

		if (mb.isConstructor() && type.getSuperclass() != null) {
			// Remind the user that this has to be done one way or another
			// TODO Maybe try to find a suitable candidate to pass as many
			// parameters as possible
			println(i1 + "/* super::" + CName.CTOR + "(); */");
		}

		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			print(i1 + "unimplemented_(u\"");
			TransformUtil.printSignature(ctx, out, type, mb, deps, true);
			println("\");");
			if (!TransformUtil.isVoid(mb.getReturnType())) {
				println(i1 + "return 0;");
			}
		}

		println("}");
		println();

		TransformUtil.defineBridge(ctx, out, type, mb, deps);
	}

	public void hardDep(ITypeBinding dep) {
		deps.hard(dep);
	}

	public void print(String string) {
		out.print(string);
	}

	public void println(String string) {
		out.println(string);
	}

	public void println() {
		out.println();
	}

	public ITypeBinding type() {
		return type;
	}
}
