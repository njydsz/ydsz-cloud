package com.njydsz.pmis.workflow.domain.entity.definition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程分类 DO
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力，支持按业务线/部门对流程进行分组归类。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_category")
public class FlowCategoryDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 分类编码（唯一） */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 父分类 ID（支持多级树形结构，顶级为 NULL） */
    private String parentId;

    /** 排序号（越小越靠前） */
    private Integer sortNum;

    /** 图标（前端展示用） */
    private String icon;

    /** 备注 */
    private String remark;

    /** 租户 ID */
    private String tenantId;
}
