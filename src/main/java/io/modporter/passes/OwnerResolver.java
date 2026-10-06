package io.modporter.passes;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import java.util.*;

/** Source-only lexical owner lookup. No capitalization guesses, global variable table or classpath inference. */
final class OwnerResolver {
    record Owners(List<String> names, boolean known) {
        static Owners unknown() { return new Owners(List.of(), false); }
        static Owners of(String name) { return name == null ? unknown() : new Owners(List.of(name), true); }
    }
    private final CompilationUnit cu;
    private final Map<String, String> imports = new HashMap<>();
    private final Set<String> ambiguousImports = new HashSet<>();
    OwnerResolver(CompilationUnit cu) {
        this.cu = cu;
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() || imp.isStatic() || imp.isModule()) continue;
            String fqcn = imp.getNameAsString();
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            if (imports.putIfAbsent(simple, fqcn) != null) ambiguousImports.add(simple);
        }
    }
    /** Null at anonymous/enum/record boundaries: never accidentally use an enclosing class. */
    TypeDeclaration<?> enclosingType(Node node) {
        for (Node n = node; n != null; n = n.getParentNode().orElse(null)) {
            if (n instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent()) return null;
            if (n instanceof EnumConstantDeclaration) return null;
            if (n instanceof TypeDeclaration<?> t) return t;
        }
        return null;
    }
    Owners directSupertypes(Node node) {
        TypeDeclaration<?> type = enclosingType(node);
        if (!(type instanceof ClassOrInterfaceDeclaration c)) return Owners.unknown();
        List<String> result = new ArrayList<>();
        for (ClassOrInterfaceType t : c.getExtendedTypes()) {
            String name = typeName(t, c); if (name == null) return Owners.unknown(); result.add(name);
        }
        for (ClassOrInterfaceType t : c.getImplementedTypes()) {
            String name = typeName(t, c); if (name == null) return Owners.unknown(); result.add(name);
        }
        return new Owners(result, true);
    }
    String typeName(Type type, Node at) {
        if (!(type instanceof ClassOrInterfaceType t)) return null;
        String written = t.getNameWithScope();
        String first = written.contains(".") ? written.substring(0, written.indexOf('.')) : written;
        // A type parameter/local class can shadow an imported type. Do not infer its bounds.
        for (Node n = at; n != null; n = n.getParentNode().orElse(null)) {
            if (n instanceof com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters<?> p
                    && p.getTypeParameters().stream().anyMatch(v -> v.getNameAsString().equals(first))) return null;
        }
        if (cu.findAll(TypeDeclaration.class).stream().anyMatch(tdecl -> tdecl.getNameAsString().equals(first))) {
            return "<local-type>:" + written;
        }
        if (ambiguousImports.contains(first)) return null;
        String imported = imports.get(first);
        if (imported != null) return imported + written.substring(first.length());
        if (written.contains(".")) return written;
        // Unimported simple names (same package / java.lang / wildcard) are not proved owners.
        return null;
    }
    Owners receiver(Expression scope, Node at) {
        if (scope == null) {
            if (at instanceof MethodCallExpr call && cu.getImports().stream().anyMatch(i -> i.isStatic()
                    && (i.isAsterisk() || i.getNameAsString().endsWith("." + call.getNameAsString())))) return Owners.unknown();
            Owners base = directSupertypes(at);
            return base.names().isEmpty() ? Owners.unknown() : base;
        }
        if (scope instanceof EnclosedExpr e) return receiver(e.getInner(), at);
        if (scope instanceof ThisExpr t) return t.getTypeName().isPresent() ? Owners.unknown() : directSupertypes(at);
        if (scope instanceof SuperExpr s) return s.getTypeName().isPresent() ? Owners.unknown() : directSupertypes(at);
        if (scope instanceof NameExpr ne) {
            Binding binding = binding(ne.getNameAsString(), ne);
            if (binding.found) return Owners.of(typeName(binding.type, binding.at));
            // Only explicit imports may prove a static owner, after checking value shadowing.
            String name = ne.getNameAsString();
            if (ambiguousImports.contains(name)) return Owners.unknown();
            if (cu.findAll(TypeDeclaration.class).stream().anyMatch(t -> t.getNameAsString().equals(name))) return Owners.unknown();
            if (!imports.containsKey(name)) return Owners.unknown();
            return Owners.of(typeName(new ClassOrInterfaceType(null, name), ne));
        }
        if (scope instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr t && t.getTypeName().isEmpty()) {
            Binding b = field(fa.getNameAsString(), enclosingType(fa));
            return b.found ? Owners.of(typeName(b.type, b.at)) : Owners.unknown();
        }
        if (scope instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isEmpty()) return Owners.of(typeName(o.getType(), o));
        if (scope instanceof CastExpr c) return Owners.of(typeName(c.getType(), c));
        return Owners.unknown();
    }
    boolean isValueName(NameExpr name) {
        return binding(name.getNameAsString(), name).found;
    }
    /**
     * 词法绑定（变量/参数/字段）的书面类型解析为 FQCN。
     * 书面类型是点分名时原样返回；简单名经显式 import 解析；未导入简单名
     * （java.lang/同包/通配 import）不做猜测，返回 null。流作用域屏障同样返回 null。
     */
    String resolveBindingType(String name, Node use) {
        String written = bindingTypeName(name, use);
        if (written == null) return null;
        if (written.indexOf('.') > 0) return written;
        if (ambiguousImports.contains(written)) return null;
        if (cu.findAll(TypeDeclaration.class).stream().anyMatch(t -> t.getNameAsString().equals(written))) return null;
        return imports.get(written);
    }
    /** 词法绑定的书面类型原样字符串（含基本类型关键字）；未绑定/屏障/无类型返回 null。 */
    String bindingTypeName(String name, Node use) {
        Binding b = binding(name, use);
        return b != null && b.found && b.type != null ? b.type.asString() : null;
    }
    boolean declaresMethod(Node at, String name) {
        TypeDeclaration<?> t = enclosingType(at);
        return t != null && t.getMethodsByName(name).size() > 0;
    }
    boolean declaresField(Node at, String name) { return field(name, enclosingType(at)).found; }
    private record Binding(boolean found, Type type, Node at) {
        static Binding absent() { return new Binding(false, null, null); }
        static Binding blocked(Node at) { return new Binding(true, null, at); }
    }
    private Binding field(String name, TypeDeclaration<?> type) {
        if (type == null) return Binding.absent();
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof FieldDeclaration f) {
                for (VariableDeclarator v : f.getVariables()) if (v.getNameAsString().equals(name)) return new Binding(true, v.getType(), v);
            }
        }
        if (type instanceof RecordDeclaration r) return parameter(name, r.getParameters());
        return Binding.absent();
    }
    private Binding parameter(String name, List<Parameter> parameters) {
        for (Parameter p : parameters) if (p.getNameAsString().equals(name)) return new Binding(true, p.getType(), p);
        return Binding.absent();
    }
    private Binding variable(String name, Expression expr) {
        if (expr instanceof VariableDeclarationExpr decl) {
            for (VariableDeclarator v : decl.getVariables()) if (v.getNameAsString().equals(name)) return new Binding(true, v.getType(), v);
        }
        return Binding.absent();
    }
    private Binding binding(String name, Node use) {
        Node child = use;
        for (Node n = use.getParentNode().orElse(null); n != null; child = n, n = n.getParentNode().orElse(null)) {
            Binding b = Binding.absent();
            if (n instanceof BlockStmt block) {
                int index = block.getStatements().indexOf(child);
                if (index < 0) return Binding.blocked(n);
                for (int i = index; i >= 0; i--) {
                    Statement st = block.getStatement(i);
                    if (st instanceof ExpressionStmt es) {
                        b = variable(name, es.getExpression());
                        if (b.found) {
                            // A use inside its own multi-declaration statement needs finer ordering.
                            return i == index ? Binding.blocked(n) : b;
                        }
                    }
                }
            }
            if (n instanceof LambdaExpr l) b = parameter(name, l.getParameters());
            else if (n instanceof CatchClause c) b = parameter(name, List.of(c.getParameter()));
            else if (n instanceof CallableDeclaration<?> c) b = parameter(name, c.getParameters());
            else if (n instanceof ForEachStmt f) b = variable(name, f.getVariable());
            else if (n instanceof ForStmt f) {
                for (Expression init : f.getInitialization()) {
                    b = variable(name, init); if (b.found) break;
                }
            } else if (n instanceof TryStmt t && child == t.getTryBlock()) {
                for (Expression resource : t.getResources()) {
                    b = variable(name, resource); if (b.found) break;
                }
            }
            if (b.found) return b;
            // Flow-scoped pattern variables and switch groups are deliberately not guessed.
            if (n instanceof SwitchEntry || n instanceof IfStmt || n instanceof WhileStmt
                    || n instanceof ConditionalExpr || n instanceof BinaryExpr) {
                boolean pattern = n.findAll(TypePatternExpr.class).stream().anyMatch(p -> p.getNameAsString().equals(name));
                if (pattern || n instanceof SwitchEntry) return Binding.blocked(n);
            }
            if (n instanceof SwitchStmt || n instanceof SwitchExpr) return Binding.blocked(n);
            if (n instanceof ObjectCreationExpr o && o.getAnonymousClassBody().isPresent()) return Binding.blocked(n);
            if (n instanceof EnumConstantDeclaration) return Binding.blocked(n);
            if (n instanceof TypeDeclaration<?> t) {
                b = field(name, t);
                if (b.found) return b;
                // Inherited/enclosing fields may shadow static type imports. Conservative barrier.
                if (t.getParentNode().orElse(null) instanceof CompilationUnit
                        && t instanceof ClassOrInterfaceDeclaration c
                        && c.getExtendedTypes().isEmpty() && c.getImplementedTypes().isEmpty()) return Binding.absent();
                return Binding.blocked(t);
            }
        }
        return Binding.absent();
    }
}
