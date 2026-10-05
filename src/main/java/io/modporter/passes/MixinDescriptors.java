package io.modporter.passes;

import java.util.*;
import java.util.function.Function;

/** Strict JVM descriptor syntax. A null class mapping fails the WHOLE rewrite. */
public final class MixinDescriptors {
    private MixinDescriptors() {}
    public record MemberRef(String name, String descriptor, boolean method) {}
    public record TargetRef(String ownerInternal, MemberRef member) {}

    public static MemberRef parseMemberRef(String s) {
        if (s == null || s.isEmpty()) return null;
        int open = s.indexOf('(');
        int colon = s.indexOf(':');
        if (open > 0) {
            String name = s.substring(0, open), desc = s.substring(open);
            return isPlainIdentifier(name) && validMethod(desc) ? new MemberRef(name, desc, true) : null;
        }
        if (colon > 0) {
            String name = s.substring(0, colon), desc = s.substring(colon + 1);
            return isPlainIdentifier(name) && validField(desc) ? new MemberRef(name, desc, false) : null;
        }
        return isPlainIdentifier(s) ? new MemberRef(s, "", true) : null;
    }
    public static TargetRef parseAtTarget(String s) {
        if (s == null || !s.startsWith("L")) return null;
        int semi = s.indexOf(';');
        if (semi < 2) return null;
        String owner = s.substring(1, semi);
        MemberRef member = parseMemberRef(s.substring(semi + 1));
        if (!validInternal(owner) || member == null || member.descriptor().isEmpty()) return null;
        return new TargetRef(owner, member);
    }
    public static boolean validMethod(String desc) {
        if (desc == null || !desc.startsWith("(")) return false;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            i = typeEnd(desc, i, false); if (i < 0) return false;
        }
        return i < desc.length() && typeEnd(desc, i + 1, true) == desc.length();
    }
    public static boolean validField(String desc) {
        return desc != null && typeEnd(desc, 0, false) == desc.length();
    }
    private static int typeEnd(String s, int start, boolean allowVoid) {
        int i = start, arrays = 0;
        while (i < s.length() && s.charAt(i) == '[') { i++; arrays++; }
        if (i >= s.length() || arrays > 255) return -1;
        char ch = s.charAt(i);
        if (ch == 'V') return allowVoid && arrays == 0 ? i + 1 : -1;
        if ("BCDFIJSZ".indexOf(ch) >= 0) return i + 1;
        if (ch != 'L') return -1;
        int semi = s.indexOf(';', i + 1);
        return semi > i + 1 && validInternal(s.substring(i + 1, semi)) ? semi + 1 : -1;
    }
    public static boolean validInternal(String name) {
        if (name == null || name.isEmpty()) return false;
        for (String segment : name.split("/", -1)) if (!isPlainIdentifier(segment)) return false;
        return true;
    }
    public static boolean isPlainIdentifier(String s) {
        if (s == null || s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        return true;
    }
    /** Accept a complete method descriptor, a field descriptor, or void return type. */
    public static String rewriteTypeDescriptor(String desc, Function<String, String> classFn) {
        if (!validMethod(desc) && !validField(desc) && !"V".equals(desc)) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < desc.length();) {
            if (desc.charAt(i) != 'L') { out.append(desc.charAt(i++)); continue; }
            int semi = desc.indexOf(';', i);
            String replacement = classFn.apply(desc.substring(i + 1, semi));
            if (replacement == null || !validInternal(replacement)) return null;
            out.append('L').append(replacement).append(';'); i = semi + 1;
        }
        return out.toString();
    }
    public static String internalToFqcnLookup(String s) { return s.replace('/', '.').replace('$', '.'); }
    /** Explicitly conservative naming convention; callers also validate source round-trip. */
    public static String fqcnToInternal(String fqcn) {
        StringBuilder out = new StringBuilder(); boolean inClass = false;
        for (String part : fqcn.split("\\.", -1)) {
            if (!isPlainIdentifier(part)) return null;
            if (out.length() > 0) out.append(inClass ? '$' : '/');
            out.append(part);
            if (Character.isUpperCase(part.charAt(0))) inClass = true;
        }
        return inClass ? out.toString() : null;
    }
    public static boolean fqcnRoundTripOk(String internal) {
        return validInternal(internal) && internal.equals(fqcnToInternal(internalToFqcnLookup(internal)));
    }
}
