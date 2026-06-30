package com.njydsz.pmis.common.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrePermission 注解单元测试
 */
@DisplayName("PrePermission 注解测试")
class PrePermissionTest {

    @PrePermission(value = {"user:list", "user:create"}, mode = PrePermission.Mode.AND, requireLogin = true)
    public void annotated() {
    }

    @Test
    @DisplayName("注解应能正确读取 value/mode/requireLogin")
    void annotationRead() throws NoSuchMethodException {
        Method m = PrePermissionTest.class.getMethod("annotated");
        PrePermission ann = m.getAnnotation(PrePermission.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).containsExactly("user:list", "user:create");
        assertThat(ann.mode()).isEqualTo(PrePermission.Mode.AND);
        assertThat(ann.requireLogin()).isTrue();
    }

    @Test
    @DisplayName("Mode 枚举应包含 AND/OR")
    void modeEnum() {
        assertThat(PrePermission.Mode.values()).hasSize(2);
        assertThat(PrePermission.Mode.valueOf("AND")).isNotNull();
        assertThat(PrePermission.Mode.valueOf("OR")).isNotNull();
    }
}
