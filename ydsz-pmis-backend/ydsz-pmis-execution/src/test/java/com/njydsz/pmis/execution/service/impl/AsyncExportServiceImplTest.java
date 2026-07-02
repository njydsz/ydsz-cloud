package com.njydsz.pmis.execution.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AsyncExportServiceImpl 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AsyncExportServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AsyncExportServiceImpl service;

    @Test
    void submitExport_shouldInsertRecordAndReturnId() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong(), anyString()))
                .thenReturn(1L);
        Long id = service.submitExport(1L, "PROJECT", Map.of("status", "ACTIVE"));
        assertThat(id).isEqualTo(1L);
        verify(jdbcTemplate).update(anyString(), anyLong(), anyString(), anyString(), eq("PENDING"), any(), any());
    }

    @Test
    void getExportRecords_shouldReturnEmptyWhenNoRecords() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(0L);
        var result = service.getExportRecords(1L, PageRequest.of(0, 10));
        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getExportRecords_shouldReturnRecords() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(Map.of("id", 1, "status", "COMPLETED")));
        var result = service.getExportRecords(1L, PageRequest.of(0, 10));
        assertThat(result).hasSize(1);
    }

    @Test
    void getDownloadUrl_shouldReturnUrlWhenCompleted() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong()))
                .thenReturn("/download/file.xlsx");
        String url = service.getDownloadUrl(1L);
        assertThat(url).isEqualTo("/download/file.xlsx");
    }

    @Test
    void getDownloadUrl_shouldReturnNullWhenNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), anyLong()))
                .thenThrow(new RuntimeException("not found"));
        String url = service.getDownloadUrl(999L);
        assertThat(url).isNull();
    }

    @Test
    void deleteExportRecord_shouldUpdateDeletedFlag() {
        service.deleteExportRecord(1L);
        verify(jdbcTemplate).update(contains("SET deleted"), eq(1L));
    }

    @Test
    void executeExport_shouldMarkCompletedOnSuccess() {
        when(jdbcTemplate.queryForMap(anyString(), anyLong()))
                .thenReturn(Map.of("export_type", "PROJECT"));
        service.executeExport(1L);
        verify(jdbcTemplate).update(contains("COMPLETED"), anyString(), any(), eq(1L));
    }

    @Test
    void executeExport_shouldMarkFailedOnError() {
        when(jdbcTemplate.queryForMap(anyString(), anyLong()))
                .thenThrow(new RuntimeException("DB error"));
        service.executeExport(1L);
        verify(jdbcTemplate).update(contains("FAILED"), anyString(), any(), eq(1L));
    }
}
