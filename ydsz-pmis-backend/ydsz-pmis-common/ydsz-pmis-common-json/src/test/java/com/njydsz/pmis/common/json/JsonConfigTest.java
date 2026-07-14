package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.json.config.JsonConfig;
import com.njydsz.pmis.common.json.naming.PropertyNamingStrategy;

/**
 * JsonConfig 全局配置测试。
 *
 * @since 1.4.0
 */
class JsonConfigTest {

    @AfterEach
    void resetConfig() {
        // 每个测试后重置全局配置
        JsonConfig.getInstance().reset().apply();
    }

    @Test
    void testGetInstance() {
        JsonConfig config = JsonConfig.getInstance();
        assertNotNull(config);
        assertSame(config, JsonConfig.getInstance());
    }

    @Test
    void testDefaultValues() {
        JsonConfig config = JsonConfig.getInstance();
        assertEquals(PropertyNamingStrategy.LOWER_CAMEL_CASE, config.getNamingStrategy());
        assertFalse(config.isWriteNulls());
        assertFalse(config.isPrettyPrint());
        assertFalse(config.isSerializeEnumUsingOrdinal());
        assertFalse(config.isUseBigDecimal());
        assertEquals("", config.getDateFormat());
    }

    @Test
    void testSetWriteNulls() {
        JsonConfig config = JsonConfig.getInstance();
        config.setWriteNulls(true);
        assertTrue(config.isWriteNulls());
        config.setWriteNulls(false);
        assertFalse(config.isWriteNulls());
    }

    @Test
    void testSetNamingStrategy() {
        JsonConfig config = JsonConfig.getInstance();
        config.setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.getNamingStrategy());
    }

    @Test
    void testSetDateFormat() {
        JsonConfig config = JsonConfig.getInstance();
        config.setDateFormat("yyyy-MM-dd");
        assertEquals("yyyy-MM-dd", config.getDateFormat());
    }

    @Test
    void testSetUseBigDecimal() {
        JsonConfig config = JsonConfig.getInstance();
        config.setUseBigDecimal(true);
        assertTrue(config.isUseBigDecimal());
    }

    @Test
    void testSetMaxJsonSize() {
        JsonConfig config = JsonConfig.getInstance();
        config.setMaxJsonSize(1024);
        assertEquals(1024, config.getMaxJsonSize());
    }

    @Test
    void testSetMaxDepth() {
        JsonConfig config = JsonConfig.getInstance();
        config.setMaxDepth(512);
        assertEquals(512, config.getMaxDepth());
    }

    @Test
    void testReset() {
        JsonConfig config = JsonConfig.getInstance();
        config.setWriteNulls(true)
              .setPrettyPrint(true)
              .setSerializeEnumUsingOrdinal(true)
              .setUseBigDecimal(true)
              .setDateFormat("yyyy-MM-dd");

        config.reset();

        assertFalse(config.isWriteNulls());
        assertFalse(config.isPrettyPrint());
        assertFalse(config.isSerializeEnumUsingOrdinal());
        assertFalse(config.isUseBigDecimal());
        assertEquals("", config.getDateFormat());
    }

    @Test
    void testCopyOf() {
        JsonConfig original = JsonConfig.getInstance();
        original.setWriteNulls(true)
               .setDateFormat("yyyy-MM-dd")
               .setUseBigDecimal(true);

        JsonConfig copy = JsonConfig.copyOf(original);
        assertTrue(copy.isWriteNulls());
        assertEquals("yyyy-MM-dd", copy.getDateFormat());
        assertTrue(copy.isUseBigDecimal());

        // 修改 copy 不影响 original
        copy.setWriteNulls(false);
        assertTrue(original.isWriteNulls());
    }

    @Test
    void testCopyOfNull() {
        JsonConfig copy = JsonConfig.copyOf(null);
        assertNotNull(copy);
    }

    @Test
    void testCopyFrom() {
        JsonConfig source = JsonConfig.copyOf(JsonConfig.getInstance());
        source.setWriteNulls(true)
              .setDateFormat("yyyy-MM-dd HH:mm:ss")
              .setUseBigDecimal(true);

        JsonConfig target = JsonConfig.getInstance();
        target.copyFrom(source);

        assertTrue(target.isWriteNulls());
        assertEquals("yyyy-MM-dd HH:mm:ss", target.getDateFormat());
        assertTrue(target.isUseBigDecimal());
    }

    @Test
    void testToString() {
        String str = JsonConfig.getInstance().toString();
        assertNotNull(str);
        assertTrue(str.contains("JsonConfig"));
    }

    @Test
    void testCircularReferenceStrategy() {
        JsonConfig config = JsonConfig.getInstance();
        config.setCircularReferenceStrategy(JsonConfig.CircularReferenceStrategy.IGNORE);
        assertEquals(JsonConfig.CircularReferenceStrategy.IGNORE, config.getCircularReferenceStrategy());

        config.setCircularReferenceStrategy(JsonConfig.CircularReferenceStrategy.ERROR);
        assertEquals(JsonConfig.CircularReferenceStrategy.ERROR, config.getCircularReferenceStrategy());

        config.setCircularReferenceStrategy(JsonConfig.CircularReferenceStrategy.REF);
        assertEquals(JsonConfig.CircularReferenceStrategy.REF, config.getCircularReferenceStrategy());
    }

    @Test
    void testApplyDoesNotThrow() {
        JsonConfig config = JsonConfig.getInstance();
        config.setWriteNulls(true)
              .setPrettyPrint(true)
              .setUseBigDecimal(true)
              .setDateFormat("yyyy-MM-dd")
              .setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
              .apply();
        // apply() 应正常执行不抛出异常
    }
}
