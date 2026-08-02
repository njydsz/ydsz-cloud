package com.njydsz.common.json.autotype;

import java.io.ObjectInputFilter;

/**
 * 安全的 Java 原生反序列化过滤器
 *
 * <p>将 Java 原生反序列化（{@link java.io.ObjectInputStream}）的类型校验
 * 统一委托给 {@link AutoTypeChecker}，消除散落在各模块的白名单硬编码，
 * 实现 JSON AutoType 与 Java 原生反序列化的<b>单一来源白名单</b>。
 *
 * <p><b>已废弃</b>：此过滤器针对 Java 原生 {@code ObjectInputStream} 反序列化，
 * 而非 JSON 反序列化。JSON 库不应承担 Java 原生序列化安全过滤的职责。
 * 建议使用 JDK 内置的 {@code ObjectInputFilter.Configurator}
 * （JDK 9+ 的 {@code -Djdk.serialFilter} 系统属性或
 * {@code ObjectInputFilter.Configurator.setSerialFilter()} API）替代。</p>
 *
 * <p><b>校验维度：</b>
 * <ul>
 *   <li><b>深度限制</b>：防止深度嵌套导致的栈溢出（默认 ≤5）</li>
 *   <li><b>引用数量限制</b>：防止引用炸弹（默认 ≤500000）</li>
 *   <li><b>字节流限制</b>：防止超大流导致 OOM（默认 ≤256MB）</li>
 *   <li><b>类型白名单</b>：委托 {@link AutoTypeChecker}，支持内部类 fallback</li>
 * </ul>
 *
 * <p><b>内部类 fallback 策略：</b>
 * 当类名为内部类（包含 {@code $}）且其外部类在 {@link AutoTypeChecker} 白名单中时，
 * 自动允许该内部类。这解决了 {@code java.util.HashMap$Node} 等 JDK 内部类
 * 未被显式列入白名单但实际安全的问题。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * ObjectInputStream ois = new ObjectInputStream(fis);
 * ois.setObjectInputFilter(SafeObjectInputFilter.create());
 * Object obj = ois.readObject();
 * </pre>
 *
 * <p><b>自定义限制：</b>
 * <pre>
 * ObjectInputFilter filter = SafeObjectInputFilter.create(
 *     8,                  // 最大深度
 *     1_000_000L,         // 最大引用数
 *     512L * 1024 * 1024  // 最大字节数（512MB）
 * );
 * </pre>
 *
 * <p><b>扩展白名单：</b>
 * <pre>
 * // 业务自定义类需要反序列化时，显式加入白名单
 * AutoTypeChecker.addToWhitelist("com.example.MySerializableClass");
 * </pre>
 *
 * @see AutoTypeChecker
 * @see ObjectInputFilter
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 此类针对 Java 原生 ObjectInputStream 反序列化，超出 JSON 库职责边界。
 *             建议使用 JDK 内置 ObjectInputFilter.Configurator 替代。
 *             将在 2.0.0 版本移除。
 */
@Deprecated(since = "1.4.0", forRemoval = true)
public final class SafeObjectInputFilter {

    /** 默认最大反序列化深度 */
    public static final int DEFAULT_MAX_DEPTH = 5;

    /** 默认最大引用数量 */
    public static final long DEFAULT_MAX_REFERENCES = 500_000L;

    /** 默认最大反序列化字节数（256MB） */
    public static final long DEFAULT_MAX_STREAM_BYTES = 256L * 1024 * 1024;

    private SafeObjectInputFilter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 创建一个安全的 {@link ObjectInputFilter}，使用默认配置。
     *
     * <p>类型校验委托给 {@link AutoTypeChecker}。
     *
     * @return 配置了默认限制和白名单的 ObjectInputFilter
     */
    public static ObjectInputFilter create() {
        return create(DEFAULT_MAX_DEPTH, DEFAULT_MAX_REFERENCES, DEFAULT_MAX_STREAM_BYTES);
    }

    /**
     * 创建一个安全的 {@link ObjectInputFilter}，使用自定义限制。
     *
     * <p>类型校验始终委托给 {@link AutoTypeChecker}，不受参数影响。
     *
     * @param maxDepth 最大反序列化深度（嵌套层级）
     * @param maxReferences 最大引用数量
     * @param maxStreamBytes 最大反序列化字节数
     * @return 配置了指定限制和白名单的 ObjectInputFilter
     */
    public static ObjectInputFilter create(int maxDepth, long maxReferences, long maxStreamBytes) {
        return filterInfo -> {
            // 限制反序列化深度
            if (filterInfo.depth() > maxDepth) {
                return ObjectInputFilter.Status.REJECTED;
            }
            // 限制引用数量
            if (filterInfo.references() > maxReferences) {
                return ObjectInputFilter.Status.REJECTED;
            }
            // 限制字节数
            if (filterInfo.streamBytes() > maxStreamBytes) {
                return ObjectInputFilter.Status.REJECTED;
            }
            // 白名单类检查
            if (filterInfo.serialClass() != null) {
                String className = filterInfo.serialClass().getName();
                // 数组类型：交给元素级检查（每个元素会单独触发 filter 回调）
                if (className.startsWith("[")) {
                    return ObjectInputFilter.Status.UNDECIDED;
                }
                // 委托给 AutoTypeChecker 统一白名单
                if (AutoTypeChecker.isTypeAllowed(className)) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                // 内部类 fallback：外部类在白名单中则允许其内部类
                // 解决 java.util.HashMap$Node 等 JDK 内部类未显式列入白名单的问题
                int dollar = className.lastIndexOf('$');
                if (dollar > 0) {
                    String outerClassName = className.substring(0, dollar);
                    if (AutoTypeChecker.isTypeAllowed(outerClassName)) {
                        return ObjectInputFilter.Status.ALLOWED;
                    }
                }
                return ObjectInputFilter.Status.REJECTED;
            }
            return ObjectInputFilter.Status.UNDECIDED;
        };
    }
}
