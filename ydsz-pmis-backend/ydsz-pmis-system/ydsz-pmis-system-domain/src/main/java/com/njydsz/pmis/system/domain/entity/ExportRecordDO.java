package com.njydsz.pmis.system.domain.entity.audit;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 异步导出记录实体（下载中心 + 报表订阅分发）
 *
 * <p>P0-3 合并：原 pmis_report_export_record 已并入本表，通过 {@link #source} 区分：
 * <ul>
 *   <li>MANUAL —— 用户在下载中心主动提交（userId 必填，subscriptionId 为空）</li>
 *   <li>SUBSCRIPTION —— 报表订阅 cron 触发（subscriptionId 必填，userId 可空 = 订阅人）</li>
 * </ul>
 *
 * <p>状态流转：PENDING → GENERATING → COMPLETED / SENT / FAILED / EXPIRED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_export_record")
public class ExportRecordDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 来源：MANUAL 用户主动提交 / SUBSCRIPTION 订阅触发 */
    private String source;

    /** 申请人用户 ID（MANUAL 必填，SUBSCRIPTION 取订阅人） */
    private String userId;

    /** 通用导出类型（MANUAL 主用，如 INITIATION_LIST、INVOICE_REPORT） */
    private String exportType;

    /** 报表类型（SUBSCRIPTION 主用，如 COCKPIT / EVM / PROFIT） */
    private String reportType;

    /** 关联订阅 ID（仅 SUBSCRIPTION 来源有值） */
    private String subscriptionId;

    /** 文件名 */
    private String fileName;

    /** MinIO 文件 key */
    private String fileKey;

    /** 下载 URL */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 状态：PENDING/GENERATING/COMPLETED/SENT/FAILED/EXPIRED */
    private String status;

    /** 导出参数 JSON */
    private String params;

    /** 错误信息 */
    private String errorMessage;

    /** 供应商侧追踪 ID */
    private String providerTraceId;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 过期时间（过期自动清理） */
    private LocalDateTime expiredAt;

    /** 乐观锁版本号 */
    private Integer version;
}
