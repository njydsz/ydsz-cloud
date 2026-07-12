package com.njydsz.pmis.common.util;

/**
 * 系统工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class SystemUtils {

    private SystemUtils() {
    }

    /**
     * 获取系统属性
     *
     * @param key 属性键
     * @return 属性值
     */
    public static String getProperty(String key) {
        return System.getProperty(key);
    }

    /**
     * 获取系统属性（带默认值）
     */
    public static String getProperty(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    /**
     * 获取系统环境变量
     */
    public static String getEnv(String name) {
        return System.getenv(name);
    }

    /**
     * 获取操作系统名称
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * 获取操作系统架构
     */
    public static String getOsArch() {
        return System.getProperty("os.arch");
    }

    /**
     * 获取 Java 版本
     */
    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }

    /**
     * 获取用户目录
     */
    public static String getUserDir() {
        return System.getProperty("user.dir");
    }

    /**
     * 获取用户主目录
     */
    public static String getUserHome() {
        return System.getProperty("user.home");
    }

    /**
     * 获取临时目录
     */
    public static String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * 获取当前进程 ID
     */
    public static String getProcessId() {
        return ManagementFactoryUtils.getProcessId();
    }

    /**
     * 判断是否为 Windows 操作系统
     */
    public static boolean isWindows() {
        return getOsName() != null && getOsName().toLowerCase().contains("windows");
    }

    /**
     * 判断是否为 Linux 操作系统
     */
    public static boolean isLinux() {
        return getOsName() != null && getOsName().toLowerCase().contains("linux");
    }

    /**
     * 判断是否为 Mac 操作系统
     */
    public static boolean isMac() {
        return getOsName() != null && getOsName().toLowerCase().contains("mac");
    }

    private static class ManagementFactoryUtils {
        static String getProcessId() {
            String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return runtimeName.split("@")[0];
        }
    }
}
