package com.njydsz.common.exception.sanitize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 堆栈跟踪脱敏器
 *
 * <p>用于在生产环境中对异常堆栈进行脱敏处理，移除或隐藏敏感信息：
 * <ul>
 *   <li>移除框架内部堆栈（Spring、MyBatis、Tomcat 等）</li>
 *   <li>隐藏敏感路径信息（如服务器文件系统路径）</li>
 *   <li>限制堆栈深度，防止堆栈过长</li>
 *   <li>移除包含敏感关键字的堆栈帧</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * StackTraceSanitizer sanitizer = new StackTraceSanitizer();
 * Throwable sanitized = sanitizer.sanitize(throwable);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class StackTraceSanitizer {

    /** 默认最大堆栈深度 */
    private static final int DEFAULT_MAX_DEPTH = 50;

    /** 框架包前缀（这些堆栈帧会被移除） */
    private static final List<String> FRAMEWORK_PREFIXES = Arrays.asList(
            "org.springframework.",
            "org.apache.catalina.",
            "org.apache.coyote.",
            "org.apache.tomcat.",
            "org.mybatis.",
            "com.baomidou.",
            "org.hibernate.",
            "org.eclipse.jetty.",
            "io.undertow.",
            "reactor.core.",
            "org.apache.kafka.",
            "redis.clients.",
            "org.redisson.",
            "com.alibaba.nacos.",
            "com.alibaba.dubbo.",
            "org.apache.dubbo.",
            "com.netflix.",
            "io.micrometer.",
            "sun.reflect.",
            "java.lang.reflect.",
            "jdk.internal.reflect."
    );

    /** 敏感关键字（包含这些关键字的堆栈帧会被移除） */
    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "password",
            "secret",
            "token",
            "credential",
            "private_key",
            "api_key",
            "access_key"
    );

    /** 最大堆栈深度 */
    private final int maxDepth;

    /** 是否移除框架堆栈 */
    private final boolean removeFrameworkFrames;

    /** 是否隐藏敏感路径 */
    private final boolean hideSensitivePaths;

    /**
     * 使用默认配置创建脱敏器
     */
    public StackTraceSanitizer() {
        this(DEFAULT_MAX_DEPTH, true, true);
    }

    /**
     * 使用自定义配置创建脱敏器
     *
     * @param maxDepth              最大堆栈深度
     * @param removeFrameworkFrames 是否移除框架堆栈
     * @param hideSensitivePaths    是否隐藏敏感路径
     */
    public StackTraceSanitizer(int maxDepth, boolean removeFrameworkFrames, boolean hideSensitivePaths) {
        this.maxDepth = maxDepth;
        this.removeFrameworkFrames = removeFrameworkFrames;
        this.hideSensitivePaths = hideSensitivePaths;
    }

    /**
     * 对异常堆栈进行脱敏处理
     *
     * @param throwable 原始异常
     * @return 脱敏后的异常（新实例，不修改原始异常）
     */
    public Throwable sanitize(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        // 创建新的异常实例，避免修改原始异常
        Throwable sanitized = createSanitizedCopy(throwable);

        // 处理堆栈跟踪
        StackTraceElement[] originalStack = sanitized.getStackTrace();
        StackTraceElement[] sanitizedStack = sanitizeStackTrace(originalStack);
        sanitized.setStackTrace(sanitizedStack);

        // 递归处理 cause
        Throwable cause = sanitized.getCause();
        if (cause != null && cause != sanitized) {
            Throwable sanitizedCause = sanitize(cause);
            sanitized.initCause(sanitizedCause);
        }

        return sanitized;
    }

    /**
     * 处理堆栈跟踪数组
     */
    private StackTraceElement[] sanitizeStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return stackTrace;
        }

        List<StackTraceElement> sanitizedFrames = new ArrayList<>();

        for (StackTraceElement frame : stackTrace) {
            // 检查是否应该移除此帧
            if (shouldRemoveFrame(frame)) {
                continue;
            }

            // 处理敏感路径
            if (hideSensitivePaths) {
                frame = hideSensitivePath(frame);
            }

            sanitizedFrames.add(frame);

            // 限制堆栈深度
            if (sanitizedFrames.size() >= maxDepth) {
                break;
            }
        }

        return sanitizedFrames.toArray(new StackTraceElement[0]);
    }

    /**
     * 判断是否应该移除此堆栈帧
     */
    private boolean shouldRemoveFrame(StackTraceElement frame) {
        if (frame == null) {
            return true;
        }

        String className = frame.getClassName();

        // 移除框架堆栈
        if (removeFrameworkFrames) {
            for (String prefix : FRAMEWORK_PREFIXES) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
        }

        // 移除包含敏感关键字的堆栈帧
        String frameString = frame.toString();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (frameString.toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 隐藏敏感路径信息
     */
    private StackTraceElement hideSensitivePath(StackTraceElement frame) {
        String fileName = frame.getFileName();
        if (fileName == null) {
            return frame;
        }

        // 检查是否包含文件系统路径（如 /home/user/project/...）
        if (fileName.contains("/") || fileName.contains("\\")) {
            // 只保留文件名，移除路径
            String simpleFileName = fileName;
            int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
            if (lastSlash >= 0 && lastSlash < fileName.length() - 1) {
                simpleFileName = fileName.substring(lastSlash + 1);
            }

            return new StackTraceElement(
                    frame.getClassName(),
                    frame.getMethodName(),
                    simpleFileName,
                    frame.getLineNumber()
            );
        }

        return frame;
    }

    /**
     * 创建异常的脱敏副本
     */
    private Throwable createSanitizedCopy(Throwable throwable) {
        try {
            // 尝试使用构造函数创建副本
            if (throwable instanceof RuntimeException) {
                return new RuntimeException(throwable.getMessage(), throwable.getCause());
            } else if (throwable instanceof Exception) {
                return new Exception(throwable.getMessage(), throwable.getCause());
            } else if (throwable instanceof Error) {
                return new Error(throwable.getMessage(), throwable.getCause());
            }
        } catch (Exception e) {
            // 创建副本失败，返回原始异常
            return throwable;
        }

        return throwable;
    }
}
