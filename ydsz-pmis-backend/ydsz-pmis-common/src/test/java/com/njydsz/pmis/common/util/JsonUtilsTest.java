package com.njydsz.pmis.common.util;

import com.alibaba.fastjson2.TypeReference;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JsonUtils 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("JsonUtils 测试")
class JsonUtilsTest {

    // ==================== parseMap ====================

    @Test
    @DisplayName("parseMap - 普通 JSON 对象应返回 Map<String, Object>")
    void parseMap_shouldReturnMapForJsonObject() {
        String json = "{\"name\":\"pmis\",\"version\":1,\"active\":true}";
        Map<String, Object> map = JsonUtils.parseMap(json);

        assertThat(map).isNotNull();
        assertThat(map.get("name")).isEqualTo("pmis");
        assertThat(map.get("version")).isEqualTo(1);
        assertThat(map.get("active")).isEqualTo(true);
    }

    @Test
    @DisplayName("parseMap - null 输入应返回 null")
    void parseMap_shouldReturnNullForNullInput() {
        assertThat(JsonUtils.parseMap(null)).isNull();
    }

    @Test
    @DisplayName("parseMap - 空白字符串应返回 null")
    void parseMap_shouldReturnNullForBlankInput() {
        assertThat(JsonUtils.parseMap("   ")).isNull();
        assertThat(JsonUtils.parseMap("")).isNull();
    }

    @Test
    @DisplayName("parseMap - 嵌套 JSON 对象应正确解析")
    void parseMap_shouldParseNestedObject() {
        String json = "{\"user\":{\"name\":\"alice\",\"roles\":[\"admin\",\"user\"]}}";
        Map<String, Object> map = JsonUtils.parseMap(json);

        assertThat(map).isNotNull();
        Object user = map.get("user");
        assertThat(user).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) user;
        assertThat(userMap.get("name")).isEqualTo("alice");
        assertThat(userMap.get("roles")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("parseMap - 非法 JSON 应抛出异常（由调用方处理）")
    void parseMap_shouldThrowForInvalidJson() {
        assertThrows(Exception.class, () -> JsonUtils.parseMap("{invalid json}"));
    }

    // ==================== parseList ====================

    @Test
    @DisplayName("parseList - JSON 数组应返回 List<Object>")
    void parseList_shouldReturnListForJsonArray() {
        String json = "[1,2,3,\"four\",true]";
        List<Object> list = JsonUtils.parseList(json);

        assertThat(list).isNotNull();
        assertThat(list).hasSize(5);
        assertThat(list.get(0)).isEqualTo(1);
        assertThat(list.get(3)).isEqualTo("four");
        assertThat(list.get(4)).isEqualTo(true);
    }

    @Test
    @DisplayName("parseList - null 输入应返回 null")
    void parseList_shouldReturnNullForNullInput() {
        assertThat(JsonUtils.parseList(null)).isNull();
    }

    @Test
    @DisplayName("parseList - 空白字符串应返回 null")
    void parseList_shouldReturnNullForBlankInput() {
        assertThat(JsonUtils.parseList("  ")).isNull();
    }

    // ==================== parseObject(Class) ====================

    @Test
    @DisplayName("parseObject(Class) - 应正确反序列化为指定类型")
    void parseObject_shouldDeserializeToTargetType() {
        String json = "{\"name\":\"pmis\",\"age\":18}";
        Person person = JsonUtils.parseObject(json, Person.class);

        assertThat(person).isNotNull();
        assertThat(person.getName()).isEqualTo("pmis");
        assertThat(person.getAge()).isEqualTo(18);
    }

    @Test
    @DisplayName("parseObject(Class) - null 输入应返回 null")
    void parseObject_shouldReturnNullForNullInput() {
        assertThat(JsonUtils.parseObject(null, Person.class)).isNull();
    }

    @Test
    @DisplayName("parseObject(Class) - 空白字符串应返回 null")
    void parseObject_shouldReturnNullForBlankInput() {
        assertThat(JsonUtils.parseObject("  ", Person.class)).isNull();
    }

    // ==================== parseObject(TypeReference) ====================

    @Test
    @DisplayName("parseObject(TypeReference) - 应正确反序列化复杂泛型类型")
    void parseObject_shouldDeserializeComplexGenericType() {
        String json = "{\"alice\":[1,2],\"bob\":[3,4,5]}";
        Map<String, List<Integer>> result = JsonUtils.parseObject(json,
                new TypeReference<Map<String, List<Integer>>>() {
                });

        assertThat(result).isNotNull();
        assertThat(result.get("alice")).containsExactly(1, 2);
        assertThat(result.get("bob")).containsExactly(3, 4, 5);
    }

    @Test
    @DisplayName("parseObject(TypeReference) - null 输入应返回 null")
    void parseObject_typeReferenceShouldReturnNullForNullInput() {
        Map<String, List<Integer>> result = JsonUtils.parseObject(null,
                new TypeReference<Map<String, List<Integer>>>() {
                });
        assertThat(result).isNull();
    }

    // ==================== toJson ====================

    @Test
    @DisplayName("toJson - 对象应正确序列化为 JSON 字符串")
    void toJson_shouldSerializeObject() {
        Person person = new Person();
        person.setName("pmis");
        person.setAge(18);

        String json = JsonUtils.toJson(person);

        assertThat(json).contains("\"name\":\"pmis\"");
        assertThat(json).contains("\"age\":18");
    }

    @Test
    @DisplayName("toJson - Map 应正确序列化")
    void toJson_shouldSerializeMap() {
        Map<String, Object> map = Map.of("k1", "v1", "k2", 2);
        String json = JsonUtils.toJson(map);

        assertThat(json).contains("\"k1\":\"v1\"");
        assertThat(json).contains("\"k2\":2");
    }

    @Test
    @DisplayName("toJson - null 对象应返回 null")
    void toJson_shouldReturnNullForNullInput() {
        assertThat(JsonUtils.toJson(null)).isNull();
    }

    // ==================== 往返测试 ====================

    @Test
    @DisplayName("往返测试 - toJson → parseMap 应保留数据")
    void roundTrip_shouldPreserveData() {
        Map<String, Object> original = Map.of(
                "name", "pmis",
                "version", 1,
                "tags", List.of("fast", "secure"));
        String json = JsonUtils.toJson(original);

        Map<String, Object> parsed = JsonUtils.parseMap(json);

        assertThat(parsed).isNotNull();
        assertThat(parsed.get("name")).isEqualTo("pmis");
        assertThat(parsed.get("version")).isEqualTo(1);
        assertThat(parsed.get("tags")).isInstanceOf(List.class);
    }

    /**
     * 测试用 POJO
     */
    @Data
    static class Person {
        private String name;
        private Integer age;
    }
}
