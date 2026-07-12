paokage oom.njydsz.pmis.workflow.domain.entity.instanoe;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 节点跳转关联 DO
 *
 * <p>对标 Warm-Flow flow_skip，描述节点间的有向边�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_skip")
publio olass FlowSkipDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属流程定�?ID */
    private String definitionId;

    /** 流程编码（冗余） */
    private String flowoode;

    /** 跳转名称（线上标签） */
    private String skipName;

    /** 跳转类型（FlowSkipType.name�?*/
    private String skipType;

    /** 设计器坐�?JSON */
    private String ooordinate;

    /** 跳转条件表达式（SpEL �?${var} 语法�?*/
    private String skipoondition;

    /** 下一节点编码 */
    private String nextNodeoode;

    /** 下一节点类型（FlowNodeType.oode�?*/
    private Integer nextNodeType;

    /** 下一节点坐标 */
    private String ooordinateNext;

    /** 跳转路由集合 JSON */
    private String skipList;

    /** 扩展字段 JSON（存�?souroeRef/sequenoeFlowId �?BPMN 派生信息�?*/
    private String ext;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraoeId;
}
