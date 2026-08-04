package com.remisoft.common.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link MapUtils} 单元测试
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("MapUtils 工具类测试")
class MapUtilsTest {

    // ==================== isEmpty / isNotEmpty ====================

    @Nested
    @DisplayName("isEmpty / isNotEmpty")
    class EmptyTest {

        @Test
        @DisplayName("null Map 为空")
        void nullMap() {
            assertThat(MapUtils.isEmpty(null)).isTrue();
            assertThat(MapUtils.isNotEmpty(null)).isFalse();
        }

        @Test
        @DisplayName("空 Map 为空")
        void emptyMap() {
            assertThat(MapUtils.isEmpty(Collections.emptyMap())).isTrue();
            assertThat(MapUtils.isNotEmpty(Collections.emptyMap())).isFalse();
        }

        @Test
        @DisplayName("有元素 Map 不为空")
        void nonEmptyMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("k", "v");
            assertThat(MapUtils.isEmpty(map)).isFalse();
            assertThat(MapUtils.isNotEmpty(map)).isTrue();
        }
    }

    // ==================== 类型安全取值 ====================

    @Nested
    @DisplayName("getString")
    class GetStringTest {

        @Test
        @DisplayName("正常取值")
        void normal() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "Alice");
            assertThat(MapUtils.getString(map, "name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("非 String 类型调用 toString")
        void nonString() {
            Map<String, Object> map = new HashMap<>();
            map.put("age", 25);
            assertThat(MapUtils.getString(map, "age")).isEqualTo("25");
        }

        @Test
        @DisplayName("key 不存在返回 null")
        void missingKey() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", "Alice");
            assertThat(MapUtils.getString(map, "missing")).isNull();
        }

        @Test
        @DisplayName("null map 返回 null")
        void nullMap() {
            assertThat(MapUtils.getString(null, "key")).isNull();
        }
    }

    @Nested
    @DisplayName("getInteger")
    class GetIntegerTest {

        @Test
        @DisplayName("Integer 直接返回")
        void integer() {
            Map<String, Object> map = new HashMap<>();
            map.put("age", 25);
            assertThat(MapUtils.getInteger(map, "age")).isEqualTo(25);
        }

        @Test
        @DisplayName("Long 转 Integer")
        void longValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("age", 25L);
            assertThat(MapUtils.getInteger(map, "age")).isEqualTo(25);
        }

        @Test
        @DisplayName("String 转 Integer")
        void stringValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("age", "25");
            assertThat(MapUtils.getInteger(map, "age")).isEqualTo(25);
        }

        @Test
        @DisplayName("非数字 String 返回 null")
        void nonNumericString() {
            Map<String, Object> map = new HashMap<>();
            map.put("age", "abc");
            assertThat(MapUtils.getInteger(map, "age")).isNull();
        }

        @Test
        @DisplayName("null map 返回 null")
        void nullMap() {
            assertThat(MapUtils.getInteger(null, "key")).isNull();
        }
    }

    @Nested
    @DisplayName("getLong")
    class GetLongTest {

        @Test
        @DisplayName("Long 直接返回")
        void longValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", 100L);
            assertThat(MapUtils.getLong(map, "id")).isEqualTo(100L);
        }

        @Test
        @DisplayName("Integer 转 Long")
        void integerValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", 100);
            assertThat(MapUtils.getLong(map, "id")).isEqualTo(100L);
        }

        @Test
        @DisplayName("String 转 Long")
        void stringValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", "100");
            assertThat(MapUtils.getLong(map, "id")).isEqualTo(100L);
        }

        @Test
        @DisplayName("非数字 String 返回 null")
        void nonNumericString() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", "abc");
            assertThat(MapUtils.getLong(map, "id")).isNull();
        }
    }

    @Nested
    @DisplayName("getBoolean")
    class GetBooleanTest {

        @Test
        @DisplayName("Boolean 直接返回")
        void booleanValue() {
            Map<String, Object> map = new HashMap<>();
            map.put("flag", true);
            assertThat(MapUtils.getBoolean(map, "flag")).isTrue();
        }

        @Test
        @DisplayName("String true 返回 true")
        void stringTrue() {
            Map<String, Object> map = new HashMap<>();
            map.put("flag", "true");
            assertThat(MapUtils.getBoolean(map, "flag")).isTrue();
        }

        @Test
        @DisplayName("String 1 返回 true")
        void stringOne() {
            Map<String, Object> map = new HashMap<>();
            map.put("flag", "1");
            assertThat(MapUtils.getBoolean(map, "flag")).isTrue();
        }

        @Test
        @DisplayName("String false 返回 false")
        void stringFalse() {
            Map<String, Object> map = new HashMap<>();
            map.put("flag", "false");
            assertThat(MapUtils.getBoolean(map, "flag")).isFalse();
        }

        @Test
        @DisplayName("无法识别的字符串返回 null")
        void unrecognizedString() {
            Map<String, Object> map = new HashMap<>();
            map.put("flag", "maybe");
            assertThat(MapUtils.getBoolean(map, "flag")).isNull();
        }
    }

    @Nested
    @DisplayName("getMap / getList")
    class GetContainerTest {

        @Test
        @DisplayName("getMap 正常返回")
        void getMap() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("k", "v");
            Map<String, Object> map = new HashMap<>();
            map.put("nested", inner);
            Map<?, ?> result = MapUtils.getMap(map, "nested");
            assertThat(result).isNotNull();
            assertThat(result.get("k")).isEqualTo("v");
        }

        @Test
        @DisplayName("getMap 非 Map 类型返回 null")
        void getMapNonMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("val", 42);
            assertThat(MapUtils.getMap(map, "val")).isNull();
        }

        @Test
        @DisplayName("getList 正常返回")
        void getList() {
            List<Integer> list = Arrays.asList(1, 2, 3);
            Map<String, Object> map = new HashMap<>();
            map.put("items", list);
            List<?> result = MapUtils.getList(map, "items");
            assertThat(result).isNotNull().hasSize(3);
            assertThat(result.get(0)).isEqualTo(1);
            assertThat(result.get(1)).isEqualTo(2);
            assertThat(result.get(2)).isEqualTo(3);
        }

        @Test
        @DisplayName("getList 非 List 类型返回 null")
        void getListNonList() {
            Map<String, Object> map = new HashMap<>();
            map.put("val", "string");
            assertThat(MapUtils.getList(map, "val")).isNull();
        }
    }

    // ==================== JSON Map 归一化 ====================

    @Nested
    @DisplayName("toStringObjectMap")
    class ToStringObjectMapTest {

        @Test
        @DisplayName("正常转换 - Integer key 转 String key")
        void integerKey() {
            Map<Integer, String> source = new LinkedHashMap<>();
            source.put(1, "one");
            source.put(2, "two");
            Map<String, Object> result = MapUtils.toStringObjectMap(source);
            assertThat(result).hasSize(2)
                    .containsEntry("1", "one")
                    .containsEntry("2", "two");
        }

        @Test
        @DisplayName("null Map 返回空 Map")
        void nullMap() {
            Map<String, Object> result = MapUtils.toStringObjectMap(null);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("safeCastMap")
    class SafeCastMapTest {

        @Test
        @DisplayName("Map 对象安全转换")
        void mapObject() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("k", "v");
            Map<String, Object> result = MapUtils.safeCastMap(inner);
            assertThat(result).containsEntry("k", "v");
        }

        @Test
        @DisplayName("非 Map 对象返回 null")
        void nonMap() {
            assertThat(MapUtils.safeCastMap("string")).isNull();
        }

        @Test
        @DisplayName("null 返回 null")
        void nullObj() {
            assertThat(MapUtils.safeCastMap(null)).isNull();
        }
    }

    @Nested
    @DisplayName("safeCastList")
    class SafeCastListTest {

        @Test
        @DisplayName("类型匹配的元素全部保留")
        void matchingType() {
            List<?> raw = Arrays.asList("a", "b", "c");
            List<String> result = MapUtils.safeCastList(raw, String.class);
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("类型不匹配的元素被跳过")
        void mixedType() {
            List<?> raw = Arrays.asList("a", 1, "b", 2);
            List<String> result = MapUtils.safeCastList(raw, String.class);
            assertThat(result).containsExactly("a", "b");
        }

        @Test
        @DisplayName("null 对象返回空 List")
        void nullObj() {
            List<String> result = MapUtils.safeCastList(null, String.class);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("非 List 对象返回空 List")
        void nonList() {
            List<String> result = MapUtils.safeCastList("string", String.class);
            assertThat(result).isEmpty();
        }
    }

    // ==================== 嵌套 JSON 解析 ====================

    @Nested
    @DisplayName("getListOfMaps")
    class GetListOfMapsTest {

        @Test
        @DisplayName("正常解析嵌套 List<Map>")
        void normal() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("k1", "v1");
            Map<String, Object> m2 = new HashMap<>();
            m2.put("k2", "v2");
            List<Map<String, Object>> list = Arrays.asList(m1, m2);

            Map<String, Object> root = new HashMap<>();
            root.put("nodes", list);

            List<Map<String, Object>> result = MapUtils.getListOfMaps(root, "nodes");
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("k1", "v1");
            assertThat(result.get(1)).containsEntry("k2", "v2");
        }

        @Test
        @DisplayName("非 List 元素被跳过")
        void skipNonMapElements() {
            List<?> mixed = Arrays.asList(
                    new HashMap<String, Object>() {{ put("k", "v"); }},
                    "string-element",
                    new HashMap<String, Object>() {{ put("k2", "v2"); }}
            );
            Map<String, Object> root = new HashMap<>();
            root.put("nodes", mixed);

            List<Map<String, Object>> result = MapUtils.getListOfMaps(root, "nodes");
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("key 不存在返回空 List")
        void missingKey() {
            Map<String, Object> root = new HashMap<>();
            root.put("other", "val");
            assertThat(MapUtils.getListOfMaps(root, "nodes")).isEmpty();
        }

        @Test
        @DisplayName("null map 返回空 List")
        void nullMap() {
            assertThat(MapUtils.getListOfMaps(null, "key")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMapFromList")
    class GetMapFromListTest {

        @Test
        @DisplayName("正常按下标取 Map")
        void normal() {
            Map<String, Object> m1 = new HashMap<>();
            m1.put("k1", "v1");
            Map<String, Object> m2 = new HashMap<>();
            m2.put("k2", "v2");
            List<?> list = Arrays.asList(m1, m2);

            Map<String, Object> result = MapUtils.getMapFromList(list, 1);
            assertThat(result).containsEntry("k2", "v2");
        }

        @Test
        @DisplayName("下标越界返回 null")
        void outOfBounds() {
            List<?> list = Arrays.asList("a", "b");
            assertThat(MapUtils.getMapFromList(list, 5)).isNull();
        }

        @Test
        @DisplayName("负下标返回 null")
        void negativeIndex() {
            List<?> list = Arrays.asList("a");
            assertThat(MapUtils.getMapFromList(list, -1)).isNull();
        }

        @Test
        @DisplayName("null list 返回 null")
        void nullList() {
            assertThat(MapUtils.getMapFromList(null, 0)).isNull();
        }

        @Test
        @DisplayName("非 Map 元素返回 null")
        void nonMapElement() {
            List<?> list = Arrays.asList("string", 42);
            assertThat(MapUtils.getMapFromList(list, 0)).isNull();
        }
    }
}
