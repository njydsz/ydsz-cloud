package com.njydsz.common.json.tree;

/**
 * JSON 数值节点
 *
 * <p>对标 Jackson NumberNode，表示一个 JSON 数字值。
 * 支持整数、浮点数、BigDecimal 等多种数值类型。</p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *   <li>不可变对象，线程安全</li>
 *   <li>支持 int、long、double、BigDecimal 等类型</li>
 *   <li>提供多种数值转换方法</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * NumberNode intNode = new NumberNode(42);
 * int value = intNode.asInt(); // 42
 *
 * NumberNode doubleNode = new NumberNode(3.14);
 * double pi = doubleNode.asDouble(); // 3.14
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class NumberNode extends JsonNode {

    private final Number value;

    /**
     * 创建数值节点
     *
     * @param value 数值，支持 Integer、Long、Double、Float、BigDecimal 等
     */
    public NumberNode(Number value) {
        this.value = value;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    public int asInt() {
        return value.intValue();
    }

    @Override
    public int asInt(int defaultValue) {
        return value != null ? value.intValue() : defaultValue;
    }

    @Override
    public long asLong() {
        return value.longValue();
    }

    @Override
    public long asLong(long defaultValue) {
        return value != null ? value.longValue() : defaultValue;
    }

    @Override
    public double asDouble() {
        return value.doubleValue();
    }

    @Override
    public double asDouble(double defaultValue) {
        return value != null ? value.doubleValue() : defaultValue;
    }

    /**
     * 获取原始数值对象
     *
     * @return 原始 Number 对象
     */
    public Number numberValue() {
        return value;
    }

    @Override
    public Object asValue() {
        return value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "null";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NumberNode)) {
            return false;
        }
        return value.equals(((NumberNode) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
