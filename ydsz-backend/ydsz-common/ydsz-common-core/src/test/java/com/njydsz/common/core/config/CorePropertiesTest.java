package com.njydsz.common.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * {@link CoreProperties} 配置校验测试
 *
 * <p>验证 JSR-303 注解（@Min/@Max/@NotBlank/@Pattern）的 fail-fast 行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("CoreProperties 配置校验测试")
class CorePropertiesTest {

    private final Validator validator;

    CorePropertiesTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("默认配置通过校验")
    void defaults_valid() {
        CoreProperties props = new CoreProperties();
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + violations);
    }

    @Test
    @DisplayName("maxPageSize 为 0 时校验失败")
    void maxPageSize_zero_invalid() {
        CoreProperties props = new CoreProperties();
        props.setMaxPageSize(0);
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("maxPageSize 超过 5000 时校验失败")
    void maxPageSize_tooLarge_invalid() {
        CoreProperties props = new CoreProperties();
        props.setMaxPageSize(5001);
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("maxPageSize 边界值 1 与 5000 通过校验")
    void maxPageSize_boundaryValid() {
        CoreProperties props = new CoreProperties();
        props.setMaxPageSize(1);
        assertTrue(validator.validate(props).isEmpty());

        props.setMaxPageSize(5000);
        assertTrue(validator.validate(props).isEmpty());
    }

    @Test
    @DisplayName("defaultPageSize 为负数时校验失败")
    void defaultPageSize_negative_invalid() {
        CoreProperties props = new CoreProperties();
        props.setDefaultPageSize(-1);
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("idType 非法值时校验失败")
    void idType_invalid() {
        CoreProperties props = new CoreProperties();
        props.getTrace().setIdType("md5");
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                        .anyMatch(v -> v.getMessage().contains("uuid") || v.getMessage().contains("snowflake")),
                "error message should mention allowed values: " + violations);
    }

    @Test
    @DisplayName("idType 为空时校验失败")
    void idType_blank_invalid() {
        CoreProperties props = new CoreProperties();
        props.getTrace().setIdType(" ");
        Set<ConstraintViolation<CoreProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("idType 为 uuid / snowflake 通过校验")
    void idType_validValues() {
        CoreProperties props = new CoreProperties();
        props.getTrace().setIdType("uuid");
        assertTrue(validator.validate(props).isEmpty());

        props.getTrace().setIdType("snowflake");
        assertTrue(validator.validate(props).isEmpty());
    }

    @Test
    @DisplayName("默认值正确")
    void defaults() {
        CoreProperties props = new CoreProperties();
        assertEquals(1000, props.getMaxPageSize());
        assertEquals(20, props.getDefaultPageSize());
        assertTrue(props.getTrace().isEnabled());
        assertTrue(props.getTrace().isGenerateIfMissing());
        assertEquals("uuid", props.getTrace().getIdType());
    }

    @Test
    @DisplayName("TraceConfig 默认值正确")
    void traceDefaults() {
        CoreProperties.TraceConfig trace = new CoreProperties.TraceConfig();
        assertTrue(trace.isEnabled());
        assertTrue(trace.isGenerateIfMissing());
        assertEquals("uuid", trace.getIdType());
    }
}
