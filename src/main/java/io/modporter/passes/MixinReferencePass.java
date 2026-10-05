package io.modporter.passes;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import io.modporter.engine.PortContext;
import java.util.*;

/** Literal-only Sponge Mixin references. Original owner snapshot precedes every target rewrite. */
public final class MixinReferencePass {
    private static final String MIXIN = "org.spongepowered.asm.mixin.Mixin";
    private static final String AT = "org.spongepowered.asm.mixin.injection.At";
    private static final Set<String> INJECTORS = Set.of("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant");
    private final PortContext ctx;
    private final ReferenceMapper mapper;
    private String path;
    private CompilationUnit cu;
    private OwnerResolver owners;
    public MixinReferencePass(PortContext ctx) { this.ctx = ctx; this.mapper = new ReferenceMapper(ctx.resolver); }
    public void transform(String path, CompilationUnit cu) {
        this.path = path; this.cu = cu; this.owners = new OwnerResolver(cu);
        List<AnnotationExpr> annotations = new ArrayList<>(cu.findAll(AnnotationExpr.class));
        Map<TypeDeclaration<?>, List<String>> targets = new IdentityHashMap<>();
        for (AnnotationExpr a : annotations) {
            if (!confirmed(a, MIXIN)) continue;
            if (!(a.getParentNode().orElse(null) instanceof TypeDeclaration<?> type)) continue;
            List<String> result = new ArrayList<>(); boolean ok = true;
            for (Expression value : targetValues(a)) {
                String owner = targetOwner(value);
                if (owner == null) { ok = false; todo(value, "@Mixin target 不是可确认的源类字面量，保留并人工核对"); }
                else result.add(owner);
            }
            targets.put(type, ok && !result.isEmpty() ? List.copyOf(result) : List.of());
        }
        // Process method selectors first, while targets strings and class literals are still source names.
        for (AnnotationExpr a : annotations) {
            String simple = a.getName().getIdentifier();
            if (!INJECTORS.contains(simple) || !confirmed(a, "org.spongepowered.asm.mixin.injection." + simple)) continue;
            if (remapDisabled(a)) { todo(a, "remap=false：本工具保留该注入点的全部字符串"); continue; }
            TypeDeclaration<?> type = owners.enclosingType(a);
            List<String> sourceTargets = targets.getOrDefault(type, List.of());
            if (type != null && type.getAnnotations().stream().anyMatch(m -> confirmed(m, MIXIN) && remapDisabled(m))) {
                todo(a, "所属 @Mixin remap=false，注入字符串保持原样"); continue;
            }
            if (!(a instanceof NormalAnnotationExpr normal)) continue;
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("method")) {
                    for (Expression e : flatten(pair.getValue())) rewriteMethod(e, sourceTargets);
                }
            }
        }
        // @At may be nested in at arrays or slices; confirmation is independent of the outer annotation.
        for (AnnotationExpr a : annotations) {
            if (!confirmed(a, AT) || !(a instanceof NormalAnnotationExpr normal)) continue;
            if (hasDisabledRemapAncestor(a)) { todo(a, "remap=false 的 @At 引用保持原样"); continue; }
            String atKind = normal.getPairs().stream().filter(p -> p.getNameAsString().equals("value"))
                    .map(MemberValuePair::getValue).filter(StringLiteralExpr.class::isInstance)
                    .map(e -> ((StringLiteralExpr)e).asString()).findFirst().orElse("");
            for (MemberValuePair p : normal.getPairs()) {
                if (!p.getNameAsString().equals("target")) continue;
                if (!Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD").contains(atKind)) {
                    todo(p, "该 @At 类型不是已支持的 INVOKE/INVOKE_ASSIGN/FIELD，target 保留"); continue;
                }
                rewriteAt(p.getValue(), atKind.equals("FIELD"));
            }
        }
        for (AnnotationExpr a : annotations) {
            if (!confirmed(a, MIXIN)) continue;
            if (remapDisabled(a)) { todo(a, "@Mixin remap=false，字符串目标保留"); continue; }
            for (Expression e : targetValues(a)) {
                if (e instanceof ClassExpr cls && cls.getType() instanceof com.github.javaparser.ast.type.ClassOrInterfaceType type
                        && type.getScope().isPresent()) {
                    String source = targetOwner(e), mapped = source == null ? null : mapper.mapClass(source);
                    if (mapped == null) todo(e, "@Mixin 全限定类字面量无完整映射，需要人工核对");
                    else if (!source.equals(mapped)) cls.setType(com.github.javaparser.StaticJavaParser.parseType(
                            MixinDescriptors.internalToFqcnLookup(mapped)));
                    continue;
                }
                if (!(e instanceof StringLiteralExpr literal)) continue;
                String source = targetOwner(e), mapped = source == null ? null : mapper.mapClass(source);
                if (mapped == null) todo(e, "@Mixin targets 类名无完整映射，保留原字符串");
                else if (!source.equals(mapped)) {
                    literal.setString(mapped.replace('/', '.'));
                    info(e, "@Mixin targets " + source + " -> " + mapped);
                }
            }
        }
    }
    private boolean confirmed(AnnotationExpr a, String fqcn) {
        String name = a.getNameAsString();
        if (name.equals(fqcn)) return true;
        if (name.contains(".")) return false;
        if (!fqcn.endsWith("." + name)) return false;
        if (cu.findAll(TypeDeclaration.class).stream().anyMatch(t -> t.getNameAsString().equals(name))) return false;
        List<String> imports = cu.getImports().stream().filter(i -> !i.isStatic() && !i.isAsterisk())
                .map(ImportDeclaration::getNameAsString).filter(n -> n.endsWith("." + name)).distinct().toList();
        return imports.size() == 1 && imports.get(0).equals(fqcn);
    }
    private List<Expression> targetValues(AnnotationExpr a) {
        List<Expression> values = new ArrayList<>();
        if (a instanceof SingleMemberAnnotationExpr s) values.addAll(flatten(s.getMemberValue()));
        else if (a instanceof NormalAnnotationExpr n) for (MemberValuePair p : n.getPairs()) {
            if (p.getNameAsString().equals("value") || p.getNameAsString().equals("targets")) values.addAll(flatten(p.getValue()));
        }
        return values;
    }
    private static List<Expression> flatten(Expression e) {
        return e instanceof ArrayInitializerExpr a ? new ArrayList<>(a.getValues()) : List.of(e);
    }
    private String targetOwner(Expression e) {
        if (e instanceof StringLiteralExpr s) {
            String written = s.asString();
            String internal = written.replace('.', '/');
            return MixinDescriptors.fqcnRoundTripOk(internal) ? internal : null;
        }
        if (e instanceof ClassExpr c) {
            String fqcn = owners.typeName(c.getType(), c);
            return fqcn == null ? null : MixinDescriptors.fqcnToInternal(fqcn);
        }
        return null;
    }
    private void rewriteMethod(Expression e, List<String> sourceTargets) {
        if (!(e instanceof StringLiteralExpr literal)) { todo(e, "method 为动态表达式，保持原样"); return; }
        String raw = literal.asString();
        var full = MixinDescriptors.parseAtTarget(raw);
        if (full != null) {
            if (!full.member().method()) { todo(e, "method 属性不能使用字段描述符"); return; }
            var mapped = mapper.member(full.ownerInternal(), full.member().name(), full.member().descriptor(), true);
            if (mapped == null) { todo(e, "带owner的method引用无法完整迁移，保持原字符串"); return; }
            String result = "L" + mapped.owner() + ";" + mapped.name() + mapped.descriptor();
            if (!raw.equals(result)) { literal.setString(result); info(e, raw + " -> " + result); }
            return;
        }
        var ref = MixinDescriptors.parseMemberRef(raw);
        if (ref == null || !ref.method() || sourceTargets.isEmpty()) {
            todo(e, "method 缺少明确owner/含通配符/构造器/无效描述符，保持原字符串"); return;
        }
        Set<String> results = new HashSet<>();
        for (String owner : sourceTargets) {
            var mapped = mapper.member(owner, ref.name(), ref.descriptor(), true);
            if (mapped == null) { todo(e, "method 的owner/成员/完整签名缺少映射或含语义提示，保持原字符串"); return; }
            results.add(mapped.name() + mapped.descriptor());
        }
        if (results.size() != 1) { todo(e, "多个Mixin target的成员迁移结果不一致，保留原字符串"); return; }
        String result = results.iterator().next();
        if (!result.equals(raw)) { literal.setString(result); info(e, raw + " -> " + result); }
    }
    private void rewriteAt(Expression e, boolean field) {
        if (!(e instanceof StringLiteralExpr literal)) { todo(e, "@At target 为动态表达式，保持原样"); return; }
        String raw = literal.asString();
        var ref = MixinDescriptors.parseAtTarget(raw);
        if (ref == null || ref.member().method() == field) { todo(e, "@At target 缺少owner或与注入种类不符，保持原字符串"); return; }
        var mapped = mapper.member(ref.ownerInternal(), ref.member().name(), ref.member().descriptor(), !field);
        if (mapped == null) { todo(e, "@At target 无法完整迁移，保留owner/成员/描述符整条引用"); return; }
        String result = "L" + mapped.owner() + ";" + mapped.name() + (field ? ":" : "") + mapped.descriptor();
        if (!result.equals(raw)) { literal.setString(result); info(e, raw + " -> " + result); }
    }
    private static boolean remapDisabled(AnnotationExpr a) {
        if (!(a instanceof NormalAnnotationExpr n)) return false;
        return n.getPairs().stream().anyMatch(p -> p.getNameAsString().equals("remap")
                && (!(p.getValue() instanceof BooleanLiteralExpr b) || !b.getValue()));
    }
    private boolean hasDisabledRemapAncestor(AnnotationExpr at) {
        for (Node n = at; n != null; n = n.getParentNode().orElse(null)) {
            if (n instanceof AnnotationExpr a && remapDisabled(a)) return true;
            if (n instanceof TypeDeclaration<?> t) {
                return t.getAnnotations().stream().anyMatch(a -> confirmed(a, MIXIN) && remapDisabled(a));
            }
        }
        return false;
    }
    private void todo(Node e, String message) { ctx.todo(path, e.getBegin().map(p -> p.line).orElse(null), "mixin-reference", message); }
    private void info(Node e, String message) { ctx.info(path, e.getBegin().map(p -> p.line).orElse(null), "mixin-reference", message); }
}
