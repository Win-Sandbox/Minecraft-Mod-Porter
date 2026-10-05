package io.modporter.passes;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.stmt.Statement;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.VersionMappings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据驱动的注解属性迁移。
 *
 * 必须在 JavaSourcePass 的 import/类型改名之前调用：
 *   new AnnotationMigrationPass(ctx).transform(relPath, cu);
 * 本 Pass 只处理 annotations.json 声明过的注解，绝不按裸名猜测。
 */
public final class AnnotationMigrationPass {

    private static final String TODO_PREFIX = " TODO [modporter] ";

    private final PortContext ctx;
    private final MappingResolver resolver;
    private final Set<String> emittedTodos = new HashSet<>();
    private String relPath;

    public AnnotationMigrationPass(PortContext ctx) {
        this.ctx = ctx;
        this.resolver = ctx.resolver;
    }

    /** 转换一个已解析的编译单元；失败只报告，不抛出。 */
    public void transform(String relPath, CompilationUnit cu) {
        if (cu == null) return;
        this.relPath = relPath;
        ImportIndex imports = new ImportIndex(cu);
        List<MatchedAnnotation> matches = new ArrayList<>();
        for (Map.Entry<String, VersionMappings.AnnotationForm> e
                : ctx.source().annotationForms.entrySet()) {
            String annoIr = e.getKey();
            VersionMappings.AnnotationForm source = e.getValue();
            VersionMappings.AnnotationForm target = ctx.target().annotationForms.get(annoIr);
            if (target == null) continue;
            for (AnnotationExpr anno : cu.findAll(AnnotationExpr.class)) {
                String dotted = dottedName(anno.getName());
                if (!imports.resolvesTo(dotted, source.className)) continue;
                matches.add(new MatchedAnnotation(annoIr, source, target, anno, dotted));
            }
        }
        for (MatchedAnnotation m : matches) {
            rewriteAnnotationClass(m, cu, imports);
            rewriteAttributes(m, cu, imports);
        }
        reportMissingReverseAttributes(matches);
    }

    // ---------------------------------------------------------- name / import

    private void rewriteAnnotationClass(MatchedAnnotation m, CompilationUnit cu, ImportIndex imports) {
        if (m.source.className.equals(m.target.className)) return;
        boolean fullyQualified = m.dotted.equals(m.source.className)
                && m.dotted.indexOf('.') > 0
                && Character.isLowerCase(m.dotted.charAt(0));
        String targetName = fullyQualified
                ? m.target.className
                : lastSegment(m.target.className);
        boolean targetSimpleShadowed = !fullyQualified
                && (localTypeNamed(cu, targetName)
                || conflictingSimpleImport(cu, targetName, m.target.className));
        m.anno.setName(buildName(targetSimpleShadowed ? m.target.className : targetName));

        if (fullyQualified || targetSimpleShadowed) return;

        if (m.dotted.indexOf('.') < 0) {
            ImportDeclaration exact = imports.exactImport(m.source.className);
            if (exact != null) {
                exact.setName(buildName(m.target.className));
            } else {
                addImportIfNeeded(cu, m.target.className);
            }
            return;
        }

        String first = firstSegment(m.dotted);
        ImportDeclaration outer = imports.simpleImport(first);
        if (outer != null) {
            String outerFqcn = outer.getNameAsString();
            if (m.source.className.equals(outerFqcn)) {
                outer.setName(buildName(m.target.className));
                return;
            }
            if (m.source.className.startsWith(outerFqcn + ".")
                    && !outerSimpleUsedElsewhere(cu, first, m.source.className)) {
                outer.setName(buildName(m.target.className));
                return;
            }
        }
        addImportIfNeeded(cu, m.target.className);
    }

    private boolean outerSimpleUsedElsewhere(CompilationUnit cu, String simple, String sourceAnnoFqcn) {
        for (AnnotationExpr anno : cu.findAll(AnnotationExpr.class)) {
            String dotted = dottedName(anno.getName());
            if (dotted.startsWith(simple + ".") || dotted.equals(simple)) {
                if (!resolvesToAnnotationFqcn(anno, sourceAnnoFqcn)) return true;
            }
        }
        for (NameExpr expr : cu.findAll(NameExpr.class)) {
            if (expr.getNameAsString().equals(simple)) return true;
        }
        for (com.github.javaparser.ast.type.ClassOrInterfaceType type
                : cu.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if (name.equals(simple) || name.startsWith(simple + ".")) return true;
        }
        for (ClassOrInterfaceDeclaration declaration
                : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        for (com.github.javaparser.ast.body.EnumDeclaration declaration
                : cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        for (com.github.javaparser.ast.body.AnnotationDeclaration declaration
                : cu.findAll(com.github.javaparser.ast.body.AnnotationDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        return false;
    }

    private boolean resolvesToAnnotationFqcn(AnnotationExpr anno, String fqcn) {
        CompilationUnit cu = compilationUnit(anno);
        ImportIndex imports = new ImportIndex(cu);
        return imports.resolvesTo(dottedName(anno.getName()), fqcn);
    }

    private static CompilationUnit compilationUnit(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof CompilationUnit) return (CompilationUnit) n;
            n = n.getParentNode().orElse(null);
        }
        return null;
    }

    private static boolean localTypeNamed(CompilationUnit cu, String simple) {
        for (ClassOrInterfaceDeclaration declaration
                : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        for (com.github.javaparser.ast.body.EnumDeclaration declaration
                : cu.findAll(com.github.javaparser.ast.body.EnumDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        for (com.github.javaparser.ast.body.AnnotationDeclaration declaration
                : cu.findAll(com.github.javaparser.ast.body.AnnotationDeclaration.class)) {
            if (declaration.getNameAsString().equals(simple)) return true;
        }
        return false;
    }
    private static boolean conflictingSimpleImport(CompilationUnit cu, String simple, String expectedFqcn) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && !imp.isAsterisk()
                    && lastSegment(imp.getNameAsString()).equals(simple)
                    && !imp.getNameAsString().equals(expectedFqcn)) {
                return true;
            }
        }
        return false;
    }

    private void addImportIfNeeded(CompilationUnit cu, String fqcn) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && !imp.isAsterisk() && imp.getNameAsString().equals(fqcn)) return;
        }
        cu.addImport(fqcn);
    }

    // ---------------------------------------------------------- attributes

    private void rewriteAttributes(MatchedAnnotation m, CompilationUnit cu, ImportIndex imports) {
        if (m.anno instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) m.anno;
            List<MemberValuePair> pairs = new ArrayList<>(normal.getPairs());
            for (MemberValuePair pair : pairs) {
                VersionMappings.AnnotationAttributeForm source = sourceAttribute(m.source, pair.getNameAsString());
                if (source == null) continue;
                VersionMappings.AnnotationAttributeForm target = m.target.attributes.get(source.ir);
                if (target == null) {
                    deleteOrKeepAttribute(m, normal, pair, source, imports);
                } else {
                    if (!source.name.equals(target.name)) {
                        pair.setName(new com.github.javaparser.ast.expr.SimpleName(target.name));
                    }
                    if (source.valueClassIr != null && target.valueClassIr != null) {
                        transformEnumValue(m, pair, source, target, imports, cu);
                    } else if (source.valueClassIr != null) {
                        ctx.todo(relPath, line(pair), "annotation-attribute",
                                "注解属性 " + source.name + " 在目标版本不再是枚举值，保留待人工迁移");
                    }
                }
            }
        } else if (m.anno instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr single = (SingleMemberAnnotationExpr) m.anno;
            VersionMappings.AnnotationAttributeForm source = sourceAttribute(m.source, "value");
            if (source != null) {
                VersionMappings.AnnotationAttributeForm target = m.target.attributes.get(source.ir);
                if (target != null && source.valueClassIr != null) {
                    transformSingleValue(m, single, source, target, imports, cu);
                }
            }
        }
    }

    private void deleteOrKeepAttribute(MatchedAnnotation m, NormalAnnotationExpr normal,
                                       MemberValuePair pair,
                                       VersionMappings.AnnotationAttributeForm source,
                                       ImportIndex imports) {
        EnumConstant constant = resolveEnumConstant(pair.getValue(), source, imports);
        if (constant != null && knownConstant(constant)) {
            normal.getPairs().remove(pair);
            ctx.info(relPath, line(pair), "annotation-attribute",
                    "删除注解属性 " + source.name + "（目标版本已移除，且源值 "
                            + constant.name + " 已核实）");
            return;
        }
        String message = "目标版本缺少注解属性 " + source.name
                + "，但源值不是已核实常量，保留待人工迁移";
        ctx.todo(relPath, line(pair), "annotation-attribute", message);
        todo(pair, message);
    }

    private void transformEnumValue(MatchedAnnotation m, MemberValuePair pair,
                                    VersionMappings.AnnotationAttributeForm source,
                                    VersionMappings.AnnotationAttributeForm target,
                                    ImportIndex imports, CompilationUnit cu) {
        EnumConstant constant = resolveEnumConstant(pair.getValue(), source, imports);
        if (constant == null) {
            ctx.todo(relPath, line(pair), "annotation-attribute",
                    "注解属性 " + source.name + " 的枚举值无法解析，保留待人工迁移");
            return;
        }
        List<MappingResolver.MemberCandidate> candidates =
                resolver.resolveMemberForOwner(constant.ownerFqcn, constant.name);
        if (candidates.isEmpty() || !MappingResolver.unambiguous(candidates)) {
            ctx.todo(relPath, line(pair), "annotation-attribute",
                    "注解属性 " + source.name + " 的枚举常量 " + constant.name
                            + " 映射不明确，保留待人工迁移");
            return;
        }
        String targetConstant = candidates.get(0).targetName;
        // 目标宿主必须与成员候选同 IR；不能再次经 FQCN 反查，避免多 IR/primary 错位。
        String targetOwner = resolver.targetClass(candidates.get(0).classIr);
        if (targetOwner == null) {
            ctx.todo(relPath, line(pair), "annotation-attribute",
                    "注解属性 " + source.name + " 的枚举宿主在目标版本缺失，保留待人工迁移");
            return;
        }
        if (targetOwner.equals(constant.ownerFqcn)
                && targetConstant.equals(constant.name)) {
            return;
        }
        // 跨版本变化时目标宿主一律使用 FQCN：嵌套类型简单链依赖外层类型可解析，
        // 在共用旧 Mod import、本地同名 EventBusSubscriber/Bus 或冲突 import 时均不安全。
        com.github.javaparser.ast.expr.Expression replacement = buildEnumAccess(
                targetOwner, targetConstant, true);
        pair.setValue(replacement);
        ctx.info(relPath, line(pair), "annotation-attribute",
                source.name + ": " + constant.name + " -> " + targetConstant);
    }

    private void transformSingleValue(MatchedAnnotation m, SingleMemberAnnotationExpr single,
                                      VersionMappings.AnnotationAttributeForm source,
                                      VersionMappings.AnnotationAttributeForm target,
                                      ImportIndex imports, CompilationUnit cu) {
        if (!source.name.equals(target.name)) {
            ctx.todo(relPath, line(single), "annotation-attribute",
                    "单值注解属性 value 在目标版本改名为 " + target.name
                            + "，需人工改为成对属性形式");
            return;
        }
        EnumConstant constant = resolveEnumConstant(single.getMemberValue(), source, imports);
        if (constant == null) {
            ctx.todo(relPath, line(single), "annotation-attribute",
                    "单值注解 value 的枚举值无法解析，保留待人工迁移");
            return;
        }
        List<MappingResolver.MemberCandidate> candidates =
                resolver.resolveMemberForOwner(constant.ownerFqcn, constant.name);
        if (candidates.isEmpty() || !MappingResolver.unambiguous(candidates)) {
            ctx.todo(relPath, line(single), "annotation-attribute",
                    "单值注解 value 的枚举常量映射不明确，保留待人工迁移");
            return;
        }
        // 目标宿主必须与成员候选同 IR；不能再次经 FQCN 反查，避免多 IR/primary 错位。
        String targetOwner = resolver.targetClass(candidates.get(0).classIr);
        if (targetOwner == null) {
            ctx.todo(relPath, line(single), "annotation-attribute",
                    "单值注解 value 的枚举宿主在目标版本缺失，保留待人工迁移");
            return;
        }
        if (targetOwner.equals(constant.ownerFqcn)
                && candidates.get(0).targetName.equals(constant.name)) {
            return;
        }
        // 跨版本变化时目标枚举宿主一律用 FQCN，不依赖任何简单名 import。
        single.setMemberValue(buildEnumAccess(targetOwner,
                candidates.get(0).targetName, true));
    }

    private void reportMissingReverseAttributes(List<MatchedAnnotation> matches) {
        for (MatchedAnnotation m : matches) {
            for (VersionMappings.AnnotationAttributeForm target : m.target.attributes.values()) {
                if (!m.source.attributes.containsKey(target.ir)) {
                    ctx.todo(relPath, line(m.anno), "annotation-attribute",
                            "目标版本存在注解属性 " + target.name
                                    + "，源版本没有对应属性，不能推断默认值，保留待人工迁移");
                }
            }
        }
    }

    // ---------------------------------------------------------- enum parsing

    private EnumConstant resolveEnumConstant(com.github.javaparser.ast.expr.Expression expr,
                                             VersionMappings.AnnotationAttributeForm attr,
                                             ImportIndex imports) {
        String valueClassIr = attr.valueClassIr;
        if (valueClassIr == null) return null;
        VersionMappings.ClassEntry ownerEntry = ctx.source().classesByIr.get(valueClassIr);
        if (ownerEntry == null) return null;
        String expectedOwner = ownerEntry.fqcn;
        List<String> chain = expressionChain(expr);
        if (chain == null || chain.size() < 2) return null;
        String constant = chain.get(chain.size() - 1);
        List<String> owner = chain.subList(0, chain.size() - 1);
        String ownerDotted = String.join(".", owner);
        boolean fullyQualified = owner.size() > 1
                && Character.isLowerCase(owner.get(0).charAt(0));
        String resolved;
        if (fullyQualified) {
            resolved = ownerDotted;
        } else if (owner.size() == 1) {
            resolved = imports.simpleFqcn(owner.get(0));
        } else {
            String first = owner.get(0);
            String imported = imports.simpleFqcn(first);
            if (imported == null) return null;
            resolved = imported + "." + String.join(".", owner.subList(1, owner.size()));
        }
        if (!expectedOwner.equals(resolved)) return null;
        return new EnumConstant(resolved, constant, fullyQualified);
    }

    private boolean knownConstant(EnumConstant constant) {
        List<MappingResolver.MemberCandidate> candidates =
                resolver.resolveMemberForOwner(constant.ownerFqcn, constant.name);
        return !candidates.isEmpty() && MappingResolver.unambiguous(candidates);
    }

    private static com.github.javaparser.ast.expr.Expression buildEnumAccess(
            String ownerFqcn, String constant, boolean fullyQualified) {
        String dotted = fullyQualified
                ? ownerFqcn + "." + constant
                : simpleChain(ownerFqcn) + "." + constant;
        com.github.javaparser.ast.expr.Expression expr = null;
        for (String segment : dotted.split("\\.")) {
            if (expr == null) {
                expr = new NameExpr(segment);
            } else {
                expr = new FieldAccessExpr(expr, segment);
            }
        }
        return expr;
    }

    private static String simpleChain(String fqcn) {
        String[] parts = fqcn.split("\\.");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (Character.isUpperCase(part.charAt(0))) out.add(part);
        }
        return String.join(".", out);
    }

    private static List<String> expressionChain(com.github.javaparser.ast.expr.Expression expr) {
        if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            List<String> out = expressionChain(field.getScope());
            if (out == null) return null;
            out.add(field.getNameAsString());
            return out;
        }
        if (expr instanceof NameExpr) {
            NameExpr name = (NameExpr) expr;
            List<String> out = new ArrayList<>();
            out.add(name.getNameAsString());
            return out;
        }
        return null;
    }

    // ---------------------------------------------------------- helpers

    private static VersionMappings.AnnotationAttributeForm sourceAttribute(
            VersionMappings.AnnotationForm form, String name) {
        for (VersionMappings.AnnotationAttributeForm attr : form.attributes.values()) {
            if (attr.name.equals(name)) return attr;
        }
        return null;
    }

    private static String dottedName(Name name) {
        List<String> parts = new ArrayList<>();
        Name n = name;
        while (n != null) {
            parts.add(0, n.getIdentifier());
            n = n.getQualifier().orElse(null);
        }
        return String.join(".", parts);
    }

    private static Name buildName(String dotted) {
        Name n = null;
        for (String segment : dotted.split("\\.")) {
            n = new Name(n, segment);
        }
        return n;
    }

    private static String firstSegment(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    private static String lastSegment(String dotted) {
        int dot = dotted.lastIndexOf('.');
        return dot < 0 ? dotted : dotted.substring(dot + 1);
    }

    private static int line(Node node) {
        return node.getRange().map(r -> r.begin.line).orElse(-1);
    }

    private void todo(Node node, String message) {
        ctx.todo(relPath, line(node), "annotation-attribute", message);
        Node anchor = node;
        while (anchor != null && !(anchor instanceof Statement)
                && !(anchor instanceof BodyDeclaration)) {
            anchor = anchor.getParentNode().orElse(null);
        }
        if (anchor == null) anchor = node;
        String key = System.identityHashCode(anchor) + "|" + message;
        if (!emittedTodos.add(key)) return;
        if (anchor.getComment().isEmpty()) {
            anchor.setComment(new LineComment(TODO_PREFIX + message));
        }
    }

    // ---------------------------------------------------------- value types

    private static final class MatchedAnnotation {
        final String annoIr;
        final VersionMappings.AnnotationForm source;
        final VersionMappings.AnnotationForm target;
        final AnnotationExpr anno;
        final String dotted;

        MatchedAnnotation(String annoIr, VersionMappings.AnnotationForm source,
                          VersionMappings.AnnotationForm target, AnnotationExpr anno, String dotted) {
            this.annoIr = annoIr;
            this.source = source;
            this.target = target;
            this.anno = anno;
            this.dotted = dotted;
        }
    }

    private static final class EnumConstant {
        final String ownerFqcn;
        final String name;
        final boolean fullyQualified;

        EnumConstant(String ownerFqcn, String name, boolean fullyQualified) {
            this.ownerFqcn = ownerFqcn;
            this.name = name;
            this.fullyQualified = fullyQualified;
        }
    }

    private static final class ImportIndex {
        private final CompilationUnit cu;
        private final Map<String, String> simpleToExact = new HashMap<>();

        ImportIndex(CompilationUnit cu) {
            this.cu = cu;
            if (cu == null) return;
            for (ImportDeclaration imp : cu.getImports()) {
                if (imp.isStatic() || imp.isAsterisk()) continue;
                String fqcn = imp.getNameAsString();
                simpleToExact.putIfAbsent(lastSegment(fqcn), fqcn);
            }
        }

        String simpleFqcn(String simple) {
            return simpleToExact.get(simple);
        }

        ImportDeclaration simpleImport(String simple) {
            String fqcn = simpleToExact.get(simple);
            return fqcn == null ? null : findImport(fqcn);
        }

        ImportDeclaration exactImport(String fqcn) {
            return findImport(fqcn);
        }

        private ImportDeclaration findImport(String fqcn) {
            if (cu == null || fqcn == null) return null;
            for (ImportDeclaration imp : cu.getImports()) {
                if (!imp.isStatic() && !imp.isAsterisk()
                        && imp.getNameAsString().equals(fqcn)) {
                    return imp;
                }
            }
            return null;
        }

        boolean resolvesTo(String dotted, String fqcn) {
            if (dotted.equals(fqcn)) return true;
            if (dotted.indexOf('.') < 0) {
                return fqcn.equals(simpleToExact.get(dotted));
            }
            String first = firstSegment(dotted);
            String imported = simpleToExact.get(first);
            if (imported == null) return false;
            String candidate = imported + "." + dotted.substring(first.length() + 1);
            return candidate.equals(fqcn);
        }
    }
}
