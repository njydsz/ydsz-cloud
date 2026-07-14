package com.njydsz.pmis.common.util.exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常处理工具类
 *
 * <p>提供全面的异常处理方法，功能对标 Apache Commons Lang3 ExceptionUtils 和 Hutool ExceptionUtil，
 * 并进行了增强和优化。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>异常链追踪</b>：支持获取完整的异常原因链</li>
 *   <li><b>根因分析</b>：快速定位异常根本原因</li>
 *   <li><b>异常包装</b>：支持受检异常转换为 RuntimeException</li>
 *   <li><b>静默抛出</b>：支持 Sneaky Throw 模式，无需声明异常即可抛出</li>
 *   <li><b>忽略执行</b>：支持异常忽略执行模式</li>
 *   <li><b>函数式支持</b>：提供 Supplier 延迟求值支持</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>异常信息：getMessage、getLocalizedMessage、getRootCauseMessage、getDetailedMessage</li>
 *   <li>堆栈跟踪：getStackTrace、printStackTrace、getStackTraceString、getTrace</li>
 *   <li>根本原因：getRootCause、getCauseChain、getThrowableList、getNthRootCause</li>
 *   <li>异常定位：getCause、isCausedBy、hasCause、getCause</li>
 *   <li>异常包装：wrap、wrapRuntime、sneak、sneakThrow</li>
 *   <li>异常抛出：throwException、rethrow、rethrowIfRuntimeException、rethrowIfInstanceOf</li>
 *   <li>异常处理：ignore、requireNonNull、setStackTrace</li>
 *   <li>异常判断：isEmpty、isNotEmpty、getExceptionType、getSimpleExceptionType</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 获取异常消息
 * String message = ExceptionUtils.getMessage(e);
 *
 * // 获取根本原因
 * Throwable rootCause = ExceptionUtils.getRootCause(e);
 *
 * // 获取完整异常链
 * List&lt;Throwable&gt; causes = ExceptionUtils.getThrowableList(e);
 *
 * // 判断异常类型
 * if (ExceptionUtils.isCausedBy(e, IOException.class)) { ... }
 *
 * // 包装为 RuntimeException
 * RuntimeException wrapped = ExceptionUtils.wrapRuntime(e);
 *
 * // 静默抛出受检异常
 * ExceptionUtils.sneakThrow(new IOException("业务异常"));
 *
 * // 忽略异常执行
 * ExceptionUtils.ignore(() -> {
 *     // 可能抛出异常的代码
 * });
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class ExceptionUtils {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionUtils.class);

    /**
     * 私有构造函数，防止外部实例化
     */
    private ExceptionUtils() {
        throw new UnsupportedOperationException("ExceptionUtils 是工具类，不允许被实例化");
    }

    /**
     * 获取异常消息
     */
    public static String getMessage(Throwable throwable) {
        return throwable != null ? throwable.getMessage() : null;
    }

    /**
     * 获取本地化消息
     */
    public static String getLocalizedMessage(Throwable throwable) {
        return throwable != null ? throwable.getLocalizedMessage() : null;
    }

    /**
     * 获取详细消息（包含类名）
     */
    public static String getDetailedMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        return throwable.getClass().getName() + ": " + throwable.getMessage();
    }

    /**
     * 获取根本原因的消息
     */
    public static String getRootCauseMessage(Throwable throwable) {
        Throwable root = getRootCause(throwable);
        return root != null ? root.getMessage() : null;
    }

    /**
     * 获取堆栈跟踪字符串
     */
    public static String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return writer.toString();
    }

    /**
     * 获取异常的详细堆栈信息
     * 将 Throwable 转换为完整的堆栈跟踪字符串
     *
     * @param throwable 异常对象
     * @return 包含完整堆栈信息的字符串
     */
    public static String getTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            String trace = sw.toString();
            logger.trace("Exception trace: {}", trace);
            return trace;
        } catch (Exception e) {
            logger.error("Failed to get exception trace", e);
            return "Failed to get exception trace: " + e.getMessage();
        }
    }

    /**
     * 获取堆栈跟踪行
     */
    public static String[] getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return new String[0];
        }

        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }

        String stackTrace = writer.toString();
        return stackTrace.split(System.getProperty("line.separator"));
    }

    /**
     * 获取异常链中的所有原因
     */
    public static List<Throwable> getThrowableList(Throwable throwable) {
        List<Throwable> list = new ArrayList<>();
        while (throwable != null) {
            list.add(throwable);
            throwable = throwable.getCause();
        }
        return list;
    }

    /**
     * 获取异常原因链
     */
    public static Throwable[] getCauseChain(Throwable throwable) {
        List<Throwable> list = getThrowableList(throwable);
        return list.toArray(new Throwable[0]);
    }

    /**
     * 获取根本原因
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable cause = throwable.getCause();
        if (cause == null) {
            return throwable;
        }

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 获取指定层级的异常原因
     */
    public static Throwable getCause(Throwable throwable, int index) {
        if (throwable == null || index < 0) {
            return null;
        }

        for (int i = 0; i < index; i++) {
            throwable = throwable.getCause();
            if (throwable == null) {
                return null;
            }
        }
        return throwable;
    }

    /**
     * 获取第 n 个根本原因
     */
    public static Throwable getNthRootCause(Throwable throwable, int n) {
        if (throwable == null || n < 0) {
            return null;
        }

        for (int i = 0; i < n; i++) {
            if (throwable.getCause() == null) {
                return throwable;
            }
            throwable = throwable.getCause();
        }
        return throwable;
    }

    /**
     * 判断异常链中是否包含指定类型的原因
     */
    public static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> causeType) {
        return getCause(throwable, causeType) != null;
    }

    /**
     * 判断异常链中是否包含指定类型的原因（包括自身）
     */
    public static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        while (throwable != null) {
            if (causeType.isInstance(throwable)) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    /**
     * 获取异常链中指定类型的原因
     */
    public static <T extends Throwable> T getCause(Throwable throwable, Class<T> causeType) {
        while (throwable != null) {
            if (causeType.isInstance(throwable)) {
                return causeType.cast(throwable);
            }
            throwable = throwable.getCause();
        }
        return null;
    }

    /**
     * 包装异常为 RuntimeException
     */
    public static RuntimeException wrapRuntime(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new RuntimeException(throwable);
    }

    /**
     * 包装异常为指定类型
     */
    public static <T extends Throwable> T wrap(Throwable throwable, Class<T> wrapperType) {
        try {
            if (wrapperType.isInstance(throwable)) {
                return wrapperType.cast(throwable);
            }

            if (wrapperType == RuntimeException.class) {
                return wrapperType.cast(new RuntimeException(throwable));
            }

            return wrapperType.getConstructor(Throwable.class).newInstance(throwable);
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap exception", e);
        }
    }

    /**
     * 包装异常并添加消息
     */
    public static RuntimeException wrap(String message, Throwable throwable) {
        if (throwable == null) {
            return new RuntimeException(message);
        }
        RuntimeException runtimeException = new RuntimeException(message, throwable);
        setStackTrace(runtimeException, throwable.getStackTrace());
        return runtimeException;
    }

    /**
     * 静默抛出异常（Sneaky Throw）
     * 可以在不声明异常的情况下抛出受检异常
     */
    
    public static RuntimeException sneak(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    /**
     * 静默抛出异常（不返回）
     */
    public static void sneakThrow(Throwable throwable) {
        throw sneak(throwable);
    }

    /**
     * 抛出异常
     */
    public static RuntimeException throwException(Throwable throwable) {
        return sneak(throwable);
    }

    /**
     * 如果为 null 则抛出异常
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }

    /**
     * 如果为 null 则抛出指定异常
     */
    public static <T, E extends Throwable> T requireNonNull(T obj, ThrowableSupplier<String> messageSupplier, Class<E> exceptionType) throws E {
        if (obj == null) {
            throw createException(messageSupplier.get(), exceptionType);
        }
        return obj;
    }

    /**
     * 忽略异常（执行 Runnable，不抛出异常）
     */
    public static void ignore(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            logger.debug("Ignored exception", e);
        }
    }

    /**
     * 忽略异常（执行 Callable，返回默认值）
     */
    public static <T> T ignore(ThrowableCallable<T> callable, T defaultValue) {
        try {
            return callable.call();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    /**
     * 忽略指定类型的异常
     */
    public static void ignore(Runnable runnable, Class<? extends Throwable> exceptionType) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                ExceptionUtils.sneakThrow(t);
            }
        }
    }

    /**
     * 重新抛出异常
     */
    public static void rethrow(Throwable throwable) {
        if (throwable != null) {
            sneakThrow(throwable);
        }
    }

    /**
     * 如果是 RuntimeException 则重新抛出，否则包装后抛出
     */
    public static void rethrowIfRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        } else if (throwable instanceof Error) {
            throw (Error) throwable;
        } else if (throwable != null) {
            throw new RuntimeException(throwable);
        }
    }

    /**
     * 如果是指定类型则重新抛出
     */
    public static <T extends Throwable> void rethrowIfInstanceOf(Throwable throwable, Class<T> exceptionType) throws T {
        if (exceptionType.isInstance(throwable)) {
            throw exceptionType.cast(throwable);
        } else if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        } else if (throwable != null) {
            throw new RuntimeException(throwable);
        }
    }

    /**
     * 设置堆栈跟踪
     */
    public static void setStackTrace(Throwable throwable, StackTraceElement[] stackTrace) {
        if (throwable != null) {
            throwable.setStackTrace(stackTrace);
        }
    }

    /**
     * 打印异常信息到标准错误
     */
    public static void printStackTrace(Throwable throwable) {
        if (throwable != null) {
            logger.error("Exception stack trace", throwable);
        }
    }

    /**
     * 获取异常类型名称
     */
    public static String getExceptionType(Throwable throwable) {
        return throwable != null ? throwable.getClass().getName() : null;
    }

    /**
     * 获取简单异常类型名称
     */
    public static String getSimpleExceptionType(Throwable throwable) {
        return throwable != null ? throwable.getClass().getSimpleName() : null;
    }

    /**
     * 判断异常是否为空或消息为空
     */
    public static boolean isEmpty(Throwable throwable) {
        return throwable == null || (throwable.getMessage() == null && throwable.getCause() == null);
    }

    /**
     * 判断异常是否不为空
     */
    public static boolean isNotEmpty(Throwable throwable) {
        return !isEmpty(throwable);
    }

    /**
     * 函数式接口：无参数无返回值（可抛出 Throwable）
     */
    @FunctionalInterface
    public interface ThrowableRunnable {
        void run() throws Throwable;
    }

    /**
     * 函数式接口：有参数有返回值（可抛出 Throwable）
     */
    @FunctionalInterface
    public interface ThrowableCallable<T> {
        T call() throws Throwable;
    }

    /**
     * 函数式接口：消息提供者
     */
    @FunctionalInterface
    public interface ThrowableSupplier<T> {
        T get();
    }

    /**
     * 创建异常
     */
    
    private static <T extends Throwable> T createException(String message, Class<T> exceptionType) throws T {
        try {
            if (exceptionType == RuntimeException.class) {
                return exceptionType.cast(new RuntimeException(message));
            }
            if (exceptionType == IllegalArgumentException.class) {
                return exceptionType.cast(new IllegalArgumentException(message));
            }
            if (exceptionType == IllegalStateException.class) {
                return exceptionType.cast(new IllegalStateException(message));
            }
            if (exceptionType == NullPointerException.class) {
                return exceptionType.cast(new NullPointerException(message));
            }
            return exceptionType.getConstructor(String.class).newInstance(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create exception", e);
        }
    }
}
