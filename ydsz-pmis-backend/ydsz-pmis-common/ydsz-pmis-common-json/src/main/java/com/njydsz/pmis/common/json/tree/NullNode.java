package com.njydsz.pmis.common.json.tree;

/**
 * JSON null 节点
 */
public final class NullNode extends JsonNode {

    private static final NullNode INSTANCE = new NullNode();

    private NullNode() {}

    public static NullNode getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public String asText() {
        return null;
    }

    @Override
    public String asText(String defaultValue) {
        return defaultValue;
    }

    @Override
    public Object asValue() {
        return null;
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NullNode;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
