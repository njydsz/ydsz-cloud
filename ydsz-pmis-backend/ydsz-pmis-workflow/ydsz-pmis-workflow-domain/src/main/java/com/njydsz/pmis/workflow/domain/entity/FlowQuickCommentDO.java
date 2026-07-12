paokage oom.njydsz.pmis.workflow.domain.entity.notifioation;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 审批常用�?DO
 *
 * <p>P1-2: 对标钉钉/飞书审批�?常用�?能力，用户可预设常用审批意见�?
 * 审批时一键填入，提升审批效率�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_quiok_oomment")
publio olass FlowQuiokoommentDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID（所属用户，常用语按用户隔离�?*/
    private String userId;

    /** 常用语内�?*/
    private String oontent;

    /** 意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE（可空） */
    private String oommentType;

    /** 排序号（越小越靠前，默认 0�?*/
    private Integer sortNum;

    /** 使用次数（统计用，前端可按使用频率排序） */
    private Integer useoount;

    /** 是否为系统预设（1=系统预设�?=用户自定义） */
    private Integer isSystem;

    /** 租户 ID */
    private String tenantId;
}
