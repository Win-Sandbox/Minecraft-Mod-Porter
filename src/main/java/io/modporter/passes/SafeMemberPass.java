package io.modporter.passes;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import io.modporter.engine.PortContext;
import io.modporter.mappings.MappingResolver;
import io.modporter.mappings.MappingResolver.MemberCandidate;
import io.modporter.mappings.VersionMappings;
import java.util.*;

/** Runs on SOURCE names, before imports/type/idiom rewrites. Never guesses an unknown receiver. */
final class SafeMemberPass {
    private final PortContext ctx;
    private final MappingResolver resolver;
    private final String file;
    private final OwnerResolver owners;
    private final Map<MethodDeclaration, MemberCandidate> overrides = new IdentityHashMap<>();
    SafeMemberPass(PortContext ctx, String file, CompilationUnit cu) {
        this.ctx = ctx; this.resolver = ctx.resolver; this.file = file; this.owners = new OwnerResolver(cu);
    }
    void transform(CompilationUnit cu) {
        // Snapshot before replacing any node: synthesized fields/getters must not be migrated twice.
        List<MethodCallExpr> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
        List<FieldAccessExpr> fields = new ArrayList<>(cu.findAll(FieldAccessExpr.class));
        for (MethodReferenceExpr reference : cu.findAll(MethodReferenceExpr.class)) {
            if (hasChange(reference.getIdentifier(), "method")) todo(reference, "方法引用 :: 的完整签名尚未求解，保留原文并人工核对");
        }
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isStatic() && !imp.isAsterisk()) {
                String fq = imp.getNameAsString();
                String name = fq.substring(fq.lastIndexOf('.') + 1);
                if (hasChange(name, "method") || hasChange(name, "field")) todo(imp, "静态导入成员需要人工核对（本轮不猜重载/形态）");
            }
        }
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getAnnotationByName("Override").isEmpty()) continue;
            TypeDeclaration<?> type = owners.enclosingType(method);
            if (type == null) continue;
            if (type.getMethodsByName(method.getNameAsString()).size() != 1) {
                if (hasChange(method.getNameAsString(), "method")) todo(method, "同名重载声明无法核对签名，保留 @Override 方法");
                continue;
            }
            MemberCandidate candidate = select(owners.directSupertypes(method), method.getNameAsString(), "method", method);
            if (candidate != null && candidate.targetKind.equals("method")) {
                if (candidate.receiverChanged()) {
                    todo(method, "@Override 映射涉及 receiver 变化，无法保持覆写语义，保留原声明");
                    continue;
                }
                overrides.put(method, candidate);
            } else if (candidate != null) todo(method, "@Override 方法不能自动变成字段");
        }
        for (MethodCallExpr call : calls) {
            if (call.findCompilationUnit().orElse(null) != cu) continue;
            String name = call.getNameAsString();
            Expression scope = call.getScope().orElse(null);
            boolean receiverUsed = scope != null && !(scope instanceof ThisExpr);
            MemberCandidate candidate;
            if ((scope == null || scope instanceof ThisExpr) && owners.declaresMethod(call, name)) {
                TypeDeclaration<?> type = owners.enclosingType(call);
                candidate = null;
                if (scope instanceof ThisExpr t && t.getTypeName().isPresent()) continue;
                for (Map.Entry<MethodDeclaration, MemberCandidate> e : overrides.entrySet()) {
                    if (owners.enclosingType(e.getKey()) == type && e.getKey().getNameAsString().equals(name)) candidate = e.getValue();
                }
                if (candidate == null) continue;
                if (candidate.sourceReceiver != null) {
                    todo(call, "方法 " + name + " 的源 receiver 为 " + receiverDesc(candidate.sourceReceiver)
                            + "，此处是本类调用，不能证明等价，保留原调用");
                    continue;
                }
            } else {
                candidate = select(owners.receiver(scope, call), name, "method", call);
            }
            if (candidate == null) continue;
            if (candidate.sourceReceiver != null && !receiverApplicableAtCall(candidate.sourceReceiver, scope, receiverUsed, call)) {
                todo(call, "方法 " + name + " 的源 receiver 形态为 " + receiverDesc(candidate.sourceReceiver)
                        + "，当前调用形态不匹配，不能应用映射，保留原调用");
                continue;
            }
            if (candidate.receiverChanged()) {
                if (candidate.targetKind.equals("field")) {
                    todo(call, "方法转字段同时发生 receiver 变化，未验证原子改写，保留原调用");
                    continue;
                }
                if (candidate.sourceReceiver == null) {
                    todo(call, "方法 " + name + " 源版本未声明 receiver，目标 receiver 为 "
                            + receiverDesc(candidate.targetReceiver) + "，不能猜测调用位置，保留原调用");
                    continue;
                }
                if (candidate.targetReceiver == null) {
                    todo(call, "方法 " + name + " 源 receiver " + receiverDesc(candidate.sourceReceiver)
                            + " 目标未给出 receiver，不能自动移动调用位置，保留原调用");
                    continue;
                }
                // 同位置表达式的成员改名已执行；接收者位置的重写仅支持精确 static owner 迁移。
                if (!"static".equals(candidate.targetReceiver.kind())
                        || !candidate.targetReceiver.owner().equals(resolver.targetClass(candidate.classIr))) {
                    todo(call, "方法 " + name + " receiver 变化 " + receiverDesc(candidate.sourceReceiver)
                            + " -> " + receiverDesc(candidate.targetReceiver)
                            + " 超出已验证可自动改写范围，保留原调用并人工核对");
                    continue;
                }
                call.setScope(new NameExpr(candidate.targetReceiver.owner()));
                info(call, name + " receiver -> " + candidate.targetReceiver.owner());
            }
            if (candidate.targetKind.equals("method")) {
                call.setName(candidate.targetName);
                info(call, name + "() -> " + candidate.targetName + "() [" + candidate.classIr + "]");
            } else if (scope != null && call.getArguments().isEmpty() && call.getTypeArguments().isEmpty()
                    && !(call.getParentNode().orElse(null) instanceof com.github.javaparser.ast.stmt.ExpressionStmt)
                    && !(call.getParentNode().orElse(null) instanceof com.github.javaparser.ast.stmt.ForStmt)) {
                info(call, name + "() -> ." + candidate.targetName);
                call.replace(new FieldAccessExpr(scope.clone(), candidate.targetName));
            } else todo(call, "方法转字段的调用形式不安全，保留原调用");
            if (candidate.note != null) todo(call, candidate.note);
        }
        for (FieldAccessExpr field : fields) {
            if (field.findCompilationUnit().orElse(null) != cu) continue;
            if (field.getScope() instanceof ThisExpr && owners.declaresField(field, field.getNameAsString())) continue;
            MemberCandidate candidate = select(owners.receiver(field.getScope(), field), field.getNameAsString(), "field", field);
            if (candidate == null) continue;
            if (candidate.sourceReceiver != null
                    && !receiverApplicableAtField(candidate.sourceReceiver, field.getScope(), true, field)) {
                todo(field, "字段 " + field.getNameAsString() + " 的源 receiver 形态为 "
                        + receiverDesc(candidate.sourceReceiver) + "，当前访问形态不匹配，保留原文");
                continue;
            }
            if (candidate.receiverChanged()) {
                todo(field, "字段 " + field.getNameAsString() + " receiver 变化 "
                        + receiverDesc(candidate.sourceReceiver) + " -> " + receiverDesc(candidate.targetReceiver)
                        + "，字段位置自动改写未验证，保留原文");
                continue;
            }
            String name = field.getNameAsString();
            if (candidate.targetKind.equals("field")) {
                field.setName(candidate.targetName);
                info(field, "." + name + " -> ." + candidate.targetName);
            } else if (isWrite(field)) todo(field, "字段在目标版本变为 getter，但当前是赋值/自增/自减，需手工迁移");
            else {
                info(field, "." + name + " -> " + candidate.targetName + "()");
                field.replace(new MethodCallExpr(field.getScope().clone(), candidate.targetName));
            }
            if (candidate.note != null) todo(field, candidate.note);
        }
        // Declarations are changed last; all call decisions used original lexical names and class identity.
        for (Map.Entry<MethodDeclaration, MemberCandidate> e : overrides.entrySet()) {
            MethodDeclaration method = e.getKey(); MemberCandidate candidate = e.getValue();
            info(method, "@Override " + method.getNameAsString() + " -> " + candidate.targetName);
            method.setName(candidate.targetName);
            if (candidate.note != null) todo(method, candidate.note);
        }
    }
    private boolean hasChange(String name, String kind) {
        return resolver.resolveMember(name).stream().anyMatch(c -> c.sourceKind.equals(kind) && !c.isNoop());
    }
    private static String receiverDesc(VersionMappings.Receiver r) {
        return r == null ? "(无)" : r.kind() + " " + (r.owner() != null ? r.owner() : String.join(".", r.path()));
    }
    private boolean receiverApplicableAtCall(VersionMappings.Receiver receiver, Expression scope,
            boolean receiverUsed, Node at) {
        if (receiver == null) return true;
        if (!receiverUsed || scope == null) return false;
        if ("static".equals(receiver.kind())) {
            // A static receiver is a type expression, never an IR id or a value name.
            if (!(scope instanceof NameExpr name) || owners.isValueName(name)) return false;
            OwnerResolver.Owners resolved = owners.receiver(scope, at);
            return resolved.known() && resolved.names().size() == 1
                    && receiver.owner().equals(resolved.names().get(0));
        }
        return exactChainPath(receiver.path(), scope);
    }
    private boolean receiverApplicableAtField(VersionMappings.Receiver receiver, Expression scope,
            boolean receiverUsed, Node at) {
        if (receiver == null) return true;
        if (!receiverUsed || scope == null) return false;
        if ("static".equals(receiver.kind())) {
            if (!(scope instanceof NameExpr name) || owners.isValueName(name)) return false;
            OwnerResolver.Owners resolved = owners.receiver(scope, at);
            return resolved.known() && resolved.names().size() == 1
                    && receiver.owner().equals(resolved.names().get(0));
        }
        return exactChainPath(receiver.path(), scope);
    }
    /** Match only the documented chain grammar; arbitrary non-NameExpr nodes are unknown. */
    private static boolean exactChainPath(List<String> path, Expression scope) {
        if (path == null || path.isEmpty()) return false;
        List<String> actual = new ArrayList<>();
        Expression current = scope;
        while (true) {
            if (current instanceof NameExpr name) {
                actual.add(name.getNameAsString());
                break;
            }
            if (current instanceof MethodCallExpr call
                    && call.getArguments().isEmpty() && call.getTypeArguments().isEmpty()) {
                actual.add(call.getNameAsString() + "()");
                if (call.getScope().isEmpty()) break;
                current = call.getScope().get();
                continue;
            }
            if (current instanceof FieldAccessExpr field && field.getScope() != null) {
                actual.add(field.getNameAsString());
                current = field.getScope();
                continue;
            }
            return false;
        }
        Collections.reverse(actual);
        return actual.equals(path);
    }
    private MemberCandidate select(OwnerResolver.Owners owner, String name, String kind, Node at) {
        if (!hasChange(name, kind)) {
            String guidance = resolver.removedMemberGuidance(name);
            if (guidance != null && owner.names().stream().anyMatch(fq -> fq.startsWith("net.minecraft.")
                    || fq.startsWith("net.minecraftforge.") || fq.startsWith("net.neoforged.") || fq.startsWith("net.fabricmc."))) {
                todo(at, "成员名命中旧API提示，但数据未提供owner，不能据此判定移除；请核对：" + guidance);
            }
            return null;
        }
        if (!owner.known()) {
            todo(at, "成员 " + name + " 的源接收者类型不能确定，保留原文"); return null;
        }
        List<MemberCandidate> all = new ArrayList<>();
        for (String fqcn : owner.names()) {
            List<MemberCandidate> candidates = resolver.resolveMemberForOwner(fqcn, name).stream()
                    .filter(c -> c.sourceKind.equals(kind)).toList();
            // Unknown direct interface/base alongside a mapped one is not proof of ownership.
            if (candidates.isEmpty() && owner.names().size() > 1) {
                todo(at, "成员 " + name + " 的多个直接父类/接口不能完全消歧，保留原文"); return null;
            }
            all.addAll(candidates);
        }
        if (all.isEmpty()) {
            if (owner.names().stream().anyMatch(fq -> resolver.source().irByFqcn.containsKey(fq))) {
                todo(at, "成员 " + name + " 可能继承自未解析的父类，owner 上无精确条目，保留原文");
            }
            return null;
        }
        if (!MappingResolver.unambiguous(all)) {
            todo(at, "成员 " + name + " 在已确认 owner 上仍有映射歧义（包括不改名候选），保留原文"); return null;
        }
        MemberCandidate c = all.get(0);
        for (MemberCandidate candidate : all) {
            var sourceMembers = resolver.source().members.get(candidate.classIr);
            var sourceEntry = sourceMembers != null ? sourceMembers.get(candidate.memberIr) : null;
            if (candidate.note != null || (sourceEntry != null && sourceEntry.note != null)) {
                todo(at, "成员 " + name + " 带有语义/签名迁移提示，保留原调用："
                        + (candidate.note != null ? candidate.note : sourceEntry.note));
                return null;
            }
        }
        // A target without the owner class cannot safely host the rewritten member.
        if (resolver.targetClass(c.classIr) == null) {
            todo(at, "成员 " + name + " 的目标 owner 不存在，保留原文"); return null;
        }
        return c.isNoop() ? null : c;
    }
    private static boolean isWrite(FieldAccessExpr field) {
        Node n = field;
        while (n.getParentNode().orElse(null) instanceof EnclosedExpr) n = n.getParentNode().get();
        Node parent = n.getParentNode().orElse(null);
        if (parent instanceof AssignExpr a && a.getTarget() == n) return true;
        if (parent instanceof UnaryExpr u) {
            return u.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || u.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT
                    || u.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT
                    || u.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT;
        }
        return false;
    }
    private void info(Node node, String message) {
        ctx.info(file, node.getBegin().map(p -> p.line).orElse(null), "member-mapping", message);
    }
    private void todo(Node node, String message) {
        ctx.todo(file, node.getBegin().map(p -> p.line).orElse(null), "member-owner", message);
        // Anchor comments on statements/declarations, never in the middle of a receiver expression.
        Node anchor = node;
        while (anchor != null && !(anchor instanceof com.github.javaparser.ast.stmt.Statement)
                && !(anchor instanceof BodyDeclaration) && !(anchor instanceof ImportDeclaration)) {
            anchor = anchor.getParentNode().orElse(null);
        }
        if (anchor != null && anchor.getComment().isEmpty()) {
            anchor.setComment(new LineComment(" TODO [modporter] " + message.replace('\n', ' ')));
        }
    }
}
