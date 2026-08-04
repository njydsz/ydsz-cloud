package com.njydsz.common.json.internal;

/**
 * GraalVM Native Image 运行时环境探测器。
 *
 * <p>在 GraalVM Native Image 中运行时代码被 AOT 编译为原生可执行文件，
 * ASM 运行期字节码生成、动态类加载等能力不可用。本探测器用于区分运行环境，
 * 使 YdszJson ASM 序列化器在不同环境下自动选择合适的序列化路径。
 *
 * <p><b>检测机制：</b>
 * <ul>
 *   <li>Native Image 构建时系统属性 {@code org.graalvm.nativeimage.imagecode}
 *       会被注入到两种值：{@code runtime}（运行时）、{@code buildtime}（构建期）。</li>
 *   <li>JVM 环境下该属性不存在（{@code System.getProperty} 返回 null）。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * if (NativeImageDetector.isInNativeImage()) {
 *     // GraalVM 下走反射序列化路径
 *     return reflectiveSerialize(obj);
 * } else {
 *     // JVM 下走 ASM 高性能路径
 *     return asmSerialize(obj);
 * }
 * }</pre>
 *
 * <p><b>注意：</b>本探测仅在 HotSpot JVM / GraalVM Native Image 下有明确语义，
 * SubstrateVM 其他变体（如 Mandrel）的表现由构建方保证，不做额外区分。
 *
 * @author ydsz-team
 * @see <a href="https://www.graalvm.org/latest/reference-manual/native-image/metadata/">GraalVM Native Image 元数据</a>
 * @since 1.0.0
 */
public final class NativeImageDetector {

    /** GraalVM Native Image 内置系统属性标记 */
    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    private static final boolean IS_NATIVE_IMAGE = System.getProperty(IMAGE_CODE_PROPERTY) != null;

    private NativeImageDetector() {
        // 禁止实例化
    }

    /**
     * 判断当前是否运行在 GraalVM Native Image 中。
     *
     * @return true 表示为 Native Image 运行时；false 表示普通 JVM 或构建期
     */
    public static boolean isInNativeImage() {
        return IS_NATIVE_IMAGE;
    }

    /**
     * 获取详细的运行时环境标识字符串。
     *
     * @return {@code "native-image"}、{@code "jvm"} 或 {@code "unknown"}
     */
    public static String getRuntimeKind() {
        if (IS_NATIVE_IMAGE) {
            return "native-image";
        }
        // JVM 下进一步区分 HotSpot / OpenJ9 等
        String vmName = System.getProperty("java.vm.name", "").toLowerCase();
        if (vmName.contains("openj9")) {
            return "openj9";
        }
        if (vmName.contains("hotspot") || vmName.contains("openjdk")) {
            return "jvm";
        }
        return "unknown";
    }

    /**
     * 判断当前运行在 JVM（非 Native Image）环境。
     *
     * @return true 表示为普通 JVM；false 表示 Native Image 或未知环境
     */
    public static boolean isInJvm() {
        return !IS_NATIVE_IMAGE;
    }
}
