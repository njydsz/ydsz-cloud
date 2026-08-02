package com.njydsz.common.json.asm;

/**
 * GraalVM Native Image 环境检测器。
 *
 * <p>在 GraalVM Native Image 中，ASM 运行时字节码生成不可用，
 * 需要自动降级为反射模式。本类提供运行时环境检测能力。</p>
 *
 * <p><b>检测方式：</b></p>
 * <ul>
 *   <li>检查 {@code org.graalvm.nativeimage.ImageInfo} 类是否存在于 classpath</li>
 *   <li>检查系统属性 {@code org.graalvm.nativeimage.imagecode} 是否设置</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class GraalVmDetector {

    private static final Boolean IN_NATIVE_IMAGE;

    static {
        IN_NATIVE_IMAGE = detectNativeImage();
    }

    private GraalVmDetector() {
        throw new UnsupportedOperationException("GraalVmDetector is a utility class");
    }

    /**
     * 检测当前是否运行在 GraalVM Native Image 中。
     *
     * @return true 如果在 Native Image 中运行
     */
    public static boolean isInNativeImage() {
        return Boolean.TRUE.equals(IN_NATIVE_IMAGE);
    }

    /**
     * 检测是否允许 ASM 字节码生成。
     *
     * <p>在 Native Image 中禁止 ASM，在常规 JVM 中允许。</p>
     *
     * @return true 如果 ASM 可用
     */
    public static boolean isAsmAllowed() {
        return !isInNativeImage();
    }

    private static boolean detectNativeImage() {
        // 方式 1：检查 GraalVM ImageInfo 类
        try {
            Class.forName("org.graalvm.nativeimage.ImageInfo", false,
                Thread.currentThread().getContextClassLoader());
            // 类存在，检查是否在运行时
            String imageCode = System.getProperty("org.graalvm.nativeimage.imagecode");
            if (imageCode != null) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // ImageInfo 类不存在，不是 GraalVM 环境
        }

        // 方式 2：检查 SubstrateVM 相关系统属性
        String vmName = System.getProperty("java.vm.name", "");
        if (vmName.contains("Substrate VM") || vmName.contains("GraalVM")) {
            String imageCode = System.getProperty("org.graalvm.nativeimage.imagecode");
            return imageCode != null;
        }

        return false;
    }
}
