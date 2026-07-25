package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * YdszJsonConfig 全局配置测试。
 *
 * @since 1.0.0
 */
class JsonConfigTest {

    @AfterEach
    void resetConfig() {
        // 每个测试后重置全局配置
        YdszJsonConfig.getInstance().reset().apply();
    }

    @Test
    void testGetInstance() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        assertNotNull(config);
        assertSame(config, YdszJsonConfig.getInstance());
    }

    @Test
    void testDefaultValues() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        assertEquals(PropertyNamingStrategy.LOWER_CAMEL_CASE, config.getNamingStrategy());
        assertFalse(config.isWriteNulls());
        assertFalse(config.isPrettyPrint());
        assertFalse(config.isSerializeEnumUsingOrdinal());
        assertFalse(config.isUseBigDecimal());
        assertEquals("", config.getDateFormat());
    }

    @Test
    void testSetWriteNulls() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setWriteNulls(true);
        assertTrue(config.isWriteNulls());
        config.setWriteNulls(false);
        assertFalse(config.isWriteNulls());
    }

    @Test
    void testSetNamingStrategy() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.getNamingStrategy());
    }

    @Test
    void testSetDateFormat() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setDateFormat("yyyy-MM-dd");
        assertEquals("yyyy-MM-dd", config.getDateFormat());
    }

    @Test
    void testSetUseBigDecimal() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setUseBigDecimal(true);
        assertTrue(config.isUseBigDecimal());
    }

    @Test
    void testSetMaxJsonSize() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setMaxJsonSize(1024);
        assertEquals(1024, config.getMaxJsonSize());
    }

    @Test
    void testSetMaxDepth() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setMaxDepth(512);
        assertEquals(512, config.getMaxDepth());
    }

    @Test
    void testReset() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
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
        YdszJsonConfig original = YdszJsonConfig.getInstance();
        original.setWriteNulls(true)
               .setDateFormat("yyyy-MM-dd")
               .setUseBigDecimal(true);

        YdszJsonConfig copy = YdszJsonConfig.copyOf(original);
        assertTrue(copy.isWriteNulls());
        assertEquals("yyyy-MM-dd", copy.getDateFormat());
        assertTrue(copy.isUseBigDecimal());

        // 修改 copy 不影响 original
        copy.setWriteNulls(false);
        assertTrue(original.isWriteNulls());
    }

    @Test
    void testCopyOfNull() {
        YdszJsonConfig copy = YdszJsonConfig.copyOf(null);
        assertNotNull(copy);
    }

    @Test
    void testCopyFrom() {
        YdszJsonConfig source = YdszJsonConfig.copyOf(YdszJsonConfig.getInstance());
        source.setWriteNulls(true)
              .setDateFormat("yyyy-MM-dd HH:mm:ss")
              .setUseBigDecimal(true);

        YdszJsonConfig target = YdszJsonConfig.getInstance();
        target.copyFrom(source);

        assertTrue(target.isWriteNulls());
        assertEquals("yyyy-MM-dd HH:mm:ss", target.getDateFormat());
        assertTrue(target.isUseBigDecimal());
    }

    @Test
    void testToString() {
        String str = YdszJsonConfig.getInstance().toString();
        assertNotNull(str);
        assertTrue(str.contains("YdszJsonConfig"));
    }

    @Test
    void testCircularReferenceStrategy() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setCircularReferenceStrategy(YdszJsonConfig.CircularReferenceStrategy.IGNORE);
        assertEquals(YdszJsonConfig.CircularReferenceStrategy.IGNORE, config.getCircularReferenceStrategy());

        config.setCircularReferenceStrategy(YdszJsonConfig.CircularReferenceStrategy.ERROR);
        assertEquals(YdszJsonConfig.CircularReferenceStrategy.ERROR, config.getCircularReferenceStrategy());

        config.setCircularReferenceStrategy(YdszJsonConfig.CircularReferenceStrategy.REF);
        assertEquals(YdszJsonConfig.CircularReferenceStrategy.REF, config.getCircularReferenceStrategy());
    }

    @Test
    void testApplyDoesNotThrow() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        config.setWriteNulls(true)
              .setPrettyPrint(true)
              .setUseBigDecimal(true)
              .setDateFormat("yyyy-MM-dd")
              .setNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
              .apply();
        // apply() 应正常执行不抛出异常
    }
}
