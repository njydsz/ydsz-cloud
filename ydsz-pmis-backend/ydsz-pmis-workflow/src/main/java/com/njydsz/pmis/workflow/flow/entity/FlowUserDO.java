package com.njydsz.pmis.workflow.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 流程用户 DO
 *
 * <p>对标 Warm-Flow flow_user，会签多办理人扩展。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_user")
public class FlowUserDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long instanceId;
    private String nodeCode;

    /** 用户类型：USER/ROLE/DEPT */
    private String userType;

    private String userId;
    private String userName;

    /** 是否已处理：0 否 / 1 是 */
    private Integer processed;
    private LocalDateTime processAt;
    private String comment;
    private Long tenantId;
    private String providerTraceId;
}
