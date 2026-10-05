package io.modporter.passes;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.MappingResolver.ClassResolution;
import io.modporter.mappings.VersionMappings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java 源码转换：导入/类型/成员重命名、惯用法改写、@Mod 与生命周期迁移。
 * 全部改写规则来自映射数据文件，本类只实现通用机制。
 */
public final class JavaSourcePass {

    private static final String TODO_PREFIX = " TODO [modporter] ";

    private final PortContext ctx;
    private final MappingResolver resolver;
    private final JavaParser parser;

    public JavaSourcePass(PortContext ctx) {
        this.ctx = ctx;
        this.resolver = ctx.resolver;
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(JavaSyntaxLevels.forVersion(ctx.source().info.javaVersion));
        this.parser = new JavaParser(config);
    }

    // languageLevel 映射已抽取到 JavaSyntaxLevels（可被回归测试直接验证）。

    /** 转换一个 .java 文件，失败时原样返回并记录 ERROR。 */
    public String transform(String relPath, String content) {
        if (!JavaSyntaxLevels.supported(ctx.source().info.javaVersion)) {
            ctx.error(relPath, null, "parse", "不支持源 Java " + ctx.source().info.javaVersion
                    + " 的解析级别，文件原样复制（当前解析器已核实级别为 Java 8–26）");
            return content;
        }
        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String problems = result.getProblems().stream()
                    .map(Object::toString).collect(Collectors.joining("; "));
            ctx.error(relPath, null, "parse", "Java 源码解析失败，文件原样复制: " + problems);
            return content;
        }
        CompilationUnit cu = result.getResult().get();
        FileState state = new FileState(relPath, cu);

        new MixinReferencePass(ctx).transform(relPath, cu);
        new SafeMemberPass(ctx, relPath, cu).transform(cu);
        new AnnotationMigrationPass(ctx).transform(relPath, cu);
        rewriteImports(state);
        rewriteSimpleNames(state);
        markRemovedClassUsages(state);
        rewriteIdioms(state);
        rewriteModAnnotation(state);
        rewriteLifecycle(state);
        applyJavaPlatform(state);

        return cu.toString();
    }

    /** 单文件转换过程中的可变状态。 */
    private final class FileState {
        final String relPath;
        final CompilationUnit cu;
        /** 原始导入的 FQCN 集合（改写前快照） */
        final Set<String> originalImports = new HashSet<>();
        /** 需要在类型引用处执行的简单名重命名 */
        final Map<String, String> simpleRenames = new HashMap<>();
        /** 已移除类的简单名 -> 指导文字 */
        final Map<String, String> removedSimple = new HashMap<>();
        /** 本文件中用户自己声明的方法名（避免误改用户自身方法） */
        final Set<String> declaredMethods = new HashSet<>();
        /** 本文件中用户自己声明的字段名（避免误改用户自身字段，如自定义的 rand） */
        final Set<String> declaredFields = new HashSet<>();
        /** 防止同一位置重复插入相同 TODO */
        final Set<String> emittedTodos = new HashSet<>();
        final Set<NameExpr> valueNames = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        FileState(String relPath, CompilationUnit cu) {
            this.relPath = relPath;
            this.cu = cu;
            OwnerResolver sourceOwners = new OwnerResolver(cu);
            for (NameExpr name : cu.findAll(NameExpr.class)) {
                if (sourceOwners.isValueName(name)) valueNames.add(name);
            }
            for (ImportDeclaration imp : cu.getImports()) {
                originalImports.add(imp.getNameAsString());
            }
            for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                declaredMethods.add(m.getNameAsString());
            }
            for (FieldDeclaration f : cu.findAll(FieldDeclaration.class)) {
                for (VariableDeclarator v : f.getVariables()) {
                    declaredFields.add(v.getNameAsString());
                }
            }
        }
    }

    // ---- 导入 ----

    private void rewriteImports(FileState s) {
        for (ImportDeclaration imp : new ArrayList<>(s.cu.getImports())) {
            String name = imp.getNameAsString();
            String memberSuffix = null;
            String classFqcn = name;
            if (imp.isStatic() && !imp.isAsterisk()) {
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    classFqcn = name.substring(0, dot);
                    memberSuffix = name.substring(dot + 1);
                }
            }
            ClassResolution r = resolver.resolveClass(classFqcn);
            switch (r.kind) {
                case MAPPED -> {
                    String newName = memberSuffix != null ? r.targetFqcn + "." + memberSuffix : r.targetFqcn;
                    if (!newName.equals(name)) {
                        String oldSimple = lastSegment(classFqcn);
                        String newSimple = lastSegment(r.targetFqcn);
                        imp.setName(buildName(newName));
                        if (!oldSimple.equals(newSimple)) {
                            s.simpleRenames.put(oldSimple, newSimple);
                        }
                        ctx.info(s.relPath, line(imp), "class-mapping", classFqcn + " -> " + r.targetFqcn);
                    }
                    if (r.note != null) {
                        ctx.todo(s.relPath, line(imp), "class-mapping", classFqcn + ": " + r.note);
                    }
                }
                case REMOVED -> {
                    String guidance = r.guidance != null ? r.guidance
                            : "该 API 在目标版本中已不存在，需要人工迁移";
                    s.removedSimple.put(lastSegment(classFqcn), guidance);
                    ctx.todo(s.relPath, line(imp), "removed-api", classFqcn + ": " + guidance);
                }
                case UNKNOWN -> {
                    if (r.note != null) ctx.todo(s.relPath, line(imp), "ambiguous-class", r.note);
                    if (classFqcn.startsWith("net.minecraft.") || classFqcn.startsWith("net.minecraftforge.")
                            || classFqcn.startsWith("net.neoforged.") || classFqcn.startsWith("net.fabricmc.")) {
                        ctx.warn(s.relPath, line(imp), "unmapped-class",
                                "映射数据中没有该类的记录，导入保持原样: " + classFqcn);
                    }
                }
            }
        }
    }

    // ---- 简单名（类型引用、注解名、静态引用） ----

    private void rewriteSimpleNames(FileState s) {
        if (s.simpleRenames.isEmpty()) return;
        for (ClassOrInterfaceType type : s.cu.findAll(ClassOrInterfaceType.class)) {
            String newName = s.simpleRenames.get(type.getNameAsString());
            if (newName != null) {
                type.setName(new SimpleName(newName));
            }
        }
        for (AnnotationExpr anno : s.cu.findAll(AnnotationExpr.class)) {
            Name renamed = renameName(anno.getName(), s.simpleRenames);
            if (renamed != null) {
                anno.setName(renamed);
            }
        }
        for (NameExpr expr : s.cu.findAll(NameExpr.class)) {
            if (s.valueNames.contains(expr)) continue;
            String name = expr.getNameAsString();
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                String newName = s.simpleRenames.get(name);
                if (newName != null) {
                    expr.setName(new SimpleName(newName));
                }
            }
        }
    }

    /** 对可能带限定符的注解名逐段重命名，无变化时返回 null。 */
    private Name renameName(Name name, Map<String, String> renames) {
        List<String> segments = new ArrayList<>();
        Name n = name;
        while (n != null) {
            segments.add(0, n.getIdentifier());
            n = n.getQualifier().orElse(null);
        }
        boolean changed = false;
        for (int i = 0; i < segments.size(); i++) {
            String r = renames.get(segments.get(i));
            if (r != null) {
                segments.set(i, r);
                changed = true;
            }
        }
        if (!changed) return null;
        Name rebuilt = null;
        for (String seg : segments) {
            rebuilt = new Name(rebuilt, seg);
        }
        return rebuilt;
    }

    private static Name buildName(String dotted) {
        Name n = null;
        for (String seg : dotted.split("\\.")) {
            n = new Name(n, seg);
        }
        return n;
    }

    // ---- 已移除类的使用位置 ----

    private void markRemovedClassUsages(FileState s) {
        if (s.removedSimple.isEmpty()) return;
        for (NameExpr expr : s.cu.findAll(NameExpr.class)) {
            String guidance = s.removedSimple.get(expr.getNameAsString());
            if (guidance != null) {
                attachTodo(s, expr, expr.getNameAsString() + " 已移除: " + guidance, "removed-api");
            }
        }
        for (ClassOrInterfaceType type : s.cu.findAll(ClassOrInterfaceType.class)) {
            String guidance = s.removedSimple.get(type.getNameAsString());
            if (guidance != null) {
                attachTodo(s, type, type.getNameAsString() + " 已移除: " + guidance, "removed-api");
            }
        }
        boolean lifecycleHandled = "eventHandler".equals(ctx.source().info.lifecycleStyle)
                && "modBus".equals(ctx.target().info.lifecycleStyle);
        for (AnnotationExpr anno : s.cu.findAll(AnnotationExpr.class)) {
            String last = anno.getName().getIdentifier();
            if (lifecycleHandled && last.equals("EventHandler")) continue; // rewriteLifecycle 会处理
            String guidance = s.removedSimple.get(last);
            if (guidance != null) {
                attachTodo(s, anno, "@" + last + " 已移除: " + guidance, "removed-api");
            }
        }
    }

    // ---- 惯用法（如 new TextComponentString(x) <-> Component.literal(x)） ----

    private void rewriteIdioms(FileState s) {
        for (Map.Entry<String, VersionMappings.IdiomForm> e : resolver.sourceIdioms().entrySet()) {
            String idiomId = e.getKey();
            VersionMappings.IdiomForm sourceForm = e.getValue();
            VersionMappings.IdiomForm targetForm = resolver.targetIdiom(idiomId);
            if (targetForm == null) continue;
            if (sourceForm.type.equals(targetForm.type)
                    && sourceForm.className.equals(targetForm.className)
                    && (sourceForm.method == null || sourceForm.method.equals(targetForm.method))) {
                continue; // 两版本形态一致
            }
            String sourceSimple = lastSegment(sourceForm.className);
            boolean imported = s.originalImports.contains(sourceForm.className);
            int replaced = 0;

            if ("constructor".equals(sourceForm.type)) {
                for (ObjectCreationExpr creation : s.cu.findAll(ObjectCreationExpr.class)) {
                    String typeName = creation.getType().getNameAsString();
                    boolean matches = (typeName.equals(sourceForm.className)
                            || (imported && typeName.equals(sourceSimple)))
                            && arityMatches(sourceForm, creation.getArguments().size());
                    if (matches && emitIdiom(s, creation, creation.getArguments(), targetForm, idiomId)) {
                        replaced++;
                    }
                }
            } else if ("staticCall".equals(sourceForm.type)) {
                for (MethodCallExpr call : s.cu.findAll(MethodCallExpr.class)) {
                    if (!call.getNameAsString().equals(sourceForm.method)) continue;
                    Expression scope = call.getScope().orElse(null);
                    boolean matches = scope instanceof NameExpr ne && imported
                            && ne.getNameAsString().equals(sourceSimple)
                            && arityMatches(sourceForm, call.getArguments().size());
                    if (matches && emitIdiom(s, call, call.getArguments(), targetForm, idiomId)) {
                        replaced++;
                    }
                }
            }
            if (replaced > 0) {
                // 源类导入换成目标类导入；若源类型仍被声明引用（如变量类型），提示人工处理
                boolean stillReferenced = s.cu.findAll(ClassOrInterfaceType.class).stream()
                        .anyMatch(t -> t.getNameAsString().equals(sourceSimple));
                if (stillReferenced) {
                    ctx.todo(s.relPath, null, "idiom",
                            sourceSimple + " 仍被用作类型声明，目标版本无同名类型，请人工替换（惯用法 " + idiomId + "）");
                } else {
                    s.cu.getImports().removeIf(imp -> imp.getNameAsString().equals(sourceForm.className));
                }
            }
        }
    }

    /** 惯用法形态的参数个数约束（null = 任意），用于区分同一构造器的不同参数形态。 */
    private static boolean arityMatches(VersionMappings.IdiomForm form, int argumentCount) {
        return form.arity == null || form.arity == argumentCount;
    }

    private boolean emitIdiom(FileState s, Expression original, List<Expression> arguments,
                              VersionMappings.IdiomForm targetForm, String idiomId) {
        List<Expression> args = new ArrayList<>();
        for (Expression a : arguments) {
            args.add(a.clone());
        }
        Expression replacement;
        String targetSimple = lastSegment(targetForm.className);
        if ("staticCall".equals(targetForm.type)) {
            MethodCallExpr call = new MethodCallExpr(new NameExpr(targetSimple), targetForm.method);
            call.getArguments().addAll(args);
            replacement = call;
        } else if ("constructor".equals(targetForm.type)) {
            ObjectCreationExpr creation = new ObjectCreationExpr();
            creation.setType(new ClassOrInterfaceType(null, targetSimple));
            creation.getArguments().addAll(args);
            replacement = creation;
        } else {
            return false;
        }
        int lineNo = original.getBegin().map(p -> p.line).orElse(0);
        if (original.replace(replacement)) {
            s.cu.addImport(targetForm.className);
            ctx.info(s.relPath, lineNo, "idiom", "惯用法改写 [" + idiomId + "]");
            return true;
        }
        return false;
    }

    // ---- @Mod 注解风格 ----

    private void rewriteModAnnotation(FileState s) {
        String sourceStyle = ctx.source().info.modAnnotationStyle;
        String targetStyle = ctx.target().info.modAnnotationStyle;
        if (sourceStyle == null || sourceStyle.equals(targetStyle)) return;

        for (AnnotationExpr anno : s.cu.findAll(AnnotationExpr.class)) {
            if (!anno.getName().getIdentifier().equals("Mod")) continue;

            if ("attributes".equals(sourceStyle) && "value".equals(targetStyle)
                    && anno instanceof NormalAnnotationExpr normal) {
                Expression modidExpr = null;
                List<String> dropped = new ArrayList<>();
                for (MemberValuePair pair : normal.getPairs()) {
                    if (pair.getNameAsString().equals("modid")) {
                        modidExpr = pair.getValue().clone();
                    } else {
                        dropped.add(pair.getNameAsString());
                    }
                }
                if (modidExpr == null) {
                    attachTodo(s, anno, "@Mod 缺少 modid 属性，无法自动转换为单值形式", "mod-annotation");
                    continue;
                }
                int lineNo = line(anno);
                anno.replace(new SingleMemberAnnotationExpr(anno.getName().clone(), modidExpr));
                ctx.info(s.relPath, lineNo, "mod-annotation", "@Mod(modid=...) -> @Mod(...)");
                if (!dropped.isEmpty()) {
                    ctx.info(s.relPath, lineNo, "mod-annotation",
                            "@Mod 属性 " + dropped + " 已省略，相应信息由元数据文件承载（已同步生成）");
                }
            } else if ("value".equals(sourceStyle) && "attributes".equals(targetStyle)
                    && anno instanceof SingleMemberAnnotationExpr single) {
                NormalAnnotationExpr normal = new NormalAnnotationExpr();
                normal.setName(anno.getName().clone());
                normal.addPair("modid", single.getMemberValue().clone());
                int lineNo = line(anno);
                anno.replace(normal);
                ctx.info(s.relPath, lineNo, "mod-annotation", "@Mod(...) -> @Mod(modid=...)");
            }
        }
    }

    // ---- 生命周期（@Mod.EventHandler -> Mod 总线监听） ----

    private void rewriteLifecycle(FileState s) {
        String sourceStyle = ctx.source().info.lifecycleStyle;
        String targetStyle = ctx.target().info.lifecycleStyle;
        if (sourceStyle == null || sourceStyle.equals(targetStyle)) return;

        if ("eventHandler".equals(sourceStyle) && "modBus".equals(targetStyle)) {
            String subscribeFqcn = resolver.targetClass("forge.SubscribeEvent");
            String guidance = ctx.target().guidanceFor("lifecycle.eventHandler");
            for (MethodDeclaration method : s.cu.findAll(MethodDeclaration.class)) {
                for (AnnotationExpr anno : new ArrayList<>(method.getAnnotations())) {
                    String id = anno.getName().getIdentifier();
                    String qualified = anno.getNameAsString();
                    if (id.equals("EventHandler")
                            && (qualified.equals("EventHandler") || qualified.endsWith("Mod.EventHandler"))) {
                        anno.remove();
                        if (subscribeFqcn != null) {
                            method.addAnnotation(new MarkerAnnotationExpr(
                                    buildName(lastSegment(subscribeFqcn))));
                            s.cu.addImport(subscribeFqcn);
                        }
                        String msg = "生命周期方法 " + method.getNameAsString() + " 已由 @Mod.EventHandler 改为 @SubscribeEvent。"
                                + (guidance != null ? guidance : "需要手动注册到 Mod 事件总线。");
                        attachTodo(s, method, msg, "lifecycle");
                    }
                }
            }
        } else {
            ctx.warn(s.relPath, null, "lifecycle",
                    "生命周期风格 " + sourceStyle + " -> " + targetStyle + " 的自动迁移暂未实现，请人工检查生命周期方法");
        }
    }

    // ---- Java 平台（语法/JDK 类库随 Java 版本的变化，数据来自 mappings/java/） ----

    /** 新语法诊断不依赖平台 JSON 是否列出非法标识符（Java 8 不列出 _）。 */
    private void checkModernSyntax(FileState s, int sourceVersion, int targetVersion) {
        if (sourceVersion >= 22 && targetVersion < 22) {
            for (SimpleName name : s.cu.findAll(SimpleName.class)) {
                if (name.getIdentifier().equals("_")) {
                    attachTodo(s, name, "Java 22+ 未命名变量/模式 _ 在目标 Java " + targetVersion
                            + " 不可用；保留原文，请为每个绑定选择独立名称或手工调整模式", "java-syntax");
                }
            }
            for (com.github.javaparser.ast.expr.MatchAllPatternExpr pattern
                    : s.cu.findAll(com.github.javaparser.ast.expr.MatchAllPatternExpr.class)) {
                attachTodo(s, pattern, "Java 22+ 未命名模式 _ 需要人工降级", "java-syntax");
            }
        }
        if (targetVersion < 21 && sourceVersion >= 21) {
            for (com.github.javaparser.ast.expr.RecordPatternExpr pattern
                    : s.cu.findAll(com.github.javaparser.ast.expr.RecordPatternExpr.class)) {
                attachTodo(s, pattern, "Java 21 record pattern 在目标 Java " + targetVersion
                        + " 不可用，需改写为类型检查与访问器调用", "java-syntax");
            }
            for (com.github.javaparser.ast.stmt.SwitchEntry entry
                    : s.cu.findAll(com.github.javaparser.ast.stmt.SwitchEntry.class)) {
                if (entry.getGuard().isPresent() || entry.getLabels().stream().anyMatch(label ->
                        label instanceof com.github.javaparser.ast.expr.PatternExpr
                                || label instanceof com.github.javaparser.ast.expr.NullLiteralExpr)) {
                    attachTodo(s, entry, "Java 21 switch 模式/when/null 分支需要人工降级", "java-syntax");
                }
            }
        }
        if (sourceVersion >= 25 && targetVersion < 25) {
            for (ImportDeclaration imp : s.cu.getImports()) {
                if (imp.isModule()) attachTodo(s, imp, "Java 25 模块导入需要改为普通类型导入", "java-syntax");
            }
            for (com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt invocation
                    : s.cu.findAll(com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt.class)) {
                Node parent = invocation.getParentNode().orElse(null);
                if (parent instanceof com.github.javaparser.ast.stmt.BlockStmt block
                        && block.getStatements().indexOf(invocation) > 0) {
                    attachTodo(s, invocation, "Java 25 构造器前置语句需要人工降级；不能盲目重排副作用", "java-syntax");
                }
            }
        }
    }

    private void applyJavaPlatform(FileState s) {
        int sourceVersion = ctx.source().info.javaVersion;
        int targetVersion = ctx.target().info.javaVersion;
        checkModernSyntax(s, sourceVersion, targetVersion);
        io.modporter.mappings.JavaPlatform target = ctx.targetJava;
        if (target == null) return;

        // 1) 目标版本非法标识符。这里必须区分两种“_”：
        //    - Java 8：_ 是合法普通标识符；目标 Java 9+ 将其保留为关键字，
        //      普通用法不再合法，且改名为 _renamed 不改变语义，可安全自动改名。
        //    - Java 22+（JEP 456）：_ 是“未命名变量/模式”，只在特定位置合法。
        //      绝不能按普通非法标识符盲目改名（会破坏未命名语义/触发使用错误）。
        for (String bad : target.illegalIdentifiers) {
            boolean underscore = "_".equals(bad);
            if (underscore && sourceVersion >= 22) {
                // checkModernSyntax 独立诊断降级，包含目标 Java 8 和模式中的 _。
                continue;
            }
            if (underscore && sourceVersion >= 9) {
                // 源 Java 9-21 中 _ 本身就是非法标识符；正常情况下解析阶段就会失败
                // 并原样复制。若宽松解析后仍出现在 AST 中，只诊断，不盲目改名。
                for (com.github.javaparser.ast.body.VariableDeclarator v
                        : s.cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                    if (v.getNameAsString().equals("_")) {
                        attachTodo(s, v, "标识符 \"_\" 在 Java 9+ 是保留关键字，此处用法需人工确认", "java-syntax");
                    }
                }
                continue;
            }
            Set<String> usedNames = s.cu.findAll(SimpleName.class).stream()
                    .map(SimpleName::getIdentifier).collect(Collectors.toSet());
            String replacement = bad + "renamed";
            while (usedNames.contains(replacement)) replacement += "_";
            boolean hit = false;
            for (com.github.javaparser.ast.body.VariableDeclarator v
                    : s.cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                if (v.getNameAsString().equals(bad)) { v.setName(new SimpleName(replacement)); hit = true; }
            }
            for (com.github.javaparser.ast.body.Parameter p
                    : s.cu.findAll(com.github.javaparser.ast.body.Parameter.class)) {
                if (p.getNameAsString().equals(bad)) { p.setName(new SimpleName(replacement)); hit = true; }
            }
            if (hit) {
                for (NameExpr n : s.cu.findAll(NameExpr.class)) {
                    if (n.getNameAsString().equals(bad)) n.setName(new SimpleName(replacement));
                }
                ctx.info(s.relPath, null, "java-syntax",
                        "标识符 \"" + bad + "\" 在 Java " + targetVersion + " 非法，已自动改名为 \"" + replacement + '"');
            }
        }

        // 2) 与目标版本受限关键字冲突的类型名：改动影响跨文件引用，只打 TODO
        for (com.github.javaparser.ast.body.TypeDeclaration<?> type
                : s.cu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class)) {
            if (target.restrictedTypeNames.contains(type.getNameAsString())) {
                attachTodo(s, type, "类型名 \"" + type.getNameAsString() + "\" 在 Java " + targetVersion
                        + " 是受限关键字，不能作类型名，需要连同所有引用一起改名", "java-syntax");
            }
        }
        if (target.restrictedTypeNames.contains("yield")) {
            for (MethodDeclaration m : s.cu.findAll(MethodDeclaration.class)) {
                if (m.getNameAsString().equals("yield")) {
                    attachTodo(s, m, "方法名 yield 在 Java 14+ 受限：无限定调用会被解析为 yield 语句，建议改名", "java-syntax");
                }
            }
        }

        // 3) 目标 JDK 已移除/封锁的类库（EE、sun.misc 等）
        for (ImportDeclaration imp : s.cu.getImports()) {
            String guidance = target.lookupImportIssue(imp.getNameAsString());
            if (guidance != null) {
                ctx.todo(s.relPath, line(imp), "java-library",
                        imp.getNameAsString() + ": " + guidance);
            }
        }

        // 3b) 方法级：已移除/行为破坏的方法、失效的字符串实参、反射查表
        scanPlatformMethods(s, target);

        // 4) 降级方向：源代码使用的语法特性在目标 Java 版本不存在
        if (targetVersion < sourceVersion) {
            flagFeature(s, com.github.javaparser.ast.type.VarType.class, "var", targetVersion,
                    "var 局部变量类型推断需改为显式类型");
            flagFeature(s, com.github.javaparser.ast.body.RecordDeclaration.class, "records", targetVersion,
                    "record 需改写为普通 final 类（字段 + 构造器 + 访问器 + equals/hashCode/toString）");
            flagFeature(s, com.github.javaparser.ast.expr.SwitchExpr.class, "switch-expressions", targetVersion,
                    "switch 表达式需改写为传统 switch 语句");
            flagFeature(s, com.github.javaparser.ast.stmt.YieldStmt.class, "switch-expressions", targetVersion,
                    "yield 语句随 switch 表达式一并改写");
            flagFeature(s, com.github.javaparser.ast.expr.PatternExpr.class, "instanceof-patterns", targetVersion,
                    "instanceof 模式匹配需改为 instanceof + 显式强转");
            Integer textBlocksSince = ctx.javaFeatures.get("text-blocks");
            if (textBlocksSince != null && textBlocksSince > targetVersion) {
                for (com.github.javaparser.ast.expr.TextBlockLiteralExpr tb
                        : s.cu.findAll(com.github.javaparser.ast.expr.TextBlockLiteralExpr.class)) {
                    int lineNo = tb.getBegin().map(p -> p.line).orElse(0);
                    tb.replace(new com.github.javaparser.ast.expr.StringLiteralExpr(
                            escapeForStringLiteral(tb.stripIndent())));
                    ctx.info(s.relPath, lineNo, "java-syntax", "文本块已自动降级为普通字符串字面量");
                }
            }
            Integer sealedSince = ctx.javaFeatures.get("sealed-classes");
            if (sealedSince != null && sealedSince > targetVersion) {
                for (com.github.javaparser.ast.body.TypeDeclaration<?> type
                        : s.cu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class)) {
                    boolean sealed = type.getModifiers().stream().anyMatch(m ->
                            m.getKeyword() == com.github.javaparser.ast.Modifier.Keyword.SEALED
                                    || m.getKeyword() == com.github.javaparser.ast.Modifier.Keyword.NON_SEALED);
                    if (sealed) {
                        attachTodo(s, type, "sealed/non-sealed 在 Java " + targetVersion
                                + " 不存在，需移除并用其他方式约束继承", "java-syntax");
                    }
                }
            }
        }
    }

    /**
     * 方法级平台检查。接收者类型用「本文件内声明类型 + 静态调用 scope」启发式判定：
     * 判定得出类型时按类型精确匹配；无法判定时只报告 anyReceiver 条目，避免误伤同名自定义方法。
     */
    private void scanPlatformMethods(FileState s, io.modporter.mappings.JavaPlatform target) {
        if (target.removedMethods.isEmpty() && target.argumentIssues.isEmpty()
                && target.reflectiveLookups.isEmpty()) {
            return;
        }
        Map<String, String> declaredTypes = collectDeclaredTypes(s);

        for (MethodCallExpr call : s.cu.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            Expression scope = call.getScope().orElse(null);
            String receiverType = resolveReceiverType(scope, declaredTypes);

            for (io.modporter.mappings.JavaPlatform.MethodIssue issue
                    : target.removedMethods.getOrDefault(name, List.of())) {
                if (receiverMatches(issue.owner, receiverType, issue.anyReceiver, scope)) {
                    attachTodo(s, call, (issue.owner != null ? issue.owner + "." : "") + name
                            + "(...): " + issue.message, "java-library");
                }
            }

            for (io.modporter.mappings.JavaPlatform.ArgumentIssue issue
                    : target.argumentIssues.getOrDefault(name, List.of())) {
                if (!receiverMatches(issue.owner, receiverType, true, scope)) continue;
                for (Expression arg : call.getArguments()) {
                    if (arg instanceof com.github.javaparser.ast.expr.StringLiteralExpr lit
                            && issue.values.stream().anyMatch(v -> v.equalsIgnoreCase(lit.getValue()))) {
                        attachTodo(s, call, name + "(\"" + lit.getValue() + "\"): " + issue.message,
                                "java-library");
                    }
                }
            }

            for (io.modporter.mappings.JavaPlatform.ReflectiveLookup lookup : target.reflectiveLookups) {
                if (!lookup.method.equals(name)) continue;
                if (!receiverMatches(lookup.owner, receiverType, true, scope)) continue;
                for (Expression arg : call.getArguments()) {
                    if (arg instanceof com.github.javaparser.ast.expr.StringLiteralExpr lit) {
                        String guidance = target.lookupImportIssue(lit.getValue());
                        if (guidance != null) {
                            attachTodo(s, call, "反射加载的类 \"" + lit.getValue() + "\" 在目标 Java 版本不可用: "
                                    + guidance, "java-library");
                        }
                    }
                }
            }
        }
    }

    /** 本文件内「变量/字段/参数名 -> 声明类型简单名」，作为接收者类型的轻量推断。 */
    private static Map<String, String> collectDeclaredTypes(FileState s) {
        Map<String, String> types = new HashMap<>();
        for (com.github.javaparser.ast.body.VariableDeclarator v
                : s.cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
            if (v.getType() instanceof ClassOrInterfaceType t) {
                types.put(v.getNameAsString(), t.getNameAsString());
            }
        }
        for (com.github.javaparser.ast.body.Parameter p
                : s.cu.findAll(com.github.javaparser.ast.body.Parameter.class)) {
            if (p.getType() instanceof ClassOrInterfaceType t) {
                types.put(p.getNameAsString(), t.getNameAsString());
            }
        }
        return types;
    }

    /** 判定接收者类型的简单名；无法判定返回 null。 */
    private static String resolveReceiverType(Expression scope, Map<String, String> declaredTypes) {
        if (scope == null) return null;
        if (scope instanceof NameExpr ne) {
            String n = ne.getNameAsString();
            // 大写开头视为静态调用的类名，否则查本文件声明类型
            if (!n.isEmpty() && Character.isUpperCase(n.charAt(0))) return n;
            return declaredTypes.get(n);
        }
        if (scope instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr) {
            return declaredTypes.get(fa.getNameAsString());
        }
        if (scope instanceof ObjectCreationExpr oc) {
            return oc.getType().getNameAsString();
        }
        return null;
    }

    /**
     * owner 为 null（任意接收者）或与判定出的接收者类型一致时匹配；
     * 接收者无法判定时，仅在 anyReceiver 或静态工厂链（如 Thread.currentThread().stop()）下匹配。
     */
    private static boolean receiverMatches(String owner, String receiverType,
                                           boolean anyReceiver, Expression scope) {
        if (owner == null) return true;
        if (receiverType != null) return owner.equals(receiverType);
        // Type.factory().method() —— 静态工厂返回自身类型的常见形态
        if (scope instanceof MethodCallExpr inner
                && inner.getScope().orElse(null) instanceof NameExpr ne
                && ne.getNameAsString().equals(owner)) {
            return true;
        }
        return anyReceiver;
    }

    private <T extends Node> void flagFeature(FileState s, Class<T> nodeType, String featureId,
                                              int targetVersion, String advice) {
        Integer since = ctx.javaFeatures.get(featureId);
        if (since == null || since <= targetVersion) return;
        for (T node : s.cu.findAll(nodeType)) {
            attachTodo(s, node, "Java " + since + "+ 语法（" + featureId + "）在目标 Java "
                    + targetVersion + " 不可用：" + advice, "java-syntax");
        }
    }

    private static String escapeForStringLiteral(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\t", "\\t")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static String lastSegment(String dotted) {
        int dot = dotted.lastIndexOf('.');
        return dot >= 0 ? dotted.substring(dot + 1) : dotted;
    }

    private static Integer line(Node node) {
        return node.getBegin().map(p -> p.line).orElse(null);
    }

    /** 给最近的语句/成员声明挂 TODO 注释（已有注释则只进报告），并写入报告。 */
    private void attachTodo(FileState s, Node node, String message, String category) {
        ctx.todo(s.relPath, line(node), category, message);
        Node anchor = node;
        while (anchor != null && !(anchor instanceof Statement) && !(anchor instanceof BodyDeclaration)) {
            anchor = anchor.getParentNode().orElse(null);
        }
        if (anchor == null) return;
        String key = System.identityHashCode(anchor) + "|" + message;
        if (!s.emittedTodos.add(key)) return;
        if (anchor.getComment().isEmpty()) {
            anchor.setComment(new LineComment(TODO_PREFIX + message));
        }
    }
}
