package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程表单定义实体
 *
 * <p>用于保存流程启动/审批时所需的表单 Schema。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_workflow_form")
public class WorkflowFormDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 表单 KEY（业务唯一） */
    private String formKey;

    /** 表单名称 */
    private String formName;

    /** 对应流程定义 KEY */
    private String processKey;

    /** 业务类型 */
    private String businessType;

    /** 表单 Schema (JSON) */
    private String schemaJson;

    /** 版本号 */
    private Integer version;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 描述 */
    private String description;

    /** 租户 ID */
    private Long tenantId;
}
