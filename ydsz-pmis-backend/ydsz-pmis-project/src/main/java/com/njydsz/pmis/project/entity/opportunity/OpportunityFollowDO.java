package com.njydsz.pmis.project.entity.opportunity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机跟进记录
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_opportunity_follow")
public class OpportunityFollowDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 商机 ID */
    private String opportunityId;
    /** 跟进类型（VISIT/CALL/QUOTE/NEGOTIATE/OTHER） */
    private String followType;
    /** 跟进时间 */
    private LocalDateTime followAt;
    /** 跟进人 ID */
    private String followerId;
    /** 跟进人名称 */
    private String followerName;
    /** 跟进内容 */
    private String content;
    /** 下一步动作 */
    private String nextStep;
    /** 下次跟进日期 */
    private LocalDate nextFollowDate;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
