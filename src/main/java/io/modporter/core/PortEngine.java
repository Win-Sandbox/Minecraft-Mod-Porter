package io.modporter.core;

import java.util.List;

/**
 * 转换引擎的对外入口。CLI 和未来的 UI 都只依赖这个接口。
 */
public interface PortEngine {

    /** 执行一次转换。listener 可为 null（等价于 ProgressListener.NOOP）。 */
    PortResult port(PortRequest request, ProgressListener listener);

    /** 当前映射数据目录支持的所有版本（按加载器分组的版本描述）。 */
    List<VersionDescriptor> supportedVersions();

    /** 简单的版本描述，供 UI 展示可选版本列表。 */
    record VersionDescriptor(String loader, String mcVersion) {}
}
