package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AuditFieldFiller 单元测试
 *
 * <p>重点验证 P1-审计字段调整：未登录用户时 createdBy/updatedBy 默认值由 {@code "0"}
 * 调整为 {@link SystemConstants#SYSTEM_USER_ID}。
 *
 * <p>本测试通过反射调用 private {@code currentUserId()}，避免对 MyBatis-Plus TableInfo
 * 初始化产生依赖，确保对默认值变更的精准覆盖。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("AuditFieldFiller 测试")
class AuditFieldFillerTest {

    private AuditFieldFiller filler;

    @BeforeEach
    void setUp() {
        filler = new AuditFieldFiller();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    /**
     * 通过反射调用 private currentUserId()，避免依赖 MyBatis-Plus 内部 TableInfo 初始化。
     */
    private String invokeCurrentUserId() throws Exception {
        Method m = AuditFieldFiller.class.getDeclaredMethod("currentUserId");
        m.setAccessible(true);
        return (String) m.invoke(filler);
    }

    @Test
    @DisplayName("currentUserId - 未登录时应返回 SYSTEM_USER_ID")
    void currentUserId_shouldReturnSystemWhenNotLoggedIn() throws Exception {
        assertEquals(SystemConstants.SYSTEM_USER_ID, invokeCurrentUserId());
        assertEquals("SYSTEM", invokeCurrentUserId());
    }

    @Test
    @DisplayName("currentUserId - 已登录时应返回用户 ID")
    void currentUserId_shouldReturnUserIdWhenLoggedIn() throws Exception {
        SecurityContext.setCurrent(LoginUser.builder().userId("1234567890").build());
        assertEquals("1234567890", invokeCurrentUserId());
    }

    @Test
    @DisplayName("currentUserId - 用户 ID 为空时应回退到 SYSTEM_USER_ID")
    void currentUserId_shouldFallBackWhenUserIdIsEmpty() throws Exception {
        SecurityContext.setCurrent(LoginUser.builder().userId("").build());
        assertEquals(SystemConstants.SYSTEM_USER_ID, invokeCurrentUserId());
    }

    @Test
    @DisplayName("currentUserId - 用户 ID 为 null 时应回退到 SYSTEM_USER_ID")
    void currentUserId_shouldFallBackWhenUserIdIsNull() throws Exception {
        SecurityContext.setCurrent(LoginUser.builder().userId(null).build());
        assertEquals(SystemConstants.SYSTEM_USER_ID, invokeCurrentUserId());
    }

    @Test
    @DisplayName("实例化 - 应是 MetaObjectHandler 子类")
    void shouldImplementMetaObjectHandler() {
        assertNotNull(filler);
        assertNotNull(filler.getClass().getInterfaces());
        assertEquals(1, filler.getClass().getInterfaces().length);
        assertEquals("MetaObjectHandler", filler.getClass().getInterfaces()[0].getSimpleName());
    }

    @Test
    @DisplayName("兼容性 - SYSTEM_USER_ID 不应等于字面量 0，确保 SQL DEFAULT 与 Java 一致")
    void systemUserId_mustNotEqualZero() {
        // 历史版本使用 "0"，此次调整后必须彻底切换为 "SYSTEM"；
        // 防止有人误将 DEFAULT 改回 "0" 或 Java 退回 "0"。
        assertNotNull(SystemConstants.SYSTEM_USER_ID);
        org.junit.jupiter.api.Assertions.assertNotEquals("0", SystemConstants.SYSTEM_USER_ID);
    }
}
