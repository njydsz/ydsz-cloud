package com.njydsz.pmis.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("构造方法 - 应私有且不可通过 new 直接实例化（反射创建可成功，但需被标记 private）")
    void constructor_shouldBePrivate() throws Exception {
        Constructor<SystemConstants> ctor = SystemConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "构造方法必须是 private");

        // 与项目内 CommonConstants / CacheConstants 保持一致，私有构造不抛异常（仅禁止外部 new），
        // 因此反射创建应可成功，但调用方不应将其作为对象使用。
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        assertNotNull(instance);
        assertInstanceOf(SystemConstants.class, instance);
    }
}
