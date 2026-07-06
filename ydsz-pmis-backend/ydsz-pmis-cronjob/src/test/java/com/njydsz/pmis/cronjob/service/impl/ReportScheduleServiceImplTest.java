package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageFeignClient;
import com.njydsz.pmis.cronjob.config.MinioConfig;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ReportScheduleServiceImpl 单元测试
 *
 * <p>P0-3 合并后聚焦验证：
 * <ul>
 *   <li>distributeReport 写入 pmis_export_record，source='SUBSCRIPTION'</li>
 *   <li>subscription_id / report_type / user_id(=subscriber_id) 正确回填</li>
 *   <li>resolveSubscriberId 异常时仍能完成落库（user_id=null）</li>
 *   <li>邮件通知通过 Feign 调用，调用失败不影响主流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportScheduleServiceImpl 单元测试（P0-3 合并后）")
class ReportScheduleServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    @Mock
    private MessageFeignClient messageFeignClient;

    private ReportScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportScheduleServiceImpl(jdbcTemplate, minioClient, minioConfig, messageFeignClient);
    }

    // ==================== distributeReport ====================

    @Test
    @DisplayName("distributeReport - 订阅报表分发，写入 pmis_export_record 并 source=SUBSCRIPTION")
    void distributeReport_shouldInsertToExportRecordWithSubscriptionSource() {
        // given
        Long subId = 100L;
        String reportType = "EVM";
        String fileKey = "report/EVM/20260706.xlsx";
        String recipients = "alice@example.com,bob@example.com";
        String channels = "EMAIL";
        Long subscriberId = 200L;

        when(jdbcTemplate.queryForObject(
                eq("SELECT subscriber_id FROM pmis_report_subscription WHERE id = ?"),
                eq(Long.class), eq(subId))).thenReturn(subscriberId);

        // when
        service.distributeReport(subId, reportType, fileKey, recipients, channels);

        // then: 落库 SQL 与参数完全正确
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        String sql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        // 验证 SQL 关键字
        assertTrue(sql.contains("INSERT INTO pmis_export_record"), "应写入 pmis_export_record");
        assertTrue(sql.contains("source"), "应包含 source 列");
        assertTrue(sql.contains("subscription_id"), "应包含 subscription_id 列");
        assertTrue(sql.contains("report_type"), "应包含 report_type 列");
        assertTrue(sql.contains("completed_at"), "应包含 completed_at（P0-3 修正 generated_at 错位）");

        // 验证参数顺序：(tenantId, source, userId, exportType, reportType, subscriptionId, fileKey, fileUrl, fileSize, status, completedAt)
        assertEquals(11, args.length, "应绑定 11 个参数");
        assertEquals(1L, args[0], "tenant_id");
        assertEquals("SUBSCRIPTION", args[1], "source=SUBSCRIPTION");
        assertEquals(subscriberId, args[2], "user_id=订阅人 ID");
        assertEquals("SUBSCRIPTION_REPORT", args[3], "export_type 固定为 SUBSCRIPTION_REPORT");
        assertEquals(reportType, args[4], "report_type=订阅报表类型");
        assertEquals(subId, args[5], "subscription_id");
        assertEquals(fileKey, args[6], "file_key");
        assertEquals(fileKey, args[7], "file_url=file_key（P0-3 同步旧逻辑）");
        assertNull(args[8], "file_size=NULL");
        assertEquals("COMPLETED", args[9], "status=COMPLETED（文件已生成）");
        assertNotNull(args[10], "completed_at 已设置");
    }

    @Test
    @DisplayName("distributeReport - 订阅人不存在时 user_id=null，主流程不中断")
    void distributeReport_shouldHandleMissingSubscriberGracefully() {
        // given: 订阅查询抛出异常
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenThrow(new EmptyResultDataAccessException(1));

        // when
        assertDoesNotThrow(() -> service.distributeReport(999L, "PROFIT",
                "report/PROFIT/x.xlsx", "x@example.com", "EMAIL"));

        // then: 仍然完成 INSERT
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertNull(args[2], "订阅人解析失败时 user_id=null");
        assertEquals("SUBSCRIPTION", args[1], "source 仍为 SUBSCRIPTION");
    }

    @Test
    @DisplayName("distributeReport - 邮件通知调用 Feign，参数中包含 reportType 与 recipients")
    void distributeReport_shouldCallFeignWithCorrectPayload() {
        // given
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(200L);
        Result<Map<String, Object>> ok = Result.ok(Map.of("ok", true));
        when(messageFeignClient.send(any())).thenReturn(ok);

        // when
        service.distributeReport(100L, "COCKPIT", "report/COCKPIT/y.xlsx",
                "user1@example.com,user2@example.com", "EMAIL");

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messageFeignClient).send(payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("EMAIL", payload.get("channel"));
        assertEquals("REPORT", payload.get("bizType"));
        assertEquals("100", payload.get("bizId"));
        assertTrue(payload.get("content").toString().contains("COCKPIT"));
    }

    @Test
    @DisplayName("distributeReport - 邮件 Feign 失败时主流程不中断")
    void distributeReport_shouldNotFailWhenFeignFails() {
        // given
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(200L);
        when(messageFeignClient.send(any())).thenThrow(new RuntimeException("feign down"));

        // when & then: 不应抛异常
        assertDoesNotThrow(() -> service.distributeReport(100L, "EVM",
                "report/EVM/z.xlsx", "x@example.com", "EMAIL"));

        // INSERT 已完成
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("distributeReport - recipients 为空时跳过 Feign 调用")
    void distributeReport_shouldSkipFeignWhenRecipientsEmpty() {
        // given
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(200L);

        // when
        service.distributeReport(100L, "EVM", "report/EVM/w.xlsx", null, "EMAIL");

        // then: 不调用 Feign
        verify(messageFeignClient, never()).send(any());
    }

    // ==================== executeReportsByFrequency 间接覆盖 ====================

    @Test
    @DisplayName("executeDailyReports - 无订阅时静默跳过")
    void executeDailyReports_shouldSilentlySkipWhenNoSubscriptions() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(java.util.List.of());

        assertDoesNotThrow(() -> service.executeDailyReports());
        // 不应触发 INSERT（说明流程在无订阅时直接返回）
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
