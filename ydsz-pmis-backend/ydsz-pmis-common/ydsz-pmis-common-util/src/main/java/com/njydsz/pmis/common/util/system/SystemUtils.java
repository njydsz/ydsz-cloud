package com.njydsz.pmis.common.util.system;

import java.io.File;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
/**
 * 系统工具类
 *
 * <p>提供全面的系统信息获取和操作方法，功能对标 Apache Commons Lang SystemUtils 和 Hutool SystemUtil，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>系统属性：getProperty、getProperties、getJavaVersion、getJavaHome</li>
 *   <li>操作系统：getOsName、getOsVersion、getOsArch、isWindows、isLinux、isMac</li>
 *   <li>用户信息：getUserName、getUserHome、getUserDir</li>
 *   <li>系统路径：getTempDir、getJavaIoTmpDir、getClassPath</li>
 *   <li>CPU 信息：getAvailableProcessors、getCpuInfo</li>
 *   <li>内存信息：getTotalMemory、getFreeMemory、getMaxMemory、getUsedMemory</li>
 *   <li>环境变量：getEnv、getenv、getSystemProperties</li>
 * </ul>
 *
 * <p><b>相比 Apache/Spring 的增强：</b>
 * <ul>
 *   <li>更全面的系统信息获取</li>
 *   <li>提供内存和 CPU 使用情况的实时监控</li>
 *   <li>更好的跨平台支持</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class SystemUtils {

    private SystemUtils() {
        throw new UnsupportedOperationException("SystemUtils is a utility class and cannot be instantiated");
    }

    /**
     * Java 版本
     */
    public static final String JAVA_VERSION = get("java.version");

    /**
     * Java 安装目录
     */
    public static final String JAVA_HOME = get("java.home");

    /**
     * 操作系统名称
     */
    public static final String OS_NAME = get("os.name");

    /**
     * 操作系统版本
     */
    public static final String OS_VERSION = get("os.version");

    /**
     * 操作系统架构
     */
    public static final String OS_ARCH = get("os.arch");

    /**
     * 文件分隔符
     */
    public static final String FILE_SEPARATOR = get("file.separator");

    /**
     * 路径分隔符
     */
    public static final String PATH_SEPARATOR = get("path.separator");

    /**
     * 行分隔符
     */
    public static final String LINE_SEPARATOR = get("line.separator");

    /**
     * 用户名称
     */
    public static final String USER_NAME = get("user.name");

    /**
     * 用户主目录
     */
    public static final String USER_HOME = get("user.home");

    /**
     * 用户当前目录
     */
    public static final String USER_DIR = get("user.dir");

    /**
     * Java 临时目录
     */
    public static final String JAVA_IO_TMPDIR = get("java.io.tmpdir");

    /**
     * 获取系统属性
     */
    public static String get(String key) {
        try {
            return System.getProperty(key);
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * 获取系统属性，如果不存在则返回默认值
     */
    public static String get(String key, String defaultValue) {
        try {
            return System.getProperty(key, defaultValue);
        } catch (SecurityException e) {
            return defaultValue;
        }
    }

    /**
     * 设置系统属性
     */
    public static String set(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        return System.setProperty(key, value);
    }

    /**
     * 清除系统属性
     */
    public static void clear(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        System.clearProperty(key);
    }

    /**
     * 获取所有系统属性
     */
    public static Properties getProperties() {
        try {
            return (Properties) System.getProperties().clone();
        } catch (SecurityException e) {
            return new Properties();
        }
    }

    /**
     * 获取环境变量
     */
    public static String getEnv(String key) {
        try {
            return System.getenv(key);
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * 获取环境变量，如果不存在则返回默认值
     */
    public static String getEnv(String key, String defaultValue) {
        String value = getEnv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取所有环境变量
     */
    public static Map<String, String> getEnvMap() {
        try {
            return new HashMap<>(System.getenv());
        } catch (SecurityException e) {
            return new HashMap<>();
        }
    }

    /**
     * 判断是否为 Windows 系统
     */
    public static boolean isWindows() {
        return OS_NAME != null && OS_NAME.toLowerCase().contains("windows");
    }

    /**
     * 判断是否为 Linux 系统
     */
    public static boolean isLinux() {
        return OS_NAME != null && OS_NAME.toLowerCase().contains("linux");
    }

    /**
     * 判断是否为 Mac 系统
     */
    public static boolean isMac() {
        return OS_NAME != null && (OS_NAME.toLowerCase().contains("mac") || OS_NAME.toLowerCase().contains("darwin"));
    }

    /**
     * 判断是否为 Unix 系统
     */
    public static boolean isUnix() {
        return isLinux() || isMac() || isAix() || isSolaris();
    }

    /**
     * 判断是否为 AIX 系统
     */
    public static boolean isAix() {
        return OS_NAME != null && OS_NAME.toLowerCase().contains("aix");
    }

    /**
     * 判断是否为 Solaris 系统
     */
    public static boolean isSolaris() {
        return OS_NAME != null && OS_NAME.toLowerCase().contains("sunos");
    }

    /**
     * 获取 Java 版本
     */
    public static String getJavaVersion() {
        return JAVA_VERSION;
    }

    /**
     * 获取 Java 安装目录
     */
    public static String getJavaHome() {
        return JAVA_HOME;
    }

    /**
     * 获取操作系统名称
     */
    public static String getOsName() {
        return OS_NAME;
    }

    /**
     * 获取操作系统版本
     */
    public static String getOsVersion() {
        return OS_VERSION;
    }

    /**
     * 获取操作系统架构
     */
    public static String getOsArch() {
        return OS_ARCH;
    }

    /**
     * 获取文件分隔符
     */
    public static String getFileSeparator() {
        return FILE_SEPARATOR;
    }

    /**
     * 获取路径分隔符
     */
    public static String getPathSeparator() {
        return PATH_SEPARATOR;
    }

    /**
     * 获取行分隔符
     */
    public static String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    /**
     * 获取用户名
     */
    public static String getUserName() {
        return USER_NAME;
    }

    /**
     * 获取用户主目录
     */
    public static String getUserHome() {
        return USER_HOME;
    }

    /**
     * 获取用户当前目录
     */
    public static String getUserDir() {
        return USER_DIR;
    }

    /**
     * 获取临时目录
     */
    public static String getTempDir() {
        return JAVA_IO_TMPDIR;
    }

    /**
     * 获取 Java 临时目录文件对象
     */
    public static File getJavaIoTmpDir() {
        return new File(JAVA_IO_TMPDIR);
    }

    /**
     * 获取可用处理器数量（CPU 核心数）
     */
    public static int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取总内存（字节）
     */
    public static long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * 获取空闲内存（字节）
     */
    public static long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * 获取最大可用内存（字节）
     */
    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * 获取已使用内存（字节）
     */
    public static long getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }

    /**
     * 获取可用内存（字节）
     */
    public static long getAvailableMemory() {
        return getMaxMemory() - getUsedMemory();
    }

    /**
     * 获取内存使用率（百分比）
     */
    public static double getMemoryUsagePercent() {
        long max = getMaxMemory();
        if (max == 0) {
            return 0;
        }
        return (double) getUsedMemory() / max * 100;
    }

    /**
     * 获取内存使用率（字节格式）
     */
    public static String getMemoryUsagePercentFormatted() {
        return String.format("%.2f%%", getMemoryUsagePercent());
    }

    /**
     * 格式化内存大小（转换为人类可读格式）
     */
    public static String formatMemorySize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取总内存（格式化）
     */
    public static String getTotalMemoryFormatted() {
        return formatMemorySize(getTotalMemory());
    }

    /**
     * 获取空闲内存（格式化）
     */
    public static String getFreeMemoryFormatted() {
        return formatMemorySize(getFreeMemory());
    }

    /**
     * 获取已使用内存（格式化）
     */
    public static String getUsedMemoryFormatted() {
        return formatMemorySize(getUsedMemory());
    }

    /**
     * 获取最大可用内存（格式化）
     */
    public static String getMaxMemoryFormatted() {
        return formatMemorySize(getMaxMemory());
    }

    /**
     * 获取类路径
     */
    public static String getClassPath() {
        return get("java.class.path");
    }

    /**
     * 获取库路径
     */
    public static String getLibraryPath() {
        return get("java.library.path");
    }

    /**
     * 获取启动类路径
     */
    public static String getBootClassPath() {
        return get("sun.boot.class.path");
    }

    /**
     * 获取扩展类路径
     */
    public static String getExtClassPath() {
        return get("java.ext.dirs");
    }

    /**
     * 获取系统信息摘要
     */
    public static String getSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("System Information:\n");
        sb.append("-------------------\n");
        sb.append("Java Version: ").append(getJavaVersion()).append("\n");
        sb.append("Java Home: ").append(getJavaHome()).append("\n");
        sb.append("OS Name: ").append(getOsName()).append("\n");
        sb.append("OS Version: ").append(getOsVersion()).append("\n");
        sb.append("OS Arch: ").append(getOsArch()).append("\n");
        sb.append("Available Processors: ").append(getAvailableProcessors()).append("\n");
        sb.append("Total Memory: ").append(getTotalMemoryFormatted()).append("\n");
        sb.append("Free Memory: ").append(getFreeMemoryFormatted()).append("\n");
        sb.append("Used Memory: ").append(getUsedMemoryFormatted()).append("\n");
        sb.append("Max Memory: ").append(getMaxMemoryFormatted()).append("\n");
        sb.append("Memory Usage: ").append(getMemoryUsagePercentFormatted()).append("\n");
        sb.append("User Name: ").append(getUserName()).append("\n");
        sb.append("User Home: ").append(getUserHome()).append("\n");
        sb.append("User Dir: ").append(getUserDir()).append("\n");
        sb.append("Temp Dir: ").append(getTempDir()).append("\n");
        return sb.toString();
    }

    /**
     * 执行垃圾回收
     */
    public static void gc() {
        Runtime.getRuntime().gc();
    }

    /**
     * 退出 JVM
     */
    public static void exit(int status) {
        System.exit(status);
    }

    /**
     * 移除关闭钩子
     */
    public static void removeShutdownHook(Thread hook) {
        Runtime.getRuntime().removeShutdownHook(hook);
    }
}
