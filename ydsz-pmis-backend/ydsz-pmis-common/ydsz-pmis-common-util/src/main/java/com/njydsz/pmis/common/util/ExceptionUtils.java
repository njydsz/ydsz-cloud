package com.njydsz.pmis.common.util;

/**
 * 异常工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * 获取异常的根因
     *
     * @param throwable 异常
     * @return 根因异常
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 获取异常堆栈字符串
     *
     * @param throwable 异常
     * @return 堆栈字符串
     */
    public static String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * 获取异常的简要信息
     *
     * @param throwable 异常
     * @return 简要信息
     */
    public static String getSimpleMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        Throwable root = getRootCause(throwable);
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * 将异常包装为 RuntimeException
     *
     * @param throwable 异常
     * @return RuntimeException
     */
    public static RuntimeException wrap(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new RuntimeException(throwable);
    }

    /**
     * 将异常包装为 RuntimeException（带消息）
     *
     * @param message   消息
     * @param throwable 异常
     * @return RuntimeException
     */
    public static RuntimeException wrap(String message, Throwable throwable) {
        return new RuntimeException(message, throwable);
    }

    /**
     * 静默抛出异常（绕过编译器检查）
     *
     * @param throwable 异常
     */
    public static void sneakyThrow(Throwable throwable) {
        ExceptionUtils.<RuntimeException>sneakyThrow0(throwable);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow0(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
