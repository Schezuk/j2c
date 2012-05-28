package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Assignment.Operator;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

import se.arnetheduck.j2c.transform.Transformer.BindingComparator;

public class ImplWriter extends TransformWriter {
	private final IPath root;
	private final Set<IVariableBinding> closures;

	/** Static initializers */
	private StringWriter clinit;

	/** Instance initializers */
	private StringWriter init;

	private List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();
	private List<FieldDeclaration> fields = new ArrayList<FieldDeclaration>();

	public final Map<ITypeBinding, ImplWriter> localTypes = new TreeMap<ITypeBinding, ImplWriter>(
			new BindingComparator());

	private boolean needsFinally;
	private boolean needsSynchronized;

	private boolean hasNatives;

	public ImplWriter(IPath root, Transformer ctx, ITypeBinding type,
			UnitInfo unitInfo) {
		super(ctx, type, unitInfo);
		this.root = root;

		closures = type.isLocal() ? new HashSet<IVariableBinding>() : null;

		for (ImportDeclaration id : unitInfo.imports) {
			IBinding b = id.resolveBinding();
			if (b instanceof IPackageBinding) {
				ctx.packages.add((IPackageBinding) b);
			} else if (b instanceof ITypeBinding) {
				ctx.softDep((ITypeBinding) b);
			}
		}
	}

	public void write(EnumDeclaration node) throws Exception {
		StringWriter body = getBody(node.enumConstants(),
				node.bodyDeclarations());
		writeType(body);
	}

	public void write(TypeDeclaration node) throws Exception {
		if (node.isInterface()) {
			for (BodyDeclaration bd : (Iterable<BodyDeclaration>) node
					.bodyDeclarations()) {
				if (bd instanceof TypeDeclaration) {
					visit((TypeDeclaration) bd);
				}
			}
			return;
		}

		StringWriter body = getBody(new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations());
		writeType(body);
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		StringWriter body = getBody(new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations());
		writeType(body);
	}

	private StringWriter getBody(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		StringWriter body = new StringWriter();
		out = new PrintWriter(body);

		visitAll(enums);

		visitAll(declarations);

		out.close();
		return body;
	}

	private void writeType(StringWriter body) throws Exception {
		try {
			String cons = type.isAnonymous() ? makeBaseConstructors()
					: makeConstructors();

			out = TransformUtil.openImpl(root, type, "");

			for (ITypeBinding dep : getHardDeps()) {
				println(TransformUtil.include(dep));
			}

			if (fmod) {
				println("#include <cmath>");
			}

			println();
			println("using namespace java::lang;");

			for (ImportDeclaration node : unitInfo.imports) {
				if (node.isStatic()) {
					continue; // We qualify references to static imports
				}

				if (node.isOnDemand()
						&& node.getName().resolveBinding() instanceof IPackageBinding) {
					println("using namespace ::", TransformUtil.cname(node
							.getName().getFullyQualifiedName()), ";");
				} else {
					println("using ",
							TransformUtil.qualifiedCName(
									(ITypeBinding) node.resolveBinding(), true),
							";");
				}
			}

			println();

			if (type.getQualifiedName().equals("java.lang.Object")) {
				out.println("java::lang::Object::~Object()");
				out.println("{");
				out.println("}");
				out.println("");
			}

			if (needsFinally) {
				makeFinally();
			}

			if (needsSynchronized) {
				makeSynchronized();
			}

			if (clinit != null) {
				makeClinit();
			}

			if (init != null) {
				makeInit();
			}

			println(cons);

			print(body.toString());

			out.close();
			ctx.impls.add(type);

			if (hasNatives) {
				StubWriter sw = new StubWriter(root, ctx, type);
				sw.write(true);
			}
		} finally {
			out = null;
		}
	}

	private void makeFinally() {
		println("namespace {");
		indent++;
		printlni("template<typename F> struct finally_ {");
		indent++;
		printlni("finally_(F f) : f(f), moved(false) { }");
		printlni("finally_(finally_ &&x) : f(x.f), moved(false) { x.moved = true; }");
		printlni("~finally_() { if(!moved) f(); }");
		printlni("private: finally_(const finally_&); finally_& operator=(const finally_&); ");
		printlni("F f;");
		printlni("bool moved;");
		indent--;
		printlni("};");
		printlni("template<typename F> finally_<F> finally(F f) { return finally_<F>(f); }");
		indent--;
		printlni("}");
	}

	private void makeSynchronized() {
		println("extern void lock(java::lang::Object *);");
		println("extern void unlock(java::lang::Object *);");
		println("namespace {");
		indent++;
		printlni("struct synchronized {");
		indent++;
		printlni("synchronized(java::lang::Object *o) : o(o) { ::lock(o); }");
		printlni("~synchronized() { ::unlock(o); }");
		printlni("private: synchronized(const synchronized&); synchronized& operator=(const synchronized&); ");
		printlni("java::lang::Object *o;");
		indent--;
		printlni("};");
		indent--;
		printlni("}");
	}

	private void makeClinit() {
		if (clinit == null) {
			return;
		}

		String cname = TransformUtil.qualifiedCName(type, false);
		println("::", cname, "::", TransformUtil.STATIC_INIT, "::",
				TransformUtil.STATIC_INIT, "()");
		println("{");
		print(clinit.toString());
		println("}");
		println();

		println("struct ::", cname, "::", TransformUtil.STATIC_INIT, " ",
				cname, "::", TransformUtil.STATIC_INIT, ";");
		println();
	}

	private void makeInit() {
		if (init == null) {
			return;
		}

		String cname = TransformUtil.qualifiedCName(type, true);
		println("void ", cname, "::", TransformUtil.INSTANCE_INIT, "()");
		println("{");
		print(init.toString());
		println("}");
		println();
	}

	private void addInit(boolean isStatic, Block body,
			VariableDeclarationFragment field) {
		PrintWriter old = out;
		if (isStatic) {
			if (clinit == null) {
				clinit = new StringWriter();
			}

			out = new PrintWriter(clinit);
		} else {
			if (init == null) {
				init = new StringWriter();
			}

			out = new PrintWriter(init);
		}

		indent++;
		if (body != null) {
			printi();
			body.accept(this);
			println();
		} else {
			assert (field != null);
			printi();
			field.getName().accept(this);
			print(" = ");
			field.getInitializer().accept(this);
			println(";");
		}
		indent--;

		out.close();
		out = old;
	}

	private String makeConstructors() {
		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);
		String qname = TransformUtil.qualifiedCName(type, true);
		String name = TransformUtil.name(type);

		boolean hasEmpty = false;
		for (MethodDeclaration md : constructors) {
			printi(qname, "::", name, "(");

			String sep = printNestedParams(closures);

			if (!md.parameters().isEmpty()) {
				print(sep);

				visitAllCSV(md.parameters(), false);
			}

			hasEmpty |= md.parameters().isEmpty();

			println(") ", TransformUtil.throwsDecl(md.thrownExceptions()));

			indent++;
			printFieldInit(": ");
			indent--;

			println("{");
			indent++;
			if (init != null) {
				printlni(TransformUtil.INSTANCE_INIT, "();");
			}

			printi(TransformUtil.CTOR, "(");

			printNames(md.parameters());

			println(");");

			indent--;
			println("}");
			println();
		}

		if (!hasEmpty) {
			printi(qname, "::", name, "(");

			printNestedParams(closures);

			println(")");

			indent++;
			printFieldInit(": ");
			indent--;
			println("{");
			if (init != null) {
				indent++;
				printlni(TransformUtil.INSTANCE_INIT, "();");
				indent--;
			}
			println("}");
			println();
		}
		out.close();
		out = old;
		return sw.toString();
	}

	private void printFieldInit(String sep) {
		ITypeBinding sb = type.getSuperclass();
		if (sb != null && TransformUtil.isInner(sb)
				&& !TransformUtil.outerStatic(sb)) {
			printi(sep);
			print("super(");
			print(TransformUtil.outerThisName(sb));
			println(")");
			sep = ", ";
		} else if (TransformUtil.isInner(type)
				&& !TransformUtil.outerStatic(type)) {
			printi(sep);
			printInit(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		for (FieldDeclaration fd : fields) {
			for (VariableDeclarationFragment vd : (List<VariableDeclarationFragment>) fd
					.fragments()) {
				printi(sep);
				vd.getName().accept(this);
				println("()");
				sep = ", ";
			}
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				printi(sep);
				printInit(closure.getName() + "_");
				sep = ", ";
			}
		}
	}

	private String makeBaseConstructors() {
		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);
		// Synthesize base class constructors
		String qname = TransformUtil.qualifiedCName(type, true);
		String name = TransformUtil.name(type);
		for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
			if (!mb.isConstructor()) {
				continue;
			}

			printi(qname, "::", name, "(");

			String sep = printNestedParams(closures);

			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				ITypeBinding pb = mb.getParameterTypes()[i];
				ctx.softDep(pb);

				print(sep, TransformUtil.relativeCName(pb, type, true), " ",
						TransformUtil.ref(pb), "a" + i);
				sep = ", ";
			}

			println(")");
			indent++;
			printi(": ");
			print(TransformUtil.relativeCName(type.getSuperclass(), type, true),
					"(");

			sep = "";
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep, "a" + i);
				sep = ", ";
			}

			println(")");

			printFieldInit(", ");

			indent--;
			println("{");
			println("}");
			println();
		}
		out.close();
		out = old;
		return sw.toString();
	}

	private void printInit(String n) {
		println(n, "(", n, ")");
	}

	List<ASTNode> boxes = new ArrayList<ASTNode>();
	List<ASTNode> unboxes = new ArrayList<ASTNode>();

	@Override
	public boolean preVisit2(ASTNode node) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.node(ctx, this, node)) {
				return false;
			}
		}

		if (node instanceof Expression) {
			Expression expr = (Expression) node;
			ITypeBinding tb = expr.resolveTypeBinding();

			if ((expr.resolveBoxing() || expr.resolveUnboxing())
					&& checkBoxNesting(expr)) {

				if (expr.resolveBoxing()) {
					String x = TransformUtil.primitives.get(tb.getName());
					assert (x != null);
					boxes.add(node);
					ITypeBinding tb2 = node.getAST().resolveWellKnownType(x);
					hardDep(tb2);
					print(TransformUtil.relativeCName(tb2, type, true),
							"::valueOf(");
				} else if (expr.resolveUnboxing()) {
					if (TransformUtil.reverses.containsKey(tb
							.getQualifiedName())) {
						unboxes.add(node);
						hardDep(tb);
						print("(");
					}
				}
			}
		}

		return super.preVisit2(node);
	}

	private boolean checkBoxNesting(Expression expr) {
		if (expr.getParent() instanceof Name) {
			return false;
		}

		if (expr.getParent() instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) expr.getParent();
			if (mi.getExpression() == expr || mi.getName() == expr) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void postVisit(ASTNode node) {
		if (!boxes.isEmpty() && boxes.get(boxes.size() - 1) == node) {
			print(")");
			boxes.remove(boxes.size() - 1);
		}
		if (!unboxes.isEmpty() && unboxes.get(unboxes.size() - 1) == node) {
			print(")->", TransformUtil.reverses.get(((Expression) node)
					.resolveTypeBinding().getQualifiedName()), "Value()");
			unboxes.remove(unboxes.size() - 1);
		}
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		localTypes.put(tb, iw);

		if (iw.closures != null && closures != null) {
			for (IVariableBinding vb : iw.closures) {
				if (!vb.getDeclaringMethod().getDeclaringClass()
						.isEqualTo(type)) {
					closures.add(vb);
				}
			}
		}

		HeaderWriter hw = new HeaderWriter(root, ctx, tb, unitInfo);

		hw.writeType(node.getAST(), new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations(), iw.closures);
		return false;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		hardDep(node.getArray().resolveTypeBinding());

		print("(*");
		node.getArray().accept(this);
		print(")[");
		node.getIndex().accept(this);
		print("]");

		return false;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		ITypeBinding tb = node.getType().resolveBinding();
		hardDep(tb);
		print("(new ", TransformUtil.relativeCName(tb, type, true));

		for (Iterator<Expression> it = node.dimensions().iterator(); it
				.hasNext();) {
			print("(");
			Expression e = it.next();
			e.accept(this);
			print(")");
			break;
		}

		if (node.getInitializer() != null) {
			node.getInitializer().accept(this);
		}
		print(")");
		return false;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		if (!(node.getParent() instanceof ArrayCreation)) {
			print("(new ");
			ITypeBinding at = node.resolveTypeBinding();
			print(TransformUtil.qualifiedCName(at, true));
			hardDep(at);
		}

		print("(");
		print(node.expressions().size());

		if (!node.expressions().isEmpty()) {
			print(", ");
			visitAllCSV(node.expressions(), false);
			for (Expression e : (List<Expression>) node.expressions()) {
				hardDep(e.resolveTypeBinding());
			}
		}

		print(")");

		if (!(node.getParent() instanceof ArrayCreation)) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(Assignment node) {
		hardDep(node.getRightHandSide().resolveTypeBinding());

		ITypeBinding tb = node.getLeftHandSide().resolveTypeBinding();
		if (tb.getQualifiedName().equals("java.lang.String")
				&& node.getOperator() == Operator.PLUS_ASSIGN) {
			node.getLeftHandSide().accept(this);
			print(" = ::join(");
			node.getLeftHandSide().accept(this);
			print(", ");
			node.getRightHandSide().accept(this);
			print(")");

			return false;
		}

		if (node.getOperator() == Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN) {
			ITypeBinding b = node.getLeftHandSide().resolveTypeBinding();
			node.getLeftHandSide().accept(this);
			print(" = ");
			if (b.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			node.getLeftHandSide().accept(this);
			print(") >> ");
			node.getRightHandSide().accept(this);

			return false;
		}

		node.getLeftHandSide().accept(this);

		print(" ", node.getOperator(), " ");

		node.getRightHandSide().accept(this);

		return false;
	}

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(Block.class, CatchClause.class, DoStatement.class,
					EnhancedForStatement.class, ForStatement.class,
					IfStatement.class, Initializer.class,
					LabeledStatement.class, MethodDeclaration.class,
					SynchronizedStatement.class, SwitchStatement.class,
					TryStatement.class, WhileStatement.class));

	@Override
	public boolean visit(Block node) {
		ASTNode parent = node.getParent();
		if (!handledBlocks.contains(parent.getClass())) {
			System.out.println("Skipped " + parent.getClass() + " block");
			return false;
		}

		println("{");

		indent++;

		visitAll(node.statements());

		if (parent instanceof LabeledStatement) {
			LabeledStatement ls = (LabeledStatement) parent;
			indent--;
			printlni("}");
			ls.getLabel().accept(this);
			println("_break:;");
		} else {
			indent--;
			printi("}");
		}

		return false;
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (node.getLabel() != null) {
			printi("goto ");
			node.getLabel().accept(this);
			print("_break");
		} else {
			printi("break");
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(CastExpression node) {
		ITypeBinding tb = node.getType().resolveBinding();
		hardDep(tb);
		hardDep(node.getExpression().resolveTypeBinding());

		print(node.getType().isPrimitiveType()
				|| node.getExpression() instanceof NullLiteral ? "static_cast< "
				: "dynamic_cast< ");

		print(TransformUtil.relativeCName(tb, type, true),
				TransformUtil.ref(node.getType()));

		print(" >(");

		node.getExpression().accept(this);

		print(")");
		return false;
	}

	@Override
	public boolean visit(CatchClause node) {
		print(" catch (");
		node.getException().accept(this);
		hardDep(node.getException().getType().resolveBinding());
		print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		print("(new ");

		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getAnonymousClassDeclaration() != null) {
			tb = node.getAnonymousClassDeclaration().resolveBinding();
			print(TransformUtil.name(tb));
			node.getAnonymousClassDeclaration().accept(this);
		} else {
			print(TransformUtil.typeArguments(node.typeArguments()));

			print(TransformUtil.relativeCName(tb, type, true));
		}

		hardDep(tb);

		print("(");

		String sep = "";
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			sep = ", ";
		} else if (TransformUtil.isInner(tb) && !TransformUtil.outerStatic(tb)) {
			print(tb.getDeclaringClass().getErasure()
					.isEqualTo(type.getErasure()) ? "this" : TransformUtil
					.outerThisName(tb));
			sep = ", ";
		}

		if (localTypes.containsKey(tb)) {
			ImplWriter iw = localTypes.get(tb);
			for (IVariableBinding closure : iw.closures) {
				print(sep, closure.getName(), "_");
				sep = ", ";
			}
		}

		if (!node.arguments().isEmpty()) {
			print(sep);
			Iterable<Expression> arguments = node.arguments();
			visitAllCSV(arguments, false);

			for (Expression e : arguments) {
				hardDep(e.resolveTypeBinding());
			}
		}

		print("))");

		return false;
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		ITypeBinding tb = node.resolveTypeBinding();
		node.getExpression().accept(this);
		print(" ? ");
		cast(node.getThenExpression(), tb);
		print(" : ");
		cast(node.getElseExpression(), tb);
		return false;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print(TransformUtil.CTOR);

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (node.getLabel() != null) {
			printi("goto ");
			node.getLabel().accept(this);
			print("_cont");
		} else {
			printi("continue");
		}

		println(";");

		return false;
	}

	@Override
	public boolean visit(DoStatement node) {
		printi("do ");
		node.getBody().accept(this);
		print(" while (");
		node.getExpression().accept(this);
		println(");");

		return false;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {

		LabeledStatement label = (LabeledStatement) (node.getParent() instanceof LabeledStatement ? node
				.getParent() : null);
		ITypeBinding eb = node.getExpression().resolveTypeBinding();
		hardDep(eb);
		if (eb.isArray()) {
			printlni("{");
			indent++;
			printi("auto _a = ");
			node.getExpression().accept(this);
			println(";");
			printlni("for(int _i = 0; _i < _a->length_; ++_i) {");
			indent++;
			printi();
			node.getParameter().accept(this);
			println(" = (*_a)[_i];");
			printi();
			node.getBody().accept(this);
			if (label != null) {
				println();
				label.getLabel().accept(this);
				println("_cont:;");
			}
			indent--;
			printlni("}");
			if (label != null) {
				println();
				label.getLabel().accept(this);
				println("_break:;");
			}
			indent--;
			printlni("}");
		} else {
			printi("for (auto _i = ");
			node.getExpression().accept(this);
			println("->iterator(); _i->hasNext(); ) {");
			indent++;
			printi();
			node.getParameter().accept(this);
			print(" = ");
			ITypeBinding tb = node.getParameter().getType().resolveBinding();

			dynamicCast(tb);
			println("_i->next());");
			printi();
			node.getBody().accept(this);
			if (label != null) {
				println();
				printlni(label.getLabel().getIdentifier() + "_cont:;");
			}
			indent--;
			printlni("}");

			if (label != null) {
				println();
				printlni(label.getLabel().getIdentifier() + "_break:;");
			}

			ITypeBinding tb2 = eb;
			hardDep(getIterator(tb2));
		}

		return false;
	}

	private ITypeBinding getIterator(ITypeBinding tb) {
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			if (mb.getName().equals("iterator")
					&& mb.getReturnType().getErasure().getQualifiedName()
							.equals(Iterator.class.getName())) {
				return mb.getReturnType();
			}
		}

		if (tb.getSuperclass() != null) {
			return getIterator(tb.getSuperclass());
		}

		return null;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		String qcn = TransformUtil.qualifiedCName(type, true);
		print(qcn, " *", qcn, "::");

		node.getName().accept(this);

		/*
		 * TODO needs an initializer class
		 */
		print(" = new ", qcn);

		visitAllCSV(node.arguments(), true);

		println(";");

		if (node.getAnonymousClassDeclaration() != null) {
			// TODO node.getAnonymousClassDeclaration().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		HeaderWriter hw = new HeaderWriter(root, ctx, tb, unitInfo);

		hw.writeType(node.getAST(), node.enumConstants(),
				node.bodyDeclarations(), iw.closures);

		if (tb.isLocal()) {
			localTypes.put(tb, iw);
		}

		return false;
	}

	@Override
	public boolean visit(FieldAccess node) {
		node.getExpression().accept(this);
		hardDep(node.getExpression().resolveTypeBinding());
		print("->");
		node.getName().accept(this);
		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		Iterable<VariableDeclarationFragment> fragments = node.fragments();

		if (!Modifier.isStatic(node.getModifiers())) {
			fields.add(node);
			for (VariableDeclarationFragment f : fragments) {
				if (f.getInitializer() != null) {
					addInit(false, null, f);
				}
			}

			return false;
		}

		for (VariableDeclarationFragment f : fragments) {
			if (TransformUtil.constantValue(f) != null) {
				continue;
			}

			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					false, hasInitilializer(fragments)));

			ITypeBinding tb = node.getType().resolveBinding();
			tb = f.getExtraDimensions() > 0 ? tb.createArrayType(f
					.getExtraDimensions()) : tb;
			print(TransformUtil.qualifiedCName(tb, true));

			print(" ");

			print(TransformUtil.ref(tb));

			print(TransformUtil.qualifiedCName(type, false), "::");

			f.getName().accept(this);

			if (f.getInitializer() != null) {
				print(" = ");

				ITypeBinding ib = f.getInitializer().resolveTypeBinding();
				if (!ib.isEqualTo(tb)) {
					hardDep(ib);
				}

				f.getInitializer().accept(this);
			}

			println(";");
		}

		return false;
	}

	@Override
	public boolean visit(ForStatement node) {
		printi("for (");

		visitAllCSV(node.initializers(), false);

		print("; ");

		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		print("; ");

		visitAllCSV(node.updaters(), false);

		print(") ");
		handleLabelBody(node.getParent(), node.getBody());
		println();
		return false;
	}

	private boolean skipIndent = false;
	private boolean fmod;

	@Override
	public boolean visit(IfStatement node) {
		if (!skipIndent) {
			printi();
		}

		print("if(");
		skipIndent = false;

		node.getExpression().accept(this);

		print(")");

		boolean thenBlock = node.getThenStatement() instanceof Block;
		if (thenBlock) {
			print(" ");
		} else {
			println();
			indent++;
		}

		node.getThenStatement().accept(this);

		if (!thenBlock) {
			indent--;
		}

		if (node.getElseStatement() != null) {
			boolean elseif = skipIndent = node.getElseStatement() instanceof IfStatement;
			if (thenBlock) {
				print(" ");
			} else {
				printi();
			}
			print("else");
			boolean elseBlock = skipIndent
					|| node.getElseStatement() instanceof Block;
			if (elseBlock) {
				print(" ");
			} else {
				println();
				indent++;
			}

			node.getElseStatement().accept(this);

			if (!elseBlock) {
				indent--;
			}

			if (!elseif && elseBlock) {
				println();
			}
		} else {
			println();
		}

		return false;
	}

	@Override
	public boolean visit(InfixExpression node) {
		final List<Expression> extendedOperands = node.extendedOperands();
		ITypeBinding tb = node.resolveTypeBinding();
		if (tb != null && tb.getQualifiedName().equals("java.lang.String")) {
			print("::join(");
			for (int i = 0; i < extendedOperands.size(); ++i) {
				print("::join(");
			}

			node.getLeftOperand().accept(this);
			print(", ");
			node.getRightOperand().accept(this);
			print(")");

			for (Expression e : extendedOperands) {
				print(", ");
				e.accept(this);
				print(")");
			}

			return false;
		}

		if (node.getOperator().equals(InfixExpression.Operator.REMAINDER)) {
			if (tb.getName().equals("float") || tb.getName().equals("double")) {
				fmod = true;
				print("std::fmod(");
				cast(node.getLeftOperand(), tb);
				print(", ");
				cast(node.getRightOperand(), tb);
				print(")");

				return false;
			}
		}

		if (node.getOperator().equals(
				InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)) {
			ITypeBinding b = node.getLeftOperand().resolveTypeBinding();
			if (b.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			node.getLeftOperand().accept(this);
			print(") >>");
		} else {
			node.getLeftOperand().accept(this);
			print(' '); // for cases like x= i - -1; or x= i++ + ++i;

			print(node.getOperator().toString());
		}

		hardDep(node.getLeftOperand().resolveTypeBinding());

		print(' ');

		node.getRightOperand().accept(this);

		hardDep(node.getRightOperand().resolveTypeBinding());

		if (!extendedOperands.isEmpty()) {
			print(' ');

			for (Expression e : extendedOperands) {
				print(node.getOperator().toString(), " ");
				e.accept(this);
				hardDep(e.resolveTypeBinding());
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		addInit(Modifier.isStatic(node.getModifiers()), node.getBody(), null);

		return false;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		ITypeBinding tb = node.getRightOperand().resolveBinding();
		dynamicCast(tb);
		node.getLeftOperand().accept(this);
		print(")");
		return false;
	}

	@Override
	public boolean visit(LabeledStatement node) {
		node.getBody().accept(this);

		return false;
	}

	private void handleLabelBody(ASTNode parent, Statement body) {
		if (parent instanceof LabeledStatement) {
			LabeledStatement node = (LabeledStatement) parent;
			println("{");
			indent++;

			if (body instanceof Block) {
				visitAll(((Block) body).statements());
			} else {
				body.accept(this);
			}

			println();
			node.getLabel().accept(this);
			println("_cont:;");
			indent--;
			printlni("}");
			node.getLabel().accept(this);
			println("_break:;");
		} else {
			body.accept(this);
		}
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (node.getBody() == null) {
			hasNatives |= Modifier.isNative(node.getModifiers());
			return false;
		}

		IMethodBinding mb = node.resolveBinding();
		if (TransformUtil.isMain(mb)) {
			ctx.mains.add(type);
		}

		printi(TransformUtil.typeParameters(node.typeParameters()));

		if (node.isConstructor()) {
			constructors.add(node);

			printi("void ", TransformUtil.qualifiedCName(type, true), "::",
					TransformUtil.CTOR);
		} else {
			ITypeBinding rt = TransformUtil.returnType(node);
			ctx.softDep(rt);
			print(TransformUtil.qualifiedCName(rt, true), " ",
					TransformUtil.ref(rt));

			print(TransformUtil.qualifiedCName(type, true), "::");

			node.getName().accept(this);
		}

		visitAllCSV(node.parameters(), true);

		println(TransformUtil.throwsDecl(node.thrownExceptions()));

		node.getBody().accept(this);

		println();
		println();

		for (ITypeBinding dep : TransformUtil.defineBridge(out, type, mb, ctx)) {
			hardDep(dep);
		}

		return false;
	}

	private void printNames(Iterable<SingleVariableDeclaration> parameters) {
		for (Iterator<SingleVariableDeclaration> it = parameters.iterator(); it
				.hasNext();) {
			SingleVariableDeclaration v = it.next();

			v.getName().accept(this);

			if (it.hasNext()) {
				print(",");
			}
		}
	}

	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding b = node.resolveMethodBinding();
		boolean erased = returnErased(b);

		if (erased) {
			dynamicCast(b.getReturnType());
		}

		Expression expr = node.getExpression();
		if (expr != null) {
			expr.accept(this);

			if (expr instanceof Name) {
				IBinding eb = ((Name) expr).resolveBinding();
				if (eb instanceof ITypeBinding) {
					print("::");
				} else {
					print("->");
				}
			} else {
				// Assume the expression returned an instance
				print("->");
			}

			hardDep(expr.resolveTypeBinding());
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		List<Expression> arguments = node.arguments();
		print("(");

		String s = "";
		boolean isVarArg = false;
		for (int i = 0; i < arguments.size(); ++i) {
			print(s);
			s = ", ";
			if (b.isVarargs() && i == b.getParameterTypes().length - 1) {
				ITypeBinding tb = b.getParameterTypes()[b.getParameterTypes().length - 1]
						.getErasure();
				if (!arguments.get(i).resolveTypeBinding()
						.isAssignmentCompatible(tb)) {
					hardDep(tb);
					print("new " + TransformUtil.relativeCName(tb, type, true),
							"(");
					print(arguments.size() - b.getParameterTypes().length + 1,
							", ");
					isVarArg = true;
				}
			}

			ITypeBinding pb;
			if (b.isVarargs() && i >= b.getParameterTypes().length - 1) {
				pb = b.getParameterTypes()[b.getParameterTypes().length - 1];
				if (isVarArg) {
					pb = pb.getComponentType();
				}
			} else {
				pb = b.getParameterTypes()[i];
			}

			Expression argument = arguments.get(i);
			cast(argument, pb);

			if (isVarArg && i == arguments.size() - 1
					&& i >= b.getParameterTypes().length - 1) {
				print(")");
			}
		}

		if (b.isVarargs() && arguments.size() < b.getParameterTypes().length) {
			if (arguments.size() > 0) {
				print(", ");
			}

			ITypeBinding tb = b.getParameterTypes()[b.getParameterTypes().length - 1];
			hardDep(tb);
			print("new " + TransformUtil.relativeCName(tb, type, true), "(0)");
		}

		print(")");

		if (erased) {
			print(")");
		}

		return false;
	}

	private boolean returnErased(IMethodBinding b) {
		return !b
				.getMethodDeclaration()
				.getReturnType()
				.isEqualTo(
						b.getMethodDeclaration().getReturnType().getErasure());
	}

	private void dynamicCast(ITypeBinding rt) {
		hardDep(rt);
		print("dynamic_cast< ", TransformUtil.relativeCName(rt, type, true),
				"* >(");
	}

	private void cast(Expression argument, ITypeBinding pb) {
		ITypeBinding tb = argument.resolveTypeBinding();
		if (!tb.isEqualTo(pb)
				&& !(argument.resolveBoxing() || argument.resolveUnboxing())) {
			// Java has different implicit cast rules
			hardDep(tb);
			print("static_cast< ", TransformUtil.relativeCName(pb, type, true),
					TransformUtil.ref(pb), " >(");
			argument.accept(this);
			print(")");
		} else {
			argument.accept(this);
		}
	}

	@Override
	public boolean visit(PostfixExpression node) {
		node.getOperand().accept(this);
		print(node.getOperator().toString());
		return false;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		print(node.getOperator().toString());
		node.getOperand().accept(this);
		return false;
	}

	@Override
	public boolean visit(ReturnStatement node) {
		printi("return");
		if (node.getExpression() != null) {
			hardDep(node.getExpression().resolveTypeBinding());
			print(" ");
			node.getExpression().accept(this);
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;

			if (isClosure(node, vb)) {
				closures.add(vb);
			}
		}

		return super.visit(node);
	}

	private boolean isClosure(SimpleName node, IVariableBinding vb) {
		if (vb.isField()) {
			return false;
		}

		if (vb.getDeclaringMethod() == null) {
			return false;
		}

		if (!Modifier.isFinal(vb.getModifiers())) {
			return false;
		}
		VariableDeclarationFragment vdf = initializer(node);
		if (vdf != null) {
		} else {
			IMethodBinding pmb = parentMethod(node);
			if (pmb.isEqualTo(vb.getDeclaringMethod())) {
				return false;
			}
		}

		return true;
	}

	private VariableDeclarationFragment initializer(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n.getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) n
						.getParent();
				if (type.isEqualTo(vdf.resolveBinding().getDeclaringClass())
						&& vdf.getInitializer() == n) {
					return vdf;
				}
			}
		}

		return null;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();
		if (node.getExtraDimensions() > 0) {
			tb = tb.createArrayType(node.getExtraDimensions());
		}

		if (node.isVarargs()) {
			tb = tb.createArrayType(1);
			print(TransformUtil.relativeCName(tb, type, true));
			print("/*...*/");
		} else {
			print(TransformUtil.relativeCName(tb, type, true));
		}

		print(" ", TransformUtil.ref(tb));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			print(".");
		}

		printi(TransformUtil.typeArguments(node.typeArguments()));

		print("super::", TransformUtil.CTOR);

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			print(".");
		}

		print("super::");
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		IMethodBinding b = node.resolveMethodBinding();
		boolean erased = returnErased(b);

		if (erased) {
			dynamicCast(b.getReturnType());
		}

		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			print(".");
		}

		print("super::");

		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

		if (erased) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchCase node) {
		printi();

		if (node.isDefault()) {
			print("default:");
		} else {
			print("case ");
			node.getExpression().accept(this);
			print(":");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		List<Statement> statements = node.statements();
		List<VariableDeclarationStatement> vdss = declarations(statements);

		if (!vdss.isEmpty()) {
			printlni("{");
			indent++;

			for (VariableDeclarationStatement vds : vdss) {
				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					ITypeBinding vdb = vds.getType().resolveBinding();
					ITypeBinding fb = fragment.getExtraDimensions() == 0 ? vdb
							: vdb.createArrayType(fragment.getExtraDimensions());
					hardDep(fb);

					printi(TransformUtil.variableModifiers(type,
							vds.getModifiers()));
					print(TransformUtil.relativeCName(fb, type, true), " ");
					print(TransformUtil.ref(fb));
					fragment.getName().accept(this);
					println(";");
				}
			}
		}

		printi("switch (");
		node.getExpression().accept(this);
		println(") {");

		boolean indented = false;
		boolean wasCase = false;
		for (int i = 0; i < statements.size(); ++i) {
			Statement s = statements.get(i);

			if (s instanceof VariableDeclarationStatement) {
				if (wasCase) {
					println();
				}
				VariableDeclarationStatement vds = (VariableDeclarationStatement) s;

				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					if (fragment.getInitializer() != null) {
						printi();
						fragment.getName().accept(this);
						print(" = ");
						fragment.getInitializer().accept(this);
						println(";");
					}
				}
			} else if (s instanceof SwitchCase) {
				if (wasCase) {
					println();
				}

				if (indented) {
					indent--;
				}

				s.accept(this);

				if (i == statements.size() - 1) {
					println(" { }");
				}
				indent++;
				indented = true;
			} else if (s instanceof Block) {
				if (wasCase) {
					print(" ");
				}
				s.accept(this);
				println();
			} else {
				if (wasCase) {
					println();
				}

				s.accept(this);
			}

			wasCase = s instanceof SwitchCase;
		}

		if (indented) {
			indent--;
			indented = false;
		}

		printlni("}");

		if (!vdss.isEmpty()) {
			indent--;
			printlni("}");
		}
		println();

		return false;
	}

	private List<VariableDeclarationStatement> declarations(
			List<Statement> statements) {
		List<VariableDeclarationStatement> ret = new ArrayList<VariableDeclarationStatement>();
		for (Statement s : statements) {
			if (s instanceof VariableDeclarationStatement) {
				ret.add((VariableDeclarationStatement) s);
			}
		}
		return ret;
	}

	private int sc;

	@Override
	public boolean visit(SynchronizedStatement node) {
		printlni("{");
		indent++;
		printi("synchronized synchronized_", sc, "(");
		hardDep(node.getExpression().resolveTypeBinding());
		node.getExpression().accept(this);
		println(");");
		printi();
		node.getBody().accept(this);
		println();
		indent--;

		printlni("}");

		needsSynchronized = true;
		sc++;
		return false;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (node.getQualifier() != null
				&& !type.isSubTypeCompatible(node.getQualifier()
						.resolveTypeBinding())) {
			String sep = "";
			ITypeBinding dc = node.getQualifier().resolveTypeBinding();
			for (ITypeBinding x = type; x.getDeclaringClass() != null
					&& !x.isSubTypeCompatible(dc); x = x.getDeclaringClass()) {
				hardDep(x.getDeclaringClass());

				print(sep, TransformUtil.outerThisName(x));
				sep = "->";
			}
		} else {
			print("this");
		}
		return false;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		printi("throw ");
		node.getExpression().accept(this);
		hardDep(node.getExpression().resolveTypeBinding());
		println(";");

		return false;
	}

	private int fc;

	@Override
	public boolean visit(TryStatement node) {
		if (node.getFinally() != null) {
			needsFinally = true;
			printlni("{");
			indent++;

			printi("auto finally", fc, " = finally([&] ");
			node.getFinally().accept(this);
			println(");");
			fc++;
		}

		if (!node.catchClauses().isEmpty()) {
			printi("try ");
		} else {
			printi();
		}

		List resources = node.resources();
		if (!node.resources().isEmpty()) {
			print('(');
			for (Iterator it = resources.iterator(); it.hasNext();) {
				VariableDeclarationExpression variable = (VariableDeclarationExpression) it
						.next();
				variable.accept(this);
				if (it.hasNext()) {
					print(';');
				}
			}
			print(')');
		}

		node.getBody().accept(this);

		visitAll(node.catchClauses());

		if (node.getFinally() != null) {
			indent--;
			println();
			printi("}");
			if (node.catchClauses().isEmpty()) {
				println();
			}
		}
		println();

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		HeaderWriter hw = new HeaderWriter(root, ctx, tb, unitInfo);

		hw.writeType(node.getAST(), new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations(), iw.closures);

		if (tb.isLocal()) {
			localTypes.put(tb, iw);
		}

		return false;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (node.getType().isPrimitiveType()) {
			Code code = ((PrimitiveType) node.getType()).getPrimitiveTypeCode();
			if (code.equals(PrimitiveType.BOOLEAN)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Boolean"));
				print("java::lang::Boolean::TYPE_");
			} else if (code.equals(PrimitiveType.BYTE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Byte"));
				print("java::lang::Byte::TYPE_");
			} else if (code.equals(PrimitiveType.CHAR)) {
				hardDep(node.getAST().resolveWellKnownType(
						"java.lang.Character"));
				print("java::lang::Character::TYPE_");
			} else if (code.equals(PrimitiveType.DOUBLE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Double"));
				print("java::lang::Double::TYPE_");
			} else if (code.equals(PrimitiveType.FLOAT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Float"));
				print("java::lang::Float::TYPE_");
			} else if (code.equals(PrimitiveType.INT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Integer"));
				print("java::lang::Integer::TYPE_");
			} else if (code.equals(PrimitiveType.LONG)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Long"));
				print("java::lang::Long::TYPE_");
			} else if (code.equals(PrimitiveType.SHORT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Short"));
				print("java::lang::Short::TYPE_");
			} else if (code.equals(PrimitiveType.VOID)) {
				print("/* " + node.toString()
						+ ".class */(java::lang::Class*)0");
			}
		} else {
			hardDep(node.getType().resolveBinding());
			node.getType().accept(this);
			print("::class_");
		}
		return false;
	}

	@Override
	public boolean visit(TypeParameter node) {
		node.getName().accept(this);
		if (!node.typeBounds().isEmpty()) {
			print(" extends ");
			for (Iterator it = node.typeBounds().iterator(); it.hasNext();) {
				Type t = (Type) it.next();
				t.accept(this);
				if (it.hasNext()) {
					print(" & ");
				}
			}
		}
		return false;
	}

	@Override
	public boolean visit(UnionType node) {
		for (Iterator it = node.types().iterator(); it.hasNext();) {
			Type t = (Type) it.next();
			t.accept(this);
			if (it.hasNext()) {
				print('|');
			}
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		print(TransformUtil.ref(node.resolveBinding().getType()));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			hardDep(node.getInitializer().resolveTypeBinding());
			print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(WhileStatement node) {
		printi("while (");
		node.getExpression().accept(this);
		print(") ");
		handleLabelBody(node.getParent(), node.getBody());

		return false;
	}
}
