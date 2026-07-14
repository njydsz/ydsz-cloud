package com.njydsz.pmis.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.custom.BusinessException;

@DisplayName("BusinessException Test")
class BusinessExceptionTest {
    @Test
    void testCreateWithCode() {
        BusinessException ex = new BusinessException(UnifiedExceptionCode.BIZ_ERROR, "test error");
        assertEquals("B10103", ex.getCode());
        assertEquals("test error", ex.getMessage());
    }
    @Test
    void testCreateWithNullMessage() {
        BusinessException ex = new BusinessException(UnifiedExceptionCode.BIZ_ERROR, null);
        assertNotNull(ex);
        assertEquals(UnifiedExceptionCode.BIZ_ERROR.getCode(), ex.getCode());
    }
    @Test
    void testGetHttpStatus() {
        BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND, "not found");
        assertEquals(404, ex.getHttpStatus());
    }
}
