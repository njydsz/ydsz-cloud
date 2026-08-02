package com.njydsz.common.json.api;

/**
 * 自定义 JSON 序列化器接口（旧版，String 返回版）。
 *
 * <p>通过 {@code @JsonSerialize(using = ...)} 注解指定，
 * 在序列化目标类型时使用自定义逻辑替代默认序列化。</p>
 *
 * <p><b>迁移说明：</b></p>
 * <p>此接口已被 {@link com.njydsz.common.json.serializer.JsonSerializer} 替代。
 * 新接口直接写入 {@link com.njydsz.common.json.writer.JSONWriter}，避免中间 String 分配，
 * 在高吞吐场景下可减少 30%+ 的 GC 压力。</p>
 *
 * <p><b>迁移示例（Before → After）：</b></p>
 * <pre>
 * // 旧版（String 返回版）—— 会产生中间 String + 双重序列化
 * public class MySerializer implements api.JsonSerializer&lt;MyType&gt; {
 *     public String serialize(MyType value) {
 *         return "{\"id\":" + value.getId() + "}";  // 字符串拼接，不安全
 *     }
 * }
 *
 * // 新版（JSONWriter 版）—— 零拷贝，安全高效
 * public class MySerializer implements serializer.JsonSerializer&lt;MyType&gt; {
 *     public void serialize(MyType value, JSONWriter out) {
 *         out.writeStartObject();
 *         out.writeName("id");
 *         out.writeNumber(value.getId());
 *         out.writeEndObject();
 *     }
 * }
 * </pre>
 *
 * @param <T> 要序列化的类型
 * @see com.njydsz.common.json.serializer.JsonSerializer 新接口
 * @deprecated 自 1.0.0 起请迁移至 {@link com.njydsz.common.json.serializer.JsonSerializer}。
 *             本接口将在后续大版本中删除（{@code forRemoval = true}）。
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public interface JsonSerializer<T> {

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 要序列化的对象
     * @return JSON 字符串
     */
    String serialize(T value);
}
