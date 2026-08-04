package com.njydsz.common.json.schema;

import java.util.*;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Schema 定义（参考 JSON Schema 规范）
 *
 * <p>用于定义 JSON 数据的结构和约束条件。</p>
 *
 * <p><b>支持的类型：</b></p>
 * <ul>
 *   <li>string - 字符串</li>
 *   <li>number - 数字</li>
 *   <li>integer - 整数</li>
 *   <li>boolean - 布尔</li>
 *   <li>array - 数组</li>
 *   <li>object - 对象</li>
 *   <li>null - 空值</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * JsonSchema schema = new JsonSchema("object");
 * schema.addProperty("name", JsonSchema.string());
 * schema.addRequired("name");
 *
 * ValidationResult result = schema.validate(json);
 * if (!result.isValid()) {
 *     System.err.println(result.getErrors());
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("JSON Schema 定义属于非核心 RFC 扩展，后续可能独立为单独模块")
public final class JsonSchema {

    /** Schema 类型 */
    private final String type;

    /** 描述 */
    private String description;

    /** 是否必填 */
    private boolean required = false;

    /** 枚举值 */
    private List<Object> enumValues;

    /** 默认值 */
    private Object defaultValue;

    /** 数组 uniqueItems 约束 */
    private Boolean uniqueItems;

    /** 对象最少属性数 */
    private Integer minProperties;

    /** 对象最多属性数 */
    private Integer maxProperties;

    /** 字符串约束 */
    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    /** 数字约束 */
    private Double minimum;
    private Double maximum;
    private Double exclusiveMinimum;
    private Double exclusiveMaximum;
    private Double multipleOf;

    /** 数组约束 */
    private JsonSchema items;
    private Integer minItems;
    private Integer maxItems;

    /** 对象约束 */
    private Map<String, JsonSchema> properties;
    private List<String> requiredProperties;
    /** 额外属性约束（JsonSchema 或 Boolean.FALSE，RFC 规范支持 boolean 重载） */
    private Object additionalProperties;

    /** 正则模式属性匹配（JSON Schema Draft 07 patternProperties） */
    private Map<String, JsonSchema> patternProperties;

    /** 组合关键字（JSON Schema Draft 07 allOf/anyOf/oneOf） */
    private List<JsonSchema> allOf;
    private List<JsonSchema> anyOf;
    private List<JsonSchema> oneOf;

    /** not 关键字：数据不能匹配此 Schema */
    private JsonSchema not;

    /** const 关键字：数据必须等于此固定值 */
    private Object constValue;

    /** if/then/else 条件关键字（JSON Schema Draft 07） */
    private JsonSchema ifSchema;
    private JsonSchema thenSchema;
    private JsonSchema elseSchema;

    /** $ref 引用：指向 definitions 中的另一个 Schema */
    private String ref;

    /** definitions 定义：可复用的命名 Schema 集合 */
    private Map<String, JsonSchema> definitions;

    /**
     * 构造函数
     */
    public JsonSchema(String type) {
        this.type = type;
        this.properties = new LinkedHashMap<>();
        this.requiredProperties = new ArrayList<>();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 字符串类型
     */
    public static JsonSchema string() {
        return new JsonSchema("string");
    }

    /**
     * 数字类型
     */
    public static JsonSchema number() {
        return new JsonSchema("number");
    }

    /**
     * 整数类型
     */
    public static JsonSchema integer() {
        return new JsonSchema("integer");
    }

    /**
     * 布尔类型
     */
    public static JsonSchema booleanType() {
        return new JsonSchema("boolean");
    }

    /**
     * 数组类型
     */
    public static JsonSchema array() {
        return new JsonSchema("array");
    }

    /**
     * 对象类型
     */
    public static JsonSchema object() {
        return new JsonSchema("object");
    }

    // ========== 链式配置方法 ==========

    /**
     * 设置描述
     */
    public JsonSchema description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 设置为必填
     */
    public JsonSchema required() {
        this.required = true;
        return this;
    }

    /**
     * 设置枚举值
     */
    public JsonSchema enumValues(Object... values) {
        this.enumValues = new ArrayList<>(Arrays.asList(values));
        return this;
    }

    /**
     * 添加单个枚举值
     */
    public JsonSchema addEnum(Object value) {
        if (this.enumValues == null) {
            this.enumValues = new ArrayList<>();
        }
        this.enumValues.add(value);
        return this;
    }

    /**
     * 设置默认值
     */
    public JsonSchema defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 设置字符串最小长度
     */
    public JsonSchema minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    /**
     * 设置字符串最大长度
     */
    public JsonSchema maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /**
     * 设置字符串正则表达式
     */
    public JsonSchema pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * 设置数字最小值
     */
    public JsonSchema minimum(double minimum) {
        this.minimum = minimum;
        return this;
    }

    /**
     * 设置数字最大值
     */
    public JsonSchema maximum(double maximum) {
        this.maximum = maximum;
        return this;
    }

    /**
     * 添加属性（对象类型）
     */
    public JsonSchema addProperty(String name, JsonSchema schema) {
        this.properties.put(name, schema);
        return this;
    }

    /**
     * 添加必填属性
     */
    public JsonSchema addRequired(String name) {
        this.requiredProperties.add(name);
        return this;
    }

    /**
     * 设置数组项类型
     */
    public JsonSchema items(JsonSchema items) {
        this.items = items;
        return this;
    }

    /**
     * 设置数组项数最小值
     */
    public JsonSchema minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    /**
     * 设置数组项数最大值
     */
    public JsonSchema maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    /**
     * 设置倍数约束
     */
    public JsonSchema multipleOf(double multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    /**
     * 设置排除性最小值
     */
    public JsonSchema exclusiveMinimum(double exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    /**
     * 设置排除性最大值
     */
    public JsonSchema exclusiveMaximum(double exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    /**
     * 设置额外属性 Schema。
     */
    public JsonSchema additionalProperties(JsonSchema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    /**
     * 设置额外属性开关（RFC 规范支持 boolean 重载）。
     * {@code false} 表示禁止额外属性，{@code true} 或默认 null 表示允许。
     */
    public JsonSchema additionalProperties(boolean additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    // ========== 组合关键字（allOf / anyOf / oneOf） ==========

    /**
     * 添加 allOf 约束（所有 Schema 都必须验证通过）。
     *
     * @param schemas 需要全部满足的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema allOf(JsonSchema... schemas) {
        if (this.allOf == null) {
            this.allOf = new ArrayList<>();
        }
        this.allOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * 添加 anyOf 约束（至少一个 Schema 验证通过）。
     *
     * @param schemas 需要满足任一条件的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema anyOf(JsonSchema... schemas) {
        if (this.anyOf == null) {
            this.anyOf = new ArrayList<>();
        }
        this.anyOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * 添加 oneOf 约束（恰好一个 Schema 验证通过）。
     *
     * @param schemas 需要满足唯一条件的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema oneOf(JsonSchema... schemas) {
        if (this.oneOf == null) {
            this.oneOf = new ArrayList<>();
        }
        this.oneOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * Null 类型
     */
    public static JsonSchema nullType() {
        return new JsonSchema("null");
    }

    /**
     * 设置 not 约束（数据不能匹配此 Schema）。
     *
     * @param not 不允许匹配的 Schema
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema not(JsonSchema not) {
        this.not = not;
        return this;
    }

    /**
     * 设置 const 约束（数据必须等于此固定值）。
     *
     * @param constValue 固定值
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema constValue(Object constValue) {
        this.constValue = constValue;
        return this;
    }

    /**
     * 设置 if/then/else 条件约束。
     *
     * <p>如果数据匹配 if Schema，则必须匹配 then Schema；
     * 如果不匹配 if Schema，则必须匹配 else Schema。</p>
     *
     * @param ifSchema 条件 Schema
     * @param thenSchema 满足条件时必须匹配的 Schema
     * @param elseSchema 不满足条件时必须匹配的 Schema（可为 null）
     * @return 当前 Schema（链式调用）
     * @since 1.0.0
     */
    public JsonSchema ifThenElse(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) {
        this.ifSchema = ifSchema;
        this.thenSchema = thenSchema;
        this.elseSchema = elseSchema;
        return this;
    }

    /**
     * 添加多个必填属性
     */
    public JsonSchema addRequired(String... names) {
        for (String name : names) {
            this.requiredProperties.add(name);
        }
        return this;
    }

    /**
     * 设置数组元素是否必须唯一。
     *
     * @param unique {@code true} 表示数组元素不允许重复
     * @return 当前 JsonSchema 实例（链式调用）
     */
    public JsonSchema uniqueItems(boolean unique) { this.uniqueItems = unique; return this; }

    /**
     * 设置对象最少属性个数。
     *
     * @param n 最少属性数量，须为非负整数
     * @return 当前 JsonSchema 实例（链式调用）
     */
    public JsonSchema minProperties(int n) { this.minProperties = n; return this; }

    /**
     * 设置对象最多属性个数。
     *
     * @param n 最多属性数量，须为非负整数
     * @return 当前 JsonSchema 实例（链式调用）
     */
    public JsonSchema maxProperties(int n) { this.maxProperties = n; return this; }

    /**
     * 添加按正则表达式匹配的属性约束。
     *
     * <p>符合 JSON Schema {@code patternProperties} 语义：对象中所有键名
     * 匹配 {@code regex} 的属性都必须满足子 schema 约束。</p>
     *
     * @param regex  属性名正则表达式（如 {@code "^s_" } 匹配前缀为 s_ 的属性）
     * @param schema 匹配该正则的属性需满足的子 schema
     * @return 当前 JsonSchema 实例（链式调用）
     */
    public JsonSchema patternProperty(String regex, JsonSchema schema) {
        if (this.patternProperties == null) { this.patternProperties = new LinkedHashMap<>(); }
        this.patternProperties.put(regex, schema);
        return this;
    }

    // ========== Getter 方法 ==========

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    public Map<String, JsonSchema> getProperties() {
        return properties;
    }

    public List<String> getRequiredProperties() {
        return requiredProperties;
    }

    public JsonSchema getItems() {
        return items;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Boolean getUniqueItems() {
        return uniqueItems;
    }

    public Integer getMinProperties() {
        return minProperties;
    }

    public Integer getMaxProperties() {
        return maxProperties;
    }

    public Double getMultipleOf() {
        return multipleOf;
    }

    public Double getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Double getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public Object getAdditionalProperties() {
        return additionalProperties;
    }

    public Map<String, JsonSchema> getPatternProperties() {
        return patternProperties;
    }

    public List<JsonSchema> getAllOf() {
        return allOf;
    }

    public List<JsonSchema> getAnyOf() {
        return anyOf;
    }

    public List<JsonSchema> getOneOf() {
        return oneOf;
    }

    public JsonSchema getNot() {
        return not;
    }

    public Object getConstValue() {
        return constValue;
    }

    public JsonSchema getIfSchema() {
        return ifSchema;
    }

    public JsonSchema getThenSchema() {
        return thenSchema;
    }

    public JsonSchema getElseSchema() {
        return elseSchema;
    }

    // ---------- $ref / definitions ----------

    public JsonSchema ref(String ref) { this.ref = ref; return this; }
    public String getRef() { return ref; }

    /**
     * 添加一个可复用的子 schema 定义（对应 JSON Schema {@code definitions} 部分）。
     *
     * <p>定义的子 schema 可通过 {@code $ref: "#/definitions/{name}"} 被本 schema
     * 或同定义域内的其他 schema 引用，避免重复编写公共约束。</p>
     *
     * @param name   定义名称（$ref 引用时的标识）
     * @param schema 子 schema 定义
     * @return 当前 JsonSchema 实例（链式调用）
     */
    public JsonSchema addDefinition(String name, JsonSchema schema) {
        if (this.definitions == null) { this.definitions = new LinkedHashMap<>(); }
        this.definitions.put(name, schema);
        return this;
    }
    public Map<String, JsonSchema> getDefinitions() { return definitions; }

    /**
     * 解析 $ref 引用，返回引用的 Schema，本地未找到返回 null。
     */
    public JsonSchema resolveRef() {
        if (ref == null || !ref.startsWith("#/definitions/")) return null;
        if (definitions == null) return null;
        String name = ref.substring("#/definitions/".length());
        return definitions.get(name);
    }

    @Override
    public String toString() {
        return "JsonSchema{type='" + type + "', required=" + required + "}";
    }
}
