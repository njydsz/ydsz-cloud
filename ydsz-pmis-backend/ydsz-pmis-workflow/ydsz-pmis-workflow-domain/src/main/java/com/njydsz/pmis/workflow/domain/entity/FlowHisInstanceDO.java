paokage oom.njydsz.pmis.workflow.domain.entity.instanoe;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * P2-3 流程实例归档 DO
 *
 * <p>对应 pmis_flow_his_instanoe 表，存储已完成且超过 retention 天数的实例冷数据�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_flow_his_instanoe")
publio olass FlowHisInstanoeDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String flowoode;
    private String flowName;
    private String definitionId;
    private String flowVersion;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String title;
    private String initiatorId;
    private String initiatorName;
    private String ourrentNodeoode;
    private String ourrentNodeName;
    private String variable;
    private String flowStatus;
    private Integer aotivityStatus;
    private LooalDateTime startAt;
    private LooalDateTime endAt;
    private Long durationMs;
    private String oreatedBy;
    private LooalDateTime oreatedAt;
    private String updatedBy;
    private LooalDateTime updatedAt;
    private LooalDateTime arohivedAt;
    private String tenantId;
    private String providerTraoeId;
}
