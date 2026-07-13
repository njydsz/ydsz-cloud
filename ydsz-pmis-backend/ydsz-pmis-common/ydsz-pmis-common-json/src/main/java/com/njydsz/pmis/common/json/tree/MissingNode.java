package com.njydsz.pmis.common.json.tree;

/**
 * JSON 缺失节点（字段不存在时返回）
 */
public final class MissingNode extends JsonNode {

    private static final MissingNode INSTANCE = new MissingNode();

    private MissingNode() {}

    public static MissingNode getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isMissing() {
        return true;
    }

    @Override
    public String asText() {
        return "";
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
        return "";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MissingNode;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
