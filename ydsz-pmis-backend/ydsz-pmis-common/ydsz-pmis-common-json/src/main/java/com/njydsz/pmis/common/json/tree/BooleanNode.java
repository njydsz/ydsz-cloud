package com.njydsz.pmis.common.json.tree;

/**
 * JSON 布尔节点
 */
public final class BooleanNode extends JsonNode {

    private static final BooleanNode TRUE = new BooleanNode(true);
    private static final BooleanNode FALSE = new BooleanNode(false);

    private final boolean value;

    private BooleanNode(boolean value) {
        this.value = value;
    }

    public static BooleanNode of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean asBoolean() {
        return value;
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
        return value;
    }

    @Override
    public String asText() {
        return String.valueOf(value);
    }

    @Override
    public Object asValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BooleanNode)) {
            return false;
        }
        return value == ((BooleanNode) obj).value;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }
}
