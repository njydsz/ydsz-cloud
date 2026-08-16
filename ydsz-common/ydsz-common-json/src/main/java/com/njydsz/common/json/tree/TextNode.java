package com.njydsz.common.json.tree;

/**
 * JSON 文本节点
 *
 * <p>对标 Jackson TextNode，表示一个 JSON 字符串值。
 * 内部持有 String 引用，提供类型转换方法。</p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *   <li>不可变对象，线程安全</li>
 *   <li>空字符串使用 EMPTY 单例，减少对象创建</li>
 *   <li>自动处理 null 值（转换为空字符串）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * TextNode node = new TextNode("hello");
 * String value = node.asText(); // "hello"
 *
 * // 使用工厂方法
 * TextNode empty = TextNode.of(null); // 返回 EMPTY 单例
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TextNode extends JsonNode {

    private static final TextNode EMPTY = new TextNode("");

    private final String value;

    /**
     * 创建文本节点
     *
     * @param value 文本值，null 会被转换为空字符串
     */
    public TextNode(String value) {
        this.value = value != null ? value : "";
    }

    /**
     * 工厂方法：创建文本节点
     *
     * <p>空字符串或 null 会返回预定义的 EMPTY 单例，减少对象创建。</p>
     *
     * @param value 文本值
     * @return 文本节点实例
     */
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
                case '
': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < ' ') {
                        sb.append("\\u");
                        sb.append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
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
