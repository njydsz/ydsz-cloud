package com.remisoft.common.json.testbean;

import com.remisoft.common.json.annotation.JsonSerialize;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;

/**
 * 自定义序列化器测试 Bean。
 *
 * <p>{@code @JsonSerialize(using = MoneySerializer.class)} 指定使用新版
 * {@link com.remisoft.common.json.serializer.JsonSerializer}（JSONWriter 版），
 * 验证 D2 移除旧 {@code api.JsonSerializer} 后单一调用路径生效。</p>
 */
@JsonSerialize(using = MoneyBean.MoneySerializer.class)
public class MoneyBean {
    private final long cents;

    public MoneyBean() { this(0); }
    public MoneyBean(long cents) { this.cents = cents; }

    public long getCents() { return cents; }

    /**
     * 新版自定义序列化器：直接写入 JSONWriter，零拷贝。
     * 将 cents 输出为 "xx.xx" 格式的 JSON 字符串。
     */
    public static class MoneySerializer implements JsonSerializer<MoneyBean> {
        @Override
        public void serialize(MoneyBean value, JSONWriter out) {
            long cents = value.getCents();
            String formatted = String.format("%d.%02d", cents / 100, Math.abs(cents % 100));
            out.writeString(formatted);
        }
    }
}
