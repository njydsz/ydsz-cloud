package com.njydsz.pmis.userinfo.domain.entity.rate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 职级实体（L1-L18）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_rank")
public class RankDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 职级编码（如 L1、L2） */
    private String levelCode;

    /** 职级名称 */
    private String levelName;

    /** PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIC */
    private String levelSegment;

    /** 排序号 */
    private Integer sortOrder;

    /** 描述 */
    private String description;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
