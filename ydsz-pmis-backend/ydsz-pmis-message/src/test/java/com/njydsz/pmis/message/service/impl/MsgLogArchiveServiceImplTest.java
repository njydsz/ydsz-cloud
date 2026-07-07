package com.njydsz.pmis.message.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MsgLogArchiveServiceImpl} 单元测试。
 *
 * <p>覆盖：分区不存在跳过、DETACH+RENAME 成功、归档表已存在跳过、DETACH 失败跳过、
 * ensurePartition 已存在跳过 / 新建成功、scheduledArchive 触发归档+预创建。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MsgLogArchiveServiceImpl 归档服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MsgLogArchiveServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MsgLogArchiveServiceImpl service;

    /** 模拟 PG 版本查询返回 14（支持 CONCURRENTLY） */
    @BeforeEach
    void setUp() {
        when(jdbcTemplate.queryForObject(eq("SELECT split_part(version(), ' ', 2)::int"), eq(Integer.class)))
                .thenReturn(14);
    }

    @Test
    @DisplayName("archive: 所有分区都不存在,返回空列表")
    void shouldReturnEmptyWhenNoPartitionExists() {
        when(jdbcTemplate.queryForObject(
                anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);
        List<String> result = service.archive(2026, 4);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("archive: 分区存在且 DETACH/RENAME 成功,返回归档表名")
    void shouldArchiveExistingPartitionSuccessfully() {
        // 2026-04 - 3 = 2026-01
        // 第 1 次查询 partitionExists("pmis_msg_log_y2026m01") -> true
        // 第 2 次查询 archiveTableExists("pmis_msg_log_archive_202601") -> false
        // 第 3 次查询 partitionExists("pmis_msg_log_y2025m12") -> false (跳出循环或继续)
        // 之后所有 partitionExists 都返回 false
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        List<String> result = service.archive(2026, 4);

        assertEquals(1, result.size());
        assertEquals("pmis_msg_log_archive_202601", result.get(0));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("DETACH PARTITION"));
        verify(jdbcTemplate, atLeastOnce()).execute(contains("RENAME TO pmis_msg_log_archive_202601"));
    }

    @Test
    @DisplayName("archive: 归档表已存在,跳过 DETACH")
    void shouldSkipDetachWhenArchiveTableExists() {
        // partition exists=1, archive exists=1
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        List<String> result = service.archive(2026, 4);

        assertTrue(result.isEmpty());
        // 不应该执行 DETACH
        verify(jdbcTemplate, never()).execute(contains("DETACH PARTITION"));
    }

    @Test
    @DisplayName("archive: DETACH 失败,跳过 RENAME")
    void shouldSkipRenameWhenDetachFails() {
        // partition exists=1, archive exists=0
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        // DETACH 抛异常
        doThrow(new RuntimeException("partition not found"))
                .when(jdbcTemplate).execute(contains("DETACH PARTITION"));

        List<String> result = service.archive(2026, 4);

        assertTrue(result.isEmpty());
        // 不应该执行 RENAME
        verify(jdbcTemplate, never()).execute(contains("RENAME TO"));
    }

    @Test
    @DisplayName("archive: RENAME 失败(DuplicateKey),不中断后续流程")
    void shouldHandleRenameDuplicateKeyException() {
        // 不实际触发,因为该异常是 Spring 转译后的;这里用 RuntimeException 模拟
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        // DETACH 成功(doNothing), RENAME 抛 DuplicateKeyException
        doNothing().when(jdbcTemplate).execute(contains("DETACH PARTITION"));
        doThrow(new DuplicateKeyException("archive table already exists"))
                .when(jdbcTemplate).execute(contains("RENAME TO"));

        List<String> result = service.archive(2026, 4);

        // DuplicateKeyException 不计入 archived 列表
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("ensurePartition: 分区已存在,返回 null")
    void shouldReturnNullWhenPartitionExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(1);

        String result = service.ensurePartition(2026, 5);

        assertNull(result);
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("ensurePartition: 分区不存在,创建成功,返回分区名")
    void shouldCreatePartitionWhenNotExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);

        String result = service.ensurePartition(2026, 5);

        assertNotNull(result);
        assertEquals("pmis_msg_log_y2026m05", result);
        verify(jdbcTemplate, times(1)).execute(contains(
                "CREATE TABLE IF NOT EXISTS pmis_msg_log_y2026m05 PARTITION OF pmis_msg_log"));
    }

    @Test
    @DisplayName("ensurePartition: 跨年处理(2026-12 -> 2027-01 区间)")
    void shouldHandleYearBoundaryWhenCreatePartition() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);

        String result = service.ensurePartition(2026, 12);

        assertNotNull(result);
        assertEquals("pmis_msg_log_y2026m12", result);
        // 验证 SQL 包含正确的边界
        verify(jdbcTemplate, times(1)).execute(contains(
                "FROM ('2026-12-01') TO ('2027-01-01')"));
    }

    @Test
    @DisplayName("scheduledArchive: 触发归档 + 预创建分区")
    void shouldRunArchiveAndEnsureOnSchedule() {
        // 所有 partitionExists 返回 0 -> archive 返回空, ensurePartition 创建新分区
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenReturn(0);

        service.scheduledArchive();

        // 应该至少调用过一次 execute（ensurePartition 触发 CREATE）
        verify(jdbcTemplate, atLeastOnce()).execute(contains("CREATE TABLE"));
    }
}
