package com.njydsz.common.json;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.MissingNode;
import com.njydsz.common.json.tree.NullNode;
import com.njydsz.common.json.tree.ObjectNode;

/**
 * JSON 高频操作工具类（静态入口）
 *
 * <p>收敛全仓库散落的 JSON 处理模式，提供安全解析、类型安全字段提取、对象合并、
 * JSON Pointer 路径访问等高频操作。所有方法均为静态方法、无状态、线程安全。
 *
 * <p><b>设计定位：</b></p>
 * <ul>
 *   <li>对标 FastJSON2 {@code JSON} 工具类的便捷性</li>
 *   <li>对标 Jackson {@code ObjectMapper} 的树模型操作</li>
 *   <li>对标 Gson {@code JsonParser} 的安全访问语义</li>
 *   <li>所有 JSON 操作统一委托给 {@link YdszJson} 引擎，确保单一代码路径</li>
 * </ul>
 *
 * <p><b>覆盖的高频模式：</b></p>
 * <pre>{@code
 * // 1. 安全解析（异常时返回 empty 而非抛异常）
 * Optional<ObjectNode> opt = JsonUtils.tryParseObject(maybeNull);
 * opt.ifPresent(node -> { ... });
 *
 * // 2. 类型安全字段提取（避免重复判 null / NumberFormatException）
 * String name = JsonUtils.getString(node, "name");
 * Long id = JsonUtils.getLong(node, "id");
 * boolean active = JsonUtils.getBoolean(node, "active", false);
 * JsonUtils.ifPresent(node, "ext", ObjectNode.class, ext -> { ... });
 *
 * // 3. 对象深度合并（base 优先，override 补充）
 * ObjectNode merged = JsonUtils.deepMerge(base, override);
 *
 * // 4. JSON Pointer 路径访问（RFC 6901）
 * String city = JsonUtils.getByPath(json, "address/city");
 *
 * // 5. 扁平化嵌套对象（内联展开为单层 key=value）
 * Map<String, Object> flat = JsonUtils.flatten(node, ".");
 *
 * // 6. 空节点工厂
 * ObjectNode obj = JsonUtils.newObject();
 * ArrayNode arr = JsonUtils.newArray();
 * }</pre>
 *
 * <p><b>规范要求（R10）：</b>禁止使用如下反模式：
 * <ul>
 *   <li>禁止在业务代码中 while/for 循环遍历 Map 提取 JSON 字段（应使用 {@code getString/getLong/...}）</li>
 *   <li>禁止在业务代码中手动 null 检查 + 类型转换（应使用 {@code getBoolean(node, key, defaultValue)}）</li>
 *   <li>禁止在分散位置重复实现 JSON 合并逻辑（应使用 {@link #deepMerge}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public final class JsonUtils {

    private JsonUtils() {
        throw new UnsupportedOperationException("JsonUtils is a utility class and cannot be instantiated");
    }

    // ==================== 安全解析 ====================

    /**
     * 安全解析 JSON 字符串为 ObjectNode。
     *
     * <p>解析失败（null/blank/非合法 JSON/非对象类型）时返回 {@link Optional#empty()}，
     * 不抛出异常。适用于 MQ 消息体、HTTP 请求体、配置值等可能为 null 或非法 JSON 的场景。
     *
     * @param json 待解析的 JSON 字符串
     * @return 成功解析返回包含 ObjectNode 的 Optional，失败返回 empty
     * @see YdszJson#parseObject(String)
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> tryParseObject(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode tree = YdszJson.readTree(json);
            if (tree instanceof ObjectNode objNode) {
                return Optional.of((T) objNode);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 安全解析 JSON 字符串为 ArrayNode。
     *
     * <p>解析失败（null/blank/非合法 JSON/非数组类型）时返回 {@link Optional#empty()}。
     *
     * @param json 待解析的 JSON 字符串
     * @return 成功解析返回包含 ArrayNode 的 Optional，失败返回 empty
     * @see YdszJson#parseArrayNode(String)
     * @since 1.2.0
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> tryParseArray(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode tree = YdszJson.readTree(json);
            if (tree instanceof ArrayNode arrNode) {
                return Optional.of((T) arrNode);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ==================== 类型安全字段提取 ====================

    /**
     * 从 ObjectNode 中获取字符串字段值。
     *
     * <p>字段不存在、为 null、或值为 MissingNode 时返回 null。
     * 非字符串值自动调用 {@code asText()} 转换。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return 字符串值，字段不存在或 null 返回 null
     * @since 1.2.0
     */
    public static String getString(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissing()) {
            return null;
        }
        return child.asText();
    }

    /**
     * 从 ObjectNode 中获取字符串字段值（带默认值）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param defaultValue 字段不存在或 null 时的默认值
     * @return 字符串值，字段不存在或 null 返回 defaultValue
     * @since 1.2.0
     */
    public static String getString(ObjectNode node, String field, String defaultValue) {
        String value = getString(node, field);
        return value != null ? value : defaultValue;
    }

    /**
     * 从 ObjectNode 中获取 Integer 字段值。
     *
     * <p>字段不存在、为 null、或值为 MissingNode 时返回 null。
     * 字符串值自动尝试 Integer 转换（转换失败返回 null）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return Integer 值，字段不存在或 null 返回 null
     * @since 1.2.0
     */
    public static Integer getInteger(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        return node.getInteger(field);
    }

    /**
     * 从 ObjectNode 中获取 int 字段值（带默认值）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param defaultValue 字段不存在或 null 时的默认值
     * @return int 值
     * @since 1.2.0
     */
    public static int getInt(ObjectNode node, String field, int defaultValue) {
        Integer value = getInteger(node, field);
        return value != null ? value : defaultValue;
    }

    /**
     * 从 ObjectNode 中获取 Long 字段值。
     *
     * <p>字段不存在、为 null、或值为 MissingNode 时返回 null。
     * 字符串值自动尝试 Long 转换（转换失败返回 null）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return Long 值，字段不存在或 null 返回 null
     * @since 1.2.0
     */
    public static Long getLong(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        return node.getLong(field);
    }

    /**
     * 从 ObjectNode 中获取 long 字段值（带默认值）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param defaultValue 字段不存在或 null 时的默认值
     * @return long 值
     * @since 1.2.0
     */
    public static long getLong(ObjectNode node, String field, long defaultValue) {
        Long value = getLong(node, field);
        return value != null ? value : defaultValue;
    }

    /**
     * 从 ObjectNode 中获取 Double 字段值。
     *
     * <p>字段不存在、为 null、或值为 MissingNode 时返回 null。
     * 字符串值自动尝试 Double 转换（转换失败返回 null）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return Double 值，字段不存在或 null 返回 null
     * @since 1.2.0
     */
    public static Double getDouble(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        return node.getDouble(field);
    }

    /**
     * 从 ObjectNode 中获取 double 字段值（带默认值）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param defaultValue 字段不存在或 null 时的默认值
     * @return double 值
     * @since 1.2.0
     */
    public static double getDouble(ObjectNode node, String field, double defaultValue) {
        Double value = getDouble(node, field);
        return value != null ? value : defaultValue;
    }

    /**
     * 从 ObjectNode 中获取 Boolean 字段值。
     *
     * <p>字段不存在、为 null、或值为 MissingNode 时返回 null。
     * 字符串值自动尝试 Boolean 转换。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return Boolean 值，字段不存在或 null 返回 null
     * @since 1.2.0
     */
    public static Boolean getBoolean(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.isMissing()) {
            return null;
        }
        if (child.isBoolean()) {
            return child.asBoolean();
        }
        return Boolean.parseBoolean(child.asText());
    }

    /**
     * 从 ObjectNode 中获取 boolean 字段值（带默认值）。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param defaultValue 字段不存在或 null 时的默认值
     * @return boolean 值
     * @since 1.2.0
     */
    public static boolean getBoolean(ObjectNode node, String field, boolean defaultValue) {
        Boolean value = getBoolean(node, field);
        return value != null ? value : defaultValue;
    }

    /**
     * 从 ObjectNode 中获取嵌套 ObjectNode 字段。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return 嵌套 ObjectNode，不存在或非对象类型返回 null
     * @since 1.2.0
     */
    public static ObjectNode getObjectNode(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child instanceof ObjectNode objNode) {
            return objNode;
        }
        return null;
    }

    /**
     * 从 ObjectNode 中获取嵌套 ArrayNode 字段。
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @return 嵌套 ArrayNode，不存在或非数组类型返回 null
     * @since 1.2.0
     */
    public static ArrayNode getArrayNode(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child instanceof ArrayNode arrNode) {
            return arrNode;
        }
        return null;
    }

    /**
     * 当字段存在且类型匹配时执行消费函数。
     *
     * <p>避免业务代码中的重复判空 + instanceof 检查。
     * <pre>{@code
     * JsonUtils.ifPresent(node, "metadata", ObjectNode.class, meta -> {
     *     processMetadata(meta);
     * });
     * }</pre>
     *
     * @param node ObjectNode 实例
     * @param field 字段名
     * @param type 期望类型
     * @param consumer 字段匹配时的消费函数
     * @param <T> 字段类型参数
     * @since 1.2.0
     */
    public static <T extends JsonNode> void ifPresent(ObjectNode node, String field,
                                                        Class<T> type,
                                                        java.util.function.Consumer<T> consumer) {
        if (node == null || consumer == null) {
            return;
        }
        JsonNode child = node.get(field);
        if (type.isInstance(child)) {
            consumer.accept(type.cast(child));
        }
    }

    // ==================== 对象合并 ====================

    /**
     * 深度合并两个 ObjectNode。
     *
     * <p>合并规则：
     * <ul>
     *   <li>{@code override} 中的字段值覆盖 {@code base} 中同名字段值</li>
     *   <li>双方均为嵌套 ObjectNode 时递归合并；否则 {@code override} 值直接覆盖</li>
     *   <li>含 {@code removeNulls=true} 时跳过 override 中的 null 字段以保留 base 值</li>
     * </ul>
     *
     * <p>不修改原对象，返回新的 ObjectNode 实例。
     *
     * @param base 基准对象（优先级低于 override）
     * @param override 覆盖对象（优先级高于 base）
     * @return 合并后的新 ObjectNode，两参数均为 null 返回空 ObjectNode
     * @since 1.2.0
     */
    public static ObjectNode deepMerge(ObjectNode base, ObjectNode override) {
        return deepMerge(base, override, false);
    }

    /**
     * 深度合并两个 ObjectNode（带 null 移除选项）。
     *
     * @param base 基准对象
     * @param override 覆盖对象
     * @param removeNulls 为 true 时跳过 override 中的 null 字段保留 base 原值
     * @return 合并后的新 ObjectNode
     * @since 1.2.0
     */
    public static ObjectNode deepMerge(ObjectNode base, ObjectNode override, boolean removeNulls) {
        ObjectNode result = new ObjectNode();
        // 先放入 base 全部字段
        if (base != null) {
            base.fieldNames().forEachRemaining(fieldName ->
                    result.put(fieldName, base.get(fieldName).deepCopy()));
        }
        // 再用 override 覆盖或补充
        if (override != null) {
            override.fieldNames().forEachRemaining(fieldName -> {
                JsonNode overrideValue = override.get(fieldName);
                if (removeNulls && (overrideValue == null || overrideValue.isNull())) {
                    return;
                }
                JsonNode baseValue = result.get(fieldName);
                if (baseValue.isObject() && overrideValue.isObject()) {
                    result.put(fieldName, deepMerge(
                            (ObjectNode) baseValue, (ObjectNode) overrideValue, removeNulls));
                } else {
                    result.put(fieldName, overrideValue.deepCopy());
                }
            });
        }
        return result;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建新的空 ObjectNode。
     *
     * <p>替代 {@code new ObjectNode()} 的直接调用，统一工厂入口。
     *
     * @return 新的空 ObjectNode 实例
     * @since 1.2.0
     */
    public static ObjectNode newObject() {
        return new ObjectNode();
    }

    /**
     * 创建新的空 ArrayNode。
     *
     * <p>替代 {@code new ArrayNode()} 的直接调用，统一工厂入口。
     *
     * @return 新的空 ArrayNode 实例
     * @since 1.2.0
     */
    public static ArrayNode newArray() {
        return new ArrayNode();
    }

    /**
     * 创建 ObjectNode 工厂——使用 Builder 风格构建。
     *
     * <p>示例：
     * <pre>{@code
     * ObjectNode node = JsonUtils.objectBuilder()
     *     .put("name", "John")
     *     .put("age", 30)
     *     .put("active", true)
     *     .build();
     * }</pre>
     *
     * @return ObjectNodeBuilder 实例
     * @since 1.2.0
     */
    public static ObjectNodeBuilder objectBuilder() {
        return new ObjectNodeBuilder();
    }

    // ==================== JSON Pointer 路径访问 ====================

    /**
     * 按 JSON Pointer（RFC 6901，"/" 分隔）提取嵌套字段值。
     *
     * <p>对标 FastJSON2 {@code JSONPath.extract(json, path)}。
     * 路径中索引用数字表示（如 "items/0/name"），对象字段按 key。
     *
     * <p>示例：
     * <pre>{@code
     * String city = JsonUtils.getByPath(userJson, "address/city");
     * String firstItem = JsonUtils.getByPath(orderJson, "items/0/name");
     * }</pre>
     *
     * @param node 根节点（ObjectNode 或 ArrayNode）
     * @param path "/" 分隔的路径表达式（不含前导 "/"）
     * @return 字段值文本，路径不存在返回 null
     * @since 1.2.0
     */
    public static String getByPath(JsonNode node, String path) {
        if (node == null || path == null || path.isBlank()) {
            return null;
        }
        String[] paths = path.split("/");
        JsonNode current = node;
        for (String segment : paths) {
            if (segment.isEmpty()) {
                continue;
            }
            if (current == null || current.isMissing() || current.isNull()) {
                return null;
            }
            if (current.isObject()) {
                current = ((ObjectNode) current).get(segment);
            } else if (current.isArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    ArrayNode arr = (ArrayNode) current;
                    current = (index >= 0 && index < arr.size()) ? arr.get(index)
                            : MissingNode.getInstance();
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return (current != null && !current.isMissing() && !current.isNull())
                ? current.asText() : null;
    }

    /**
     * 按 JSON Pointer 从 JSON 字符串提取嵌套字段。
     *
     * @param json JSON 对象字符串
     * @param path "/" 分隔的路径
     * @return 字段值文本，解析失败或路径不存在返回 null
     * @since 1.2.0
     */
    public static String getByPath(String json, String path) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode tree = YdszJson.readTree(json);
            return getByPath(tree, path);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 扁平化 ====================

    /**
     * 将嵌套 ObjectNode 扁平化为单层 Map。
     *
     * <p>内部嵌套对象使用 separator 连接的 key 表示（如 "address.city" = "Beijing"）。
     * 数组不展开，仅调用元素的 toString。
     *
     * <p>适用场景：日志字段输出、动态表单字段映射、ES 索引映射等。
     *
     * @param node 要扁平化的 ObjectNode（不为 null）
     * @param separator 层级分隔符（通常为 "." 或 "_"）
     * @return 扁平化 Map
     * @since 1.2.0
     */
    public static Map<String, Object> flatten(ObjectNode node, String separator) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node == null) {
            return result;
        }
        flattenRecursive(node, "", separator, result);
        return result;
    }

    private static void flattenRecursive(JsonNode node, String prefix,
                                         String separator, Map<String, Object> result) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            Iterator<String> fieldNames = objNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = objNode.get(fieldName);
                String childKey = prefix.isEmpty() ? fieldName : prefix + separator + fieldName;
                if (child != null && child.isObject()) {
                    flattenRecursive(child, childKey, separator, result);
                } else if (child != null) {
                    result.put(childKey, child.asValue());
                }
            }
        } else {
            result.put(prefix.isEmpty() ? "_value" : prefix, node.asValue());
        }
    }

    // ==================== 选择性序列化辅助 ====================

    /**
     * 序列化对象并仅保留指定字段（白名单过滤）。
     *
     * <p>对标 Jackson 的 SimpleBeanPropertyFilter 实现"导出处方"功能，
     * 替代业务代码中频繁的 {@code Map.put} 逐字段组装逻辑。
     *
     * <p>示例：
     * <pre>{@code
     * // 仅导出 name 和 age 字段
     * String json = JsonUtils.toJsonWithFields(user, Set.of("name", "age"));
     * }</pre>
     *
     * @param obj 要序列化的对象
     * @param visibleFields 要保留的字段名集合（其余字段排除）
     * @return JSON 字符串
     * @since 1.2.0
     */
    public static String toJsonWithFields(Object obj, Set<String> visibleFields) {
        if (obj == null) {
            return "null";
        }
        if (visibleFields == null || visibleFields.isEmpty()) {
            return YdszJson.toJson(obj);
        }
        JsonMapper mapper = JsonMapper.builder().writeNulls(false).build();
        return mapper.toJsonExcludeFields(obj, invertFields(obj, visibleFields));
    }

    /**
     * 序列化对象并排除指定字段（黑名单过滤）。
     *
     * <p>适用于列权限过滤、敏感字段排除等场景。
     * 替代 {@link AuthColPermissionAspect} 等新写 JsonMapper + toJsonExcludeFields 的重复代码。
     *
     * <p>示例：
     * <pre>{@code
     * // 排除 password 和 salt 字段
     * String json = JsonUtils.toJsonWithoutFields(user, Set.of("password", "salt"));
     * }</pre>
     *
     * @param obj 要序列化的对象
     * @param excludedFields 要排除的字段名集合
     * @return JSON 字符串
     * @since 1.2.0
     */
    public static String toJsonWithoutFields(Object obj, Set<String> excludedFields) {
        if (obj == null) {
            return "null";
        }
        if (excludedFields == null || excludedFields.isEmpty()) {
            return YdszJson.toJson(obj);
        }
        JsonMapper mapper = JsonMapper.builder().writeNulls(false).build();
        return mapper.toJsonExcludeFields(obj, excludedFields);
    }

    /**
     * 计算"字段全集 - 保留字段"的补集，用于将白名单转为黑名单调用 JsonMapper。
     * <p>注意：这里启发式地返回全集补集；业务场景通常字段有限，
     * 若无法获得全集则回退到仅输出保留字段（通过树遍历提取）。</p>
     */
    private static Set<String> invertFields(Object obj, Set<String> visibleFields) {
        // 启发式：先序列化为 ObjectNode，取其 keySet 计算补集
        try {
            JsonNode tree = YdszJson.valueToTree(obj);
            if (tree instanceof ObjectNode objNode) {
                Set<String> allFields = new java.util.HashSet<>();
                objNode.fieldNames().forEachRemaining(allFields::add);
                allFields.removeAll(visibleFields);
                return allFields;
            }
        } catch (Exception ignored) {
            // 忽略异常，返回空集合（此时 toJsonExcludeFields 不排除任何字段）
        }
        return java.util.Collections.emptySet();
    }

    // ==================== 便捷 Map 构建 ====================

    /**
     * 将 Map<String, ?> 转换为 ObjectNode。
     *
     * <p>适用于需要将配置 Map、动态字段 Map 等转换为树模型的场景。
     *
     * @param map 源 Map
     * @return ObjectNode，map 为 null 返回空 ObjectNode
     * @since 1.2.0
     */
    public static ObjectNode toObjectNode(Map<String, ?> map) {
        ObjectNode result = new ObjectNode();
        if (map == null) {
            return result;
        }
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value != null ? YdszJson.valueToTree(value) : NullNode.getInstance());
        }
        return result;
    }

    // ==================== 委托快捷方法 ====================

    /**
     * 序列化对象为 JSON 字符串（快捷委托）。
     *
     * <p>与 {@link YdszJson#toJson(Object)} 语义一致，供已导入 JsonUtils 的场景减少全限定名。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @since 1.2.0
     */
    public static String toJson(Object obj) {
        return YdszJson.toJson(obj);
    }

    /**
     * 反序列化 JSON 字符串为指定类型（快捷委托）。
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 反序列化后的对象
     * @since 1.2.0
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return YdszJson.fromJson(json, clazz);
    }

    // ==================== ObjectNodeBuilder 内部类 ====================

    /**
     * ObjectNode 链式构建器。
     *
     * <p>扩展标准 {@link ObjectNode} 的 put 方法，支持更多链式操作场景。
     * 通过 {@link JsonUtils#objectBuilder()} 创建。
     *
     * @since 1.2.0
     */
    public static final class ObjectNodeBuilder {
        private final ObjectNode node = new ObjectNode();

        private ObjectNodeBuilder() {
        }

        /**
         * 添加字符串字段。
         */
        public ObjectNodeBuilder put(String name, String value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加整数字段。
         */
        public ObjectNodeBuilder put(String name, int value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加长整数字段。
         */
        public ObjectNodeBuilder put(String name, long value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加双精度浮点数字段。
         */
        public ObjectNodeBuilder put(String name, double value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加布尔字段。
         */
        public ObjectNodeBuilder put(String name, boolean value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加 JSON 节点字段。
         */
        public ObjectNodeBuilder put(String name, JsonNode value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加 ObjectNode 字段。
         */
        public ObjectNodeBuilder put(String name, ObjectNode value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加 ArrayNode 字段。
         */
        public ObjectNodeBuilder put(String name, ArrayNode value) {
            node.put(name, value);
            return this;
        }

        /**
         * 添加枚举字段（调用 name() 序列化）。
         */
        public ObjectNodeBuilder put(String name, Enum<?> value) {
            node.put(name, value != null ? value.name() : null);
            return this;
        }

        /**
         * 仅当 value 非 null 时添加字段。
         */
        public ObjectNodeBuilder putIfPresent(String name, Object value) {
            if (value != null) {
                node.put(name, YdszJson.valueToTree(value));
            }
            return this;
        }

        /**
         * 仅当 condition 为 true 时添加字段。
         */
        public ObjectNodeBuilder putIf(String name, Object value, boolean condition) {
            if (condition) {
                node.put(name, YdszJson.valueToTree(value));
            }
            return this;
        }

        /**
         * 构建最终的 ObjectNode。
         *
         * @return 构建完成的 ObjectNode 实例
         */
        public ObjectNode build() {
            return node;
        }
    }
}
