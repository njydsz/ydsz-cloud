package com.njydsz.pmis.common.json.schema;

import java.util.*;

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
 * @since 1.3.0
 * @since 1.3.0
 */
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
    private JsonSchema additionalProperties;

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
     * 布尔类型（简化方法名）
     */
    public static JsonSchema boolean_() {
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
        this.enumValues = Arrays.asList(values);
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
     * 设置额外属性 Schema
     */
    public JsonSchema additionalProperties(JsonSchema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    /**
     * Null 类型
     */
    public static JsonSchema nullType() {
        return new JsonSchema("null");
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

    public Double getMultipleOf() {
        return multipleOf;
    }

    public Double getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Double getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public JsonSchema getAdditionalProperties() {
        return additionalProperties;
    }

    @Override
    public String toString() {
        return "JsonSchema{type='" + type + "', required=" + required + "}";
    }
}
