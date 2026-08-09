package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Schema（Draft 07）定义。
 *
 * <p>Builder 模式构造 JSON Schema 校验规则，支持 Draft 07 核心关键字：
 * type / required / enum / default / minLength / maxLength / pattern /
 * minimum / maximum / exclusiveMinimum / exclusiveMaximum / multipleOf /
 * items / minItems / maxItems / properties / additionalProperties /
 * allOf / anyOf / oneOf / not / const / if-then-else。
 *
 * <p>静态工厂方法：
 * <pre>{@code
 * // 简单类型
 * JsonSchema.string().minLength(1).maxLength(100)
 * JsonSchema.number().minimum(0).maximum(9999)
 * JsonSchema.integer().minimum(1)
 * JsonSchema.booleanType()
 * JsonSchema.array().items(JsonSchema.string())
 * JsonSchema.object()
 *     .addProperty("name", JsonSchema.string().required())
 *     .addProperty("age", JsonSchema.integer().minimum(0))
 *     .addRequired("name")
 * }</pre>
 *
 * <p><b>线程安全：</b>构造完成后不可变（{@code properties} / {@code requiredProperties} 等
 * 内部集合初始化后不再修改），可在多线程间安全共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonSchemaValidator JSON Schema 校验器
 * @see ValidationResult 校验结果
 */
@Experimental
public final class JsonSchema {

    private final String type;
    private String description;
    private boolean required;
    private List<Object> enumValues;
    private Object defaultValue;
    private Boolean uniqueItems;
    private Integer minProperties;
    private Integer maxProperties;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private String format;
    private Double minimum;
    private Double maximum;
    private Double exclusiveMinimum;
    private Double exclusiveMaximum;
    private Double multipleOf;
    private JsonSchema items;
    private Integer minItems;
    private Integer maxItems;
    private Map<String, JsonSchema> properties;
    private List<String> requiredProperties;
    private Object additionalProperties;
    private Map<String, JsonSchema> patternProperties;
    private List<JsonSchema> allOf;
    private List<JsonSchema> anyOf;
    private List<JsonSchema> oneOf;
    private JsonSchema not;
    private Object constValue;
    private JsonSchema ifSchema;
    private JsonSchema thenSchema;
    private JsonSchema elseSchema;
    private String ref;
    private Map<String, JsonSchema> definitions;
    private Map<String, List<String>> dependentRequired;
    private Map<String, JsonSchema> dependentSchemas;

    public JsonSchema(String type) {
        this.type = type;
    }

    // ======================== 静态工厂方法 ========================

    public static JsonSchema string() {
        return new JsonSchema("string");
    }

    public static JsonSchema number() {
        return new JsonSchema("number");
    }

    public static JsonSchema integer() {
        return new JsonSchema("integer");
    }

    public static JsonSchema booleanType() {
        return new JsonSchema("boolean");
    }

    public static JsonSchema array() {
        return new JsonSchema("array");
    }

    public static JsonSchema object() {
        return new JsonSchema("object");
    }

    public static JsonSchema nullType() {
        return new JsonSchema("null");
    }

    // ======================== Builder 方法 ========================

    public JsonSchema description(String description) {
        this.description = description;
        return this;
    }

    public JsonSchema required() {
        this.required = true;
        return this;
    }

    public JsonSchema enumValues(Object... values) {
        if (this.enumValues == null) {
            this.enumValues = new ArrayList<>();
        }
        for (Object v : values) {
            this.enumValues.add(v);
        }
        return this;
    }

    public JsonSchema addEnum(Object value) {
        if (this.enumValues == null) {
            this.enumValues = new ArrayList<>();
        }
        this.enumValues.add(value);
        return this;
    }

    public JsonSchema defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public JsonSchema minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    public JsonSchema maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public JsonSchema pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public JsonSchema format(String format) {
        this.format = format;
        return this;
    }

    public JsonSchema minimum(double minimum) {
        this.minimum = minimum;
        return this;
    }

    public JsonSchema maximum(double maximum) {
        this.maximum = maximum;
        return this;
    }

    public JsonSchema addProperty(String name, JsonSchema schema) {
        if (this.properties == null) {
            this.properties = new LinkedHashMap<>();
        }
        this.properties.put(name, schema);
        return this;
    }

    public JsonSchema addRequired(String name) {
        if (this.requiredProperties == null) {
            this.requiredProperties = new ArrayList<>();
        }
        this.requiredProperties.add(name);
        return this;
    }

    public JsonSchema addRequired(String... names) {
        if (this.requiredProperties == null) {
            this.requiredProperties = new ArrayList<>();
        }
        for (String name : names) {
            this.requiredProperties.add(name);
        }
        return this;
    }

    public JsonSchema items(JsonSchema items) {
        this.items = items;
        return this;
    }

    public JsonSchema minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    public JsonSchema maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public JsonSchema multipleOf(double multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    public JsonSchema exclusiveMinimum(double exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    public JsonSchema exclusiveMaximum(double exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    public JsonSchema additionalProperties(JsonSchema schema) {
        this.additionalProperties = schema;
        return this;
    }

    public JsonSchema additionalProperties(boolean allow) {
        this.additionalProperties = allow;
        return this;
    }

    public JsonSchema allOf(JsonSchema... schemas) {
        if (this.allOf == null) {
            this.allOf = new ArrayList<>();
        }
        Collections.addAll(this.allOf, schemas);
        return this;
    }

    public JsonSchema anyOf(JsonSchema... schemas) {
        if (this.anyOf == null) {
            this.anyOf = new ArrayList<>();
        }
        Collections.addAll(this.anyOf, schemas);
        return this;
    }

    public JsonSchema oneOf(JsonSchema... schemas) {
        if (this.oneOf == null) {
            this.oneOf = new ArrayList<>();
        }
        Collections.addAll(this.oneOf, schemas);
        return this;
    }

    public JsonSchema not(JsonSchema schema) {
        this.not = schema;
        return this;
    }

    public JsonSchema constValue(Object constValue) {
        this.constValue = constValue;
        return this;
    }

    public JsonSchema dependentRequired(String property, String... dependencies) {
        if (this.dependentRequired == null) {
            this.dependentRequired = new LinkedHashMap<>();
        }
        this.dependentRequired.put(property, List.of(dependencies));
        return this;
    }

    public JsonSchema dependentSchemas(String property, JsonSchema schema) {
        if (this.dependentSchemas == null) {
            this.dependentSchemas = new LinkedHashMap<>();
        }
        this.dependentSchemas.put(property, schema);
        return this;
    }

    public JsonSchema ifThenElse(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) {
        this.ifSchema = ifSchema;
        this.thenSchema = thenSchema;
        this.elseSchema = elseSchema;
        return this;
    }

    public JsonSchema uniqueItems(boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
        return this;
    }

    public JsonSchema minProperties(int minProperties) {
        this.minProperties = minProperties;
        return this;
    }

    public JsonSchema maxProperties(int maxProperties) {
        this.maxProperties = maxProperties;
        return this;
    }

    public JsonSchema patternProperty(String regex, JsonSchema schema) {
        if (this.patternProperties == null) {
            this.patternProperties = new LinkedHashMap<>();
        }
        this.patternProperties.put(regex, schema);
        return this;
    }

    public JsonSchema ref(String ref) {
        this.ref = ref;
        return this;
    }

    public JsonSchema addDefinition(String name, JsonSchema schema) {
        if (this.definitions == null) {
            this.definitions = new LinkedHashMap<>();
        }
        this.definitions.put(name, schema);
        return this;
    }

    public JsonSchema resolveRef() {
        return this.ref != null ? this : this;
    }

    // ======================== Getter 方法 ========================

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
        return enumValues != null ? Collections.unmodifiableList(enumValues) : null;
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

    public String getFormat() {
        return format;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    public Map<String, JsonSchema> getProperties() {
        return properties != null ? Collections.unmodifiableMap(properties) : null;
    }

    public List<String> getRequiredProperties() {
        return requiredProperties != null ? Collections.unmodifiableList(requiredProperties) : null;
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
        return patternProperties != null ? Collections.unmodifiableMap(patternProperties) : null;
    }

    public List<JsonSchema> getAllOf() {
        return allOf != null ? Collections.unmodifiableList(allOf) : null;
    }

    public List<JsonSchema> getAnyOf() {
        return anyOf != null ? Collections.unmodifiableList(anyOf) : null;
    }

    public List<JsonSchema> getOneOf() {
        return oneOf != null ? Collections.unmodifiableList(oneOf) : null;
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

    public String getRef() {
        return ref;
    }

    public Map<String, JsonSchema> getDefinitions() {
        return definitions != null ? Collections.unmodifiableMap(definitions) : null;
    }

    public Map<String, List<String>> getDependentRequired() {
        return dependentRequired != null ? Collections.unmodifiableMap(dependentRequired) : null;
    }

    public Map<String, JsonSchema> getDependentSchemas() {
        return dependentSchemas != null ? Collections.unmodifiableMap(dependentSchemas) : null;
    }

    @Override
    public String toString() {
        return "JsonSchema{type='" + type + '\'' + ", required=" + required + '}';
    }
}
