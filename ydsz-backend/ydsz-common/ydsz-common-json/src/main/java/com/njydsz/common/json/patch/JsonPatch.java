package com.njydsz.common.json.patch;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.parser.JsonParserUtil;

import java.util.*;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Patch 实现（RFC 6902）。
 *
 * <p>支持以下操作：
 * <ul>
 *   <li>{@code add} — 添加值到指定路径</li>
 *   <li>{@code remove} — 移除指定路径的值</li>
 *   <li>{@code replace} — 替换指定路径的值</li>
 *   <li>{@code move} — 将值从一个路径移动到另一个路径</li>
 *   <li>{@code copy} — 将值从一个路径复制到另一个路径</li>
 *   <li>{@code test} — 测试指定路径的值是否匹配</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * String patch = "[{"op":"replace","path":"/name","value":"Alice"}]";
 * String json = "{"name":"Bob","age":30}";
 * String result = JsonPatch.apply(patch, json);
 * // result: {"name":"Alice","age":30}
 * </pre>
 *
 * <p>所有 JSON 对象/数组都通过 {@link JsonParserUtil} 解析为
 * {@code LinkedHashMap<String, Object>} / {@code ArrayList<Object>}，
 * 因此路径遍历中的强制类型转换在运行时是安全的。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("JSON Patch (RFC 6902) 属于独立工具域，非核心序列化能力")
@Deprecated(since = "1.0.0", forRemoval = true)
public final class JsonPatch {

    private JsonPatch() {
        throw new UnsupportedOperationException("JsonPatch is a utility class");
    }

    /**
     * 应用 JSON Patch 到目标 JSON。
     *
     * @param patchJson Patch 操作数组（JSON 字符串）
     * @param targetJson 目标 JSON 字符串
     * @return 应用 Patch 后的 JSON 字符串
     */
    public static String apply(String patchJson, String targetJson) {
        List<Object> operations = JsonParserUtil.parseArray(patchJson);
        Map<String, Object> target = JsonParserUtil.parseObject(targetJson);

        for (Object opObj : operations) {
            if (!(opObj instanceof Map)) {
                throw new IllegalArgumentException("Each patch operation must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rawOp = (Map<String, Object>) opObj;

            Object opField = rawOp.get("op");
            String opType = opField instanceof String ? (String) opField : null;
            Object pathField = rawOp.get("path");
            String path = pathField instanceof String ? (String) pathField : null;
            Object value = rawOp.get("value");
            Object fromField = rawOp.get("from");
            String from = fromField instanceof String ? (String) fromField : null;

            if (opType == null || path == null) {
                throw new IllegalArgumentException("Patch operation must have 'op' and 'path' as strings");
            }

            switch (opType) {
                case "add":
                    setByPath(target, path, value);
                    break;
                case "remove":
                    removeByPath(target, path);
                    break;
                case "replace":
                    setByPath(target, path, value);
                    break;
                case "move":
                    Object movedValue = getByPath(target, from);
                    removeByPath(target, from);
                    setByPath(target, path, movedValue);
                    break;
                case "copy":
                    Object copiedValue = getByPath(target, from);
                    setByPath(target, path, copiedValue);
                    break;
                case "test":
                    Object currentValue = getByPath(target, path);
                    if (!Objects.equals(currentValue, value)) {
                        throw new IllegalStateException(
                            "Test failed at path '" + path + "': expected "
                                + YdszJson.toJson(value) + " but got "
                                + YdszJson.toJson(currentValue));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown patch operation: " + opType);
            }
        }

        return YdszJson.toJson(target);
    }

    /**
     * 根据 JSON Pointer 路径获取值。
     *
     * <p>由于经过 {@link JsonParserUtil} 解析后，所有 Map 节点的实际类型为
     * {@code LinkedHashMap<String, Object>}、List 节点为 {@code ArrayList<Object>}，
     * 故此处强制类型转换为运行时安全的。</p>
     */
    private static Object getByPath(Map<String, Object> target, String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return target;
        }
        String[] parts = path.split("/");
        Object current = target;
        for (int i = 1; i < parts.length; i++) {
            String part = unescapeToken(parts[i]);
            if (current instanceof Map<?, ?>) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List<?> list) {
                int idx = Integer.parseInt(part);
                current = list.get(idx);
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 根据 JSON Pointer 路径设置值。
     */
    private static void setByPath(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("/");
        Object current = target;
        for (int i = 1; i < parts.length - 1; i++) {
            String part = unescapeToken(parts[i]);
            if (current instanceof Map<?, ?>) {
                Object next = ((Map<?, ?>) current).get(part);
                if (next == null) {
                    next = new LinkedHashMap<String, Object>();
                    ((Map<String, Object>) current).put(part, next);
                }
                current = next;
            } else if (current instanceof List<?> list) {
                int idx = Integer.parseInt(part);
                current = list.get(idx);
            }
        }
        String lastPart = unescapeToken(parts[parts.length - 1]);
        if (current instanceof Map<?, ?>) {
            ((Map<String, Object>) current).put(lastPart, value);
        } else if (current instanceof List<?>) {
            int idx = Integer.parseInt(lastPart);
            ((List<Object>) current).add(idx, value);
        }
    }

    /**
     * 根据 JSON Pointer 路径移除值。
     */
    private static void removeByPath(Map<String, Object> target, String path) {
        String[] parts = path.split("/");
        Object current = target;
        for (int i = 1; i < parts.length - 1; i++) {
            String part = unescapeToken(parts[i]);
            if (current instanceof Map<?, ?>) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List<?> list) {
                current = list.get(Integer.parseInt(part));
            }
        }
        String lastPart = unescapeToken(parts[parts.length - 1]);
        if (current instanceof Map<?, ?>) {
            ((Map<String, Object>) current).remove(lastPart);
        } else if (current instanceof List<?> list) {
            list.remove(Integer.parseInt(lastPart));
        }
    }

    /**
     * 反转 JSON Pointer 转义（~1 → /，~0 → ~）。
     */
    private static String unescapeToken(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * 创建一个 JSON Patch 操作列表。
     *
     * @return Patch 构建器
 * @author ydsz-team
 * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * JSON Patch 构建器。
     */
    public static final class Builder {
        private final List<Object> operations = new ArrayList<>();

        /**
         * 追加一个 {@code add} 操作（RFC 6902）。
         *
         * <p>在 {@code path} 处写入 {@code value}；若父路径不存在则自动创建中间节点。</p>
         *
         * @param path 目标 JSON Pointer 路径
         * @param value 要写入的值
         */
        public Builder add(String path, Object value) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "add");
            op.put("path", path);
            op.put("value", value);
            operations.add(op);
            return this;
        }

        /**
         * 追加一个 {@code remove} 操作（RFC 6902）。
         *
         * @param path 要删除的 JSON Pointer 路径
         */
        public Builder remove(String path) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "remove");
            op.put("path", path);
            operations.add(op);
            return this;
        }

        /**
         * 追加一个 {@code replace} 操作（RFC 6902）。
         *
         * @param path 目标 JSON Pointer 路径
         * @param value 替换后的值
         */
        public Builder replace(String path, Object value) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "replace");
            op.put("path", path);
            op.put("value", value);
            operations.add(op);
            return this;
        }

        /**
         * 追加一个 {@code move} 操作（RFC 6902）。
         *
         * <p>先读取 {@code from} 处的值并删除源节点，再写入 {@code path}。</p>
         *
         * @param from 源 JSON Pointer 路径
         * @param path 目标 JSON Pointer 路径
         */
        public Builder move(String from, String path) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "move");
            op.put("from", from);
            op.put("path", path);
            operations.add(op);
            return this;
        }

        /**
         * 追加一个 {@code copy} 操作（RFC 6902）。
         *
         * <p>将 {@code from} 处的值复制到 {@code path}，源节点保留。</p>
         *
         * @param from 源 JSON Pointer 路径
         * @param path 目标 JSON Pointer 路径
         */
        public Builder copy(String from, String path) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "copy");
            op.put("from", from);
            op.put("path", path);
            operations.add(op);
            return this;
        }

        /**
         * 追加一个 {@code test} 操作（RFC 6902）。
         *
         * <p>断言 {@code path} 处当前值等于 {@code value}；不等时，后续 {@link #applyTo} 将抛出
         * {@link IllegalStateException}。</p>
         *
         * @param path 目标 JSON Pointer 路径
         * @param value 期望的值
         */
        public Builder test(String path, Object value) {
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("op", "test");
            op.put("path", path);
            op.put("value", value);
            operations.add(op);
            return this;
        }

        /**
         * 构建并应用 Patch。
         *
         * @param targetJson 目标 JSON 字符串
         * @return 应用后的 JSON 字符串
         */
        public String applyTo(String targetJson) {
            String patchJson = YdszJson.toJson(operations);
            return JsonPatch.apply(patchJson, targetJson);
        }
    }
}
