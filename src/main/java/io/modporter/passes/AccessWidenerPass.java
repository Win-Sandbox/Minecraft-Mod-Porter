package io.modporter.passes;

import io.modporter.engine.OutputFile;
import io.modporter.engine.PortContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Fabric AW v1/v2, line-atomic and namespace-conservative. Unknown runtime namespaces are never guessed. */
public final class AccessWidenerPass {
    private final PortContext ctx;
    private final ReferenceMapper mapper;
    public AccessWidenerPass(PortContext ctx) { this.ctx = ctx; mapper = new ReferenceMapper(ctx.resolver); }
    public OutputFile transform(String path, String content) {
        String[] lines = content.split("\\r\\n|\\n|\\r", -1);
        List<String> endings = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\r\\n|\\n|\\r").matcher(content);
        while (matcher.find()) endings.add(matcher.group());
        int headerIndex = 0;
        while (headerIndex < lines.length && code(lines[headerIndex]).isBlank()) headerIndex++;
        if (headerIndex == lines.length) { todo(path, 1, "缺少 AW 文件头，整文件保持原样"); return null; }
        String[] header = code(lines[headerIndex]).strip().split("\\s+");
        if (header.length != 3 || !header[0].equals("accessWidener") || !Set.of("v1", "v2").contains(header[1])) {
            todo(path, headerIndex + 1, "无效 AW v1/v2 文件头，整文件保持原样"); return null;
        }
        // The header namespace is safe only when the dataset explicitly names the same
        // runtime namespace on both sides.  Fabric's Yarn `named` and official
        // class-tweaker/AW files are both represented by mappingsChannel; intermediary
        // is deliberately excluded because this schema does not identify its runtime
        // namespace or provide a namespace conversion table.
        String sourceChannel = ctx.source().info.mappingsChannel;
        String targetChannel = ctx.target().info.mappingsChannel;
        boolean supportedChannel = "yarn".equals(sourceChannel) || "official".equals(sourceChannel);
        String expectedNamespace = "yarn".equals(sourceChannel) ? "named" : "official";
        if (!"fabric".equals(ctx.source().info.loader) || !"fabric".equals(ctx.target().info.loader)
                || !Objects.equals(sourceChannel, targetChannel)
                || !Objects.equals(expectedNamespace, header[2]) || !supportedChannel) {
            todo(path, headerIndex + 1, "AW 命名空间未证实：仅支持 Fabric 同命名空间 Yarn named 或 official → 同命名空间；intermediary/跨命名空间整文件保留");
            return null;
        }
        boolean changed = false;
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i], code = code(line);
            if (code.isBlank()) continue;
            List<int[]> spans = new ArrayList<>(); List<String> tokens = new ArrayList<>();
            Matcher words = Pattern.compile("\\S+").matcher(code);
            while (words.find()) { spans.add(new int[]{words.start(), words.end()}); tokens.add(words.group()); }
            String[] t = tokens.toArray(String[]::new);
            if (t.length < 3) { todo(path, i + 1, "规则字段不完整，保留整行"); continue; }
            boolean transitive = t[0].startsWith("transitive-");
            String access = transitive ? t[0].substring(11) : t[0];
            String kind = t[1];
            boolean accessOk = access.equals("accessible") || (access.equals("extendable") && !kind.equals("field"))
                    || (access.equals("mutable") && kind.equals("field"));
            if (!accessOk || (transitive && header[1].equals("v1")) || !Set.of("class", "field", "method").contains(kind)
                    || (kind.equals("class") ? t.length != 3 : t.length != 5)) {
                todo(path, i + 1, "规则种类/修饰符/版本/字段数无效，保留整行"); continue;
            }
            String[] replacement = t.clone();
            if (kind.equals("class")) replacement[2] = mapper.mapClass(t[2]);
            else {
                var mapped = mapper.member(t[2], t[3], t[4], kind.equals("method"));
                if (mapped == null) replacement[2] = null;
                else { replacement[2] = mapped.owner(); replacement[3] = mapped.name(); replacement[4] = mapped.descriptor(); }
            }
            if (replacement[2] == null) {
                todo(path, i + 1, "owner/成员/描述符缺少完整映射或有语义变化，保留整行"); continue;
            }
            StringBuilder rebuilt = new StringBuilder(line);
            for (int n = t.length - 1; n >= 2; n--) rebuilt.replace(spans.get(n)[0], spans.get(n)[1], replacement[n]);
            lines[i] = rebuilt.toString();
            if (!lines[i].equals(line)) {
                changed = true; ctx.info(path, i + 1, "accesswidener", line + " -> " + lines[i]);
            }
        }
        if (!changed) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) { out.append(lines[i]); if (i < endings.size()) out.append(endings.get(i)); }
        return new OutputFile(path, out.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String code(String line) { int hash = line.indexOf('#'); return hash < 0 ? line : line.substring(0, hash); }
    private void todo(String file, int line, String message) { ctx.todo(file, line, "accesswidener", message); }
}
