package com.njydsz.pmis.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批常用语 DO
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力，用户可预设常用审批意见，
 * 审批时一键填入，提升审批效率。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_quick_comment")
public class FlowQuickCommentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID（所属用户，常用语按用户隔离） */
    private String userId;

    /** 常用语内容 */
    private String content;

    /** 意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE（可空） */
    private String commentType;

    /** 排序号（越小越靠前，默认 0） */
    private Integer sortNum;

    /** 使用次数（统计用，前端可按使用频率排序） */
    private Integer useCount;

    /** 是否为系统预设（1=系统预设，0=用户自定义） */
    private Integer isSystem;

    /** 租户 ID */
    private String tenantId;
}
