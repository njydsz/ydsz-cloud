package com.njydsz.pmis.common.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 领域异常体系单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("领域异常体系测试")
class DomainExceptionTest {

    @Test
    @DisplayName("DomainException 应携带错误消息")
    void shouldCarryMessage() {
        DomainException ex = new DomainException("业务规则违反");
        assertEquals("业务规则违反", ex.getMessage());
        assertEquals(DomainException.DEFAULT_ERROR_CODE, ex.getErrorCode());
    }

    @Test
    @DisplayName("DomainException 应携带自定义错误码")
    void shouldCarryErrorCode() {
        DomainException ex = new DomainException("ORDER_DUPLICATE", "订单重复");
        assertEquals("ORDER_DUPLICATE", ex.getErrorCode());
        assertEquals("订单重复", ex.getMessage());
    }

    @Test
    @DisplayName("DomainException 应携带原因异常")
    void shouldCarryCause() {
        Throwable cause = new RuntimeException("底层错误");
        DomainException ex = new DomainException("包装错误", cause);
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("AggregateNotFoundException 应携带聚合根类型和ID")
    void shouldCarryAggregateInfo() {
        AggregateNotFoundException ex = new AggregateNotFoundException("Order", 12345L);
        assertEquals("Order", ex.getAggregateType());
        assertEquals(12345L, ex.getAggregateId());
        assertEquals(AggregateNotFoundException.ERROR_CODE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Order"));
        assertTrue(ex.getMessage().contains("12345"));
    }

    @Test
    @DisplayName("ConcurrencyConflictException 应携带版本号信息")
    void shouldCarryVersionInfo() {
        ConcurrencyConflictException ex = new ConcurrencyConflictException("Order", 1L, 3);
        assertEquals("Order", ex.getAggregateType());
        assertEquals(1L, ex.getAggregateId());
        assertEquals(3, ex.getExpectedVersion());
        assertEquals(ConcurrencyConflictException.ERROR_CODE, ex.getErrorCode());
    }

    @Test
    @DisplayName("AggregateNotFoundException 应为 RuntimeException")
    void shouldBeRuntimeException() {
        assertThrows(RuntimeException.class, () -> {
            throw new AggregateNotFoundException("Test", "id");
        });
    }

    @Test
    @DisplayName("ConcurrencyConflictException 应继承 DomainException")
    void shouldExtendDomainException() {
        ConcurrencyConflictException ex = new ConcurrencyConflictException("Test", "id", 1);
        assertTrue(ex instanceof DomainException);
    }
}
