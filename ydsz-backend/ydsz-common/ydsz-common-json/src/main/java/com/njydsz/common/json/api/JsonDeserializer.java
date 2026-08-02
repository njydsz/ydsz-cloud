package com.njydsz.common.json.api;

/**
 * 自定义 JSON 反序列化器接口（旧版，String 入参版）。
 *
 * <p>通过 {@code @JsonDeserialize(using = ...)} 注解指定，
 * 在反序列化目标类型时使用自定义逻辑替代默认反序列化。</p>
 *
 * <p><b>迁移说明：</b></p>
 * <p>此接口已被 {@link com.njydsz.common.json.deserializer.JsonDeserializer} 替代。
 * 新接口直接使用 {@link com.njydsz.common.json.reader.JSONReader} 进行流式解析，
 * 避免完整 JSON 字符串复制，在大 JSON 场景下显著降低内存峰值。</p>
 *
 * <p><b>迁移示例（Before → After）：</b></p>
 * <pre>
 * // 旧版（String 入参版）—— 需要完整 JSON 字符串
 * public class MyDeserializer implements api.JsonDeserializer&lt;MyType&gt; {
 *     public MyType deserialize(String json, Class&lt;MyType&gt; type) {
 *         // 需要手动解析 json 字符串
 *         return parseManually(json);
 *     }
 * }
 *
 * // 新版（JSONReader 版）—— 流式解析，逐 token 消费
 * public class MyDeserializer implements deserializer.JsonDeserializer&lt;MyType&gt; {
 *     public MyType deserialize(JSONReader reader, java.lang.reflect.Type type) {
 *         MyType obj = new MyType();
 *         reader.next(); // 读取 token
 *         // ... 按需解析
 *         return obj;
 *     }
 * }
 * </pre>
 *
 * @param <T> 要反序列化的类型
 * @see com.njydsz.common.json.deserializer.JsonDeserializer 新接口
 * @deprecated 自 1.0.0 起请迁移至 {@link com.njydsz.common.json.deserializer.JsonDeserializer}。
 *             本接口将在后续大版本中删除（{@code forRemoval = true}）。
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public interface JsonDeserializer<T> {

    /**
     * 将 JSON 字符串反序列化为对象。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化后的对象
     */
    T deserialize(String json, Class<T> type);
}
