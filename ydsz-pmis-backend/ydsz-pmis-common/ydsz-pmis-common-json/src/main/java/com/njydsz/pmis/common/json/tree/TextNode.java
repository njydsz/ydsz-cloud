package com.njydsz.pmis.common.json.tree;

/**
 * JSON 文本节点
 */
public final class TextNode extends JsonNode {

    private static final TextNode EMPTY = new TextNode("");

    private final String value;

    public TextNode(String value) {
        this.value = value != null ? value : "";
    }

    public static TextNode of(String value) {
        return value != null && !value.isEmpty() ? new TextNode(value) : EMPTY;
    }

    @Override
    public boolean isTextual() {
        return true;
    }

    @Override
    public String asText() {
        return value;
    }

    @Override
    public String asText(String defaultValue) {
        return value.isEmpty() ? defaultValue : value;
    }

    @Override
    public Object asValue() {
        return value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextNode)) {
            return false;
        }
        return value.equals(((TextNode) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
