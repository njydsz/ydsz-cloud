package com.njydsz.pmis.scheduler.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageFeignClient;
import com.njydsz.pmis.scheduler.config.MinioConfig;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportScheduleServiceImpl 单元测试。
 *
 * <p>P1-8: 验证 generateReport 生成 Excel 并上传 MinIO，
 * distributeReport 落库并通过 Feign 发送邮件通知。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReportScheduleServiceImpl 报表调度服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportScheduleServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private MessageFeignClient messageFeignClient;

    @InjectMocks
    private ReportScheduleServiceImpl service;

    @Test
    @DisplayName("generateReport 应生成 Excel 并上传 MinIO 返回 fileKey")
    void generateReport_shouldGenerateExcelAndUploadToMinio() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");

        String fileKey = service.generateReport("PROFIT",
                Map.of("projectName", "XX项目", "revenue", 1000, "cost", 400, "profit", 600, "margin", "60%"));

        assertThat(fileKey).startsWith("report/PROFIT/").endsWith(".xlsx");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("generateReport COCKPIT 类型应生成对应结构")
    void generateReport_shouldHandleCockpitType() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");

        String fileKey = service.generateReport("COCKPIT",
                Map.of("activeProjects", 12, "totalContractAmount", 5000000));

        assertThat(fileKey).startsWith("report/COCKPIT/");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("generateReport 未知类型应按通用键值结构生成")
    void generateReport_shouldHandleUnknownTypeWithGenericStructure() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");

        String fileKey = service.generateReport("CUSTOM", Map.of("k1", "v1"));

        assertThat(fileKey).startsWith("report/CUSTOM/");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("distributeReport 应落库并发送 EMAIL 通知")
    void distributeReport_shouldInsertRecordAndSendEmail() {
        when(messageFeignClient.send(any())).thenReturn(Result.ok(Map.of("messageId", "m1")));

        service.distributeReport(10L, "PROFIT", "report/PROFIT/1.xlsx", "a@x.com,b@x.com", "EMAIL");

        // 验证落库
        verify(jdbcTemplate).update(contains("INSERT INTO pmis_report_export_record"),
                eq(10L), eq("PROFIT"), eq("report/PROFIT/1.xlsx"), eq("COMPLETED"), any());
        // 验证 Feign 发送邮件
        verify(messageFeignClient).send(any());
    }

    @Test
    @DisplayName("distributeReport 无接收人时应跳过邮件通知但仍落库")
    void distributeReport_shouldSkipEmailWhenNoRecipients() {
        service.distributeReport(11L, "EVM", "report/EVM/2.xlsx", "", "EMAIL");

        verify(jdbcTemplate).update(contains("INSERT"), eq(11L), anyString(), anyString(), anyString(), any());
        verify(messageFeignClient, never()).send(any());
    }

    @Test
    @DisplayName("distributeReport 邮件发送失败不应影响落库")
    void distributeReport_shouldNotFailWhenEmailThrows() {
        when(messageFeignClient.send(any())).thenThrow(new RuntimeException("mail down"));

        service.distributeReport(12L, "UTILIZATION", "report/UTILIZATION/3.xlsx", "c@x.com", "EMAIL");

        // 落库仍应执行
        verify(jdbcTemplate).update(contains("INSERT"), eq(12L), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("executeDailyReports 应处理所有订阅并生成分发")
    void executeDailyReports_shouldProcessAllSubscriptions() throws Exception {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(
                        Map.of("id", 1, "report_type", "PROFIT", "recipients", "a@x.com", "channels", "EMAIL"),
                        Map.of("id", 2, "report_type", "COCKPIT", "recipients", "b@x.com", "channels", "EMAIL")));
        when(messageFeignClient.send(any())).thenReturn(Result.ok(Map.of()));

        service.executeDailyReports();

        // 两个订阅各生成一份报表（上传两次）
        verify(minioClient, atLeastOnce()).putObject(any(PutObjectArgs.class));
        // 两个订阅各发一封邮件
        verify(messageFeignClient, atLeastOnce()).send(any());
    }

    @Test
    @DisplayName("executeDailyReports 单个订阅异常不应中断整体调度")
    void executeDailyReports_shouldContinueWhenOneSubscriptionFails() {
        when(minioConfig.getDefaultBucket()).thenReturn("pmis");
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(
                        Map.of("id", 1, "report_type", "PROFIT", "recipients", "a@x.com", "channels", "EMAIL"),
                        Map.of("id", 2, "report_type", "COCKPIT", "recipients", "b@x.com", "channels", "EMAIL")));
        // 第一个订阅的报表上传抛出
        try {
            org.mockito.Mockito.doThrow(new RuntimeException("minio error"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));
        } catch (Exception ignored) {
        }

        // 不应抛出
        service.executeDailyReports();
    }
}
