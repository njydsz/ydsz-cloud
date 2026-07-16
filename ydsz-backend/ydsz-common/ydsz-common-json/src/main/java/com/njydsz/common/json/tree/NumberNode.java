package com.njydsz.common.json.tree;

/**
 * JSON 数值节点
 */
public final class NumberNode extends JsonNode {

    private final Number value;

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
