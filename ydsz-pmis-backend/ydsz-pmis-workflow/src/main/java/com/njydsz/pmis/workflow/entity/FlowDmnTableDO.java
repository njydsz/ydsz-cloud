package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * DMN 决策表定义 DO
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 * <p>输入列/输出列/规则行以 JSON 字符串存储，运行时由 DmnEngine 反序列化并执行。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_dmn_table")
public class FlowDmnTableDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 决策表唯一标识 */
    private String tableKey;

    /** 决策表名称 */
    private String tableName;

    /** 决策表描述 */
    private String description;

    /** 命中策略: UNIQUE/FIRST/PRIORITY/ANY/COLLECT */
    private String hitPolicy;

    /** COLLECT 聚合运算符: LIST/SUM/MIN/MAX/COUNT */
    private String collectOperator;

    /** 输入列定义(JSON) */
    private String inputsJson;

    /** 输出列定义(JSON) */
    private String outputsJson;

    /** 规则行定义(JSON) */
    private String rulesJson;

    /** 版本号 */
    private Integer version;

    /** 状态: DRAFT/PUBLISHED/DEPRECATED */
    private String status;
}
