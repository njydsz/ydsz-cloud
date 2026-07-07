package com.njydsz.pmis.common.datasource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSourceConstants 单元测试（P2-3 读写分离）
 *
 * <p>验证动态数据源常量值正确，且常量类不可实例化。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DataSourceConstants 测试")
class DataSourceConstantsTest {

    @Test
    @DisplayName("主库常量 - 应等于 master")
    void master_shouldBeMaster() {
        assertNotNull(DataSourceConstants.MASTER);
        assertFalse(DataSourceConstants.MASTER.isBlank());
        assertEquals("master", DataSourceConstants.MASTER);
    }

    @Test
    @DisplayName("从库常量 - 应等于 slave")
    void slave_shouldBeSlave() {
        assertNotNull(DataSourceConstants.SLAVE);
        assertFalse(DataSourceConstants.SLAVE.isBlank());
        assertEquals("slave", DataSourceConstants.SLAVE);
    }

    @Test
    @DisplayName("主从常量 - 应互不相同（区分读写数据源）")
    void masterAndSlave_shouldBeDifferent() {
        assertNotEquals(DataSourceConstants.MASTER, DataSourceConstants.SLAVE);
    }

    @Test
    @DisplayName("常量类 - 构造函数私有，禁止实例化")
    void constructor_shouldBePrivate() throws NoSuchMethodException {
        Constructor<DataSourceConstants> constructor =
                DataSourceConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                "DataSourceConstants 构造函数应为 private，禁止实例化");
    }
}

