package com.njydsz.pmis.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SystemConstants 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("SystemConstants 测试")
class SystemConstantsTest {

    @Test
    @DisplayName("SYSTEM_USER_ID - 应等于 SYSTEM 字符串字面量")
    void systemUserId_shouldBeSystem() {
        assertEquals("SYSTEM", SystemConstants.SYSTEM_USER_ID);
    }

    @Test
    @DisplayName("SYSTEM_USER_ID - 应非空且非 blank")
    void systemUserId_shouldNotBeBlank() {
        assertNotNull(SystemConstants.SYSTEM_USER_ID);
        assertFalse(SystemConstants.SYSTEM_USER_ID.isBlank());
    }

    @Test
    @DisplayName("构造方法 - 应私有且不可实例化")
    void constructor_shouldBePrivate() throws NoSuchMethodException {
        Constructor<SystemConstants> ctor = SystemConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "构造方法必须是 private");
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertTrue(ex.getCause() instanceof AssertionError
                        || ex.getCause() instanceof UnsupportedOperationException
                        || ex.getCause() instanceof RuntimeException,
                "调用私有构造应被工具类自检拦截");
    }
}
