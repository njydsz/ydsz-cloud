package com.njydsz.pmis.system.entity.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.LogBaseDO;
import com.njydsz.pmis.common.sensitive.Sensitive;
import com.njydsz.pmis.common.sensitive.SensitiveStrategy;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感操作二次确认
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_sensitive_operation")
public class SensitiveOperationDO extends LogBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;
    /** 用户名 */
    private String username;
    /** 敏感操作编码 */
    private String operationCode;
    /** 敏感操作名称 */
    private String operationName;
    /** 业务类型 */
    private String bizType;
    /** 业务单据 ID */
    private String bizId;
    /** 二次认证方式 */
    private String reAuthMethod;
    /** 二次认证令牌 */
    private String reAuthToken;
    /** 验证时间 */
    private LocalDateTime verifiedAt;
    /** 过期时间 */
    private LocalDateTime expireAt;
    /** 客户端 IP（脱敏：保留前 3 段） */
    @Sensitive(SensitiveStrategy.ADDRESS)
    private String clientIp;
    /** 链路追踪 ID */
    private String traceId;
    /** 租户 ID */
    private String tenantId;
}
