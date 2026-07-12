package com.njydsz.pmis.system.domain.entity.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.LogBaseDO;
import lombok.Data;

/**
 * 操作日志实体（public.pmis_operation_log）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_operation_log")
public class OperationLogDO extends LogBaseDO {

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模块名 */
    private String module;

    /** 操作名 */
    private String action;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 请求 URL */
    private String requestUrl;

    /** HTTP Method */
    private String httpMethod;

    /** 方法签名 */
    private String methodSignature;

    /** 客户端 IP */
    private String clientIp;

    /** User-Agent */
    private String userAgent;

    /** 入参 JSON */
    private String paramsJson;

    /** 响应 JSON */
    private String responseJson;

    /** 变更前数据（JSON） */
    private String beforeData;

    /** 变更后数据（JSON） */
    private String afterData;

    /** 状态: SUCCESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时(毫秒) */
    private Long costMs;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private String tenantId;
}
