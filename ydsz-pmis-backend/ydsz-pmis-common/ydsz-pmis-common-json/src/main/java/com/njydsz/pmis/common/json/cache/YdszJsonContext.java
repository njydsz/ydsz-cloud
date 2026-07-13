package com.njydsz.pmis.common.json.cache;

import com.njydsz.pmis.common.json.engine.YdszSerializerEngine;
import com.njydsz.pmis.common.json.parser.YdszJsonParser;

/**
 * YdszJson 统一 ThreadLocal 上下文管理。
 *
 * <p>集中管理所有 ThreadLocal 变量的清理，防止线程池环境下的内存泄漏。
 * 在 Web 容器 / 线程池环境中，应在请求结束或任务完成时调用 {@link #clearAll()} 方法。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>Spring MVC 请求结束后（通过 Filter / Interceptor）</li>
 *   <li>线程池任务执行完成后（通过 TaskDecorator / afterExecute）</li>
 *   <li>定时任务执行完成后</li>
 * </ul>
 *
 * <p><b>ThreadLocal 清理范围：</b>
 * <ul>
 *   <li>YdszSerializationProvider — 10 个 ThreadLocal（StringBuilder/JSONWriter/循环引用检测等）</li>
 *   <li>YdszJsonParser — 4 个 ThreadLocal（char[]/Map/List/StringBuilder 对象池）</li>
 *   <li>SerializerCache — ThreadLocal 缓存</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public final class YdszJsonContext {

    private YdszJsonContext() {
        throw new UnsupportedOperationException("YdszJsonContext is a utility class");
    }

    /**
     * 清理所有 YdszJson 相关的 ThreadLocal 变量。
     *
     * <p><b>重要：</b>在 Web 容器线程池或定时任务线程池中，
     * 应在每次请求/任务完成后调用此方法，防止 ThreadLocal 内存泄漏。
     */
    public static void clearAll() {
        // 清理序列化引擎的 ThreadLocal（包括 StringBuilder/JSONWriter/循环引用检测等）
        YdszSerializerEngine.clearAllCaches();

        // 清理底层解析器的 ThreadLocal（char[]/Map/List/StringBuilder 对象池）
        YdszJsonParser.clearThreadLocals();

        // 清理序列化器缓存
        SerializerCache.clear();
    }

    /**
     * 获取当前线程的 ThreadLocal 内存占用估计（用于诊断）。
     *
     * @return 估计的字节数
     */
    public static long estimateThreadLocalMemory() {
        long total = 0;
        total += 4096;  // StringBuilder ~4KB
        total += 4096;  // JSONWriter ~4KB
        total += 2048;  // 循环引用检测 Set ~2KB
        total += 8192;  // Parser char[] ~8KB
        total += 4096;  // Parser Map ~4KB
        total += 4096;  // Parser List ~4KB
        total += 256;   // Parser StringBuilder ~256B
        return total;
    }
}
