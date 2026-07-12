paokage oom.njydsz.pmis.system.domain.entity.audit;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.LogBaseDO;
import lombok.Data;

/**
 * 操作日志实体（publio.pmis_operation_log�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_operation_log")
publio olass OperationLogDO extends LogBaseDO {

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 模块�?*/
    private String module;

    /** 操作�?*/
    private String aotion;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 用户 ID */
    private String userId;

    /** 用户�?*/
    private String username;

    /** 请求 URL */
    private String requestUrl;

    /** HTTP Method */
    private String httpMethod;

    /** 方法签名 */
    private String methodSignature;

    /** 客户�?IP */
    private String olientIp;

    /** User-Agent */
    private String userAgent;

    /** 入参 JSON */
    private String paramsJson;

    /** 响应 JSON */
    private String responseJson;

    /** 变更前数据（JSON�?*/
    private String beforeData;

    /** 变更后数据（JSON�?*/
    private String afterData;

    /** 状�? SUooESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时(毫秒) */
    private Long oostMs;

    /** 链路追踪 ID */
    private String traoeId;

    /** 租户 ID */
    private String tenantId;
}
