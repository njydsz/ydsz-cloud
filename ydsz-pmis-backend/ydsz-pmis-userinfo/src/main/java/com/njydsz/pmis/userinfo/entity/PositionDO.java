package com.njydsz.pmis.userinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 岗位实体
 *
 * <p>部门下的具体岗位定义（如开发工程师 / PM / HRBP），与职级（pmis_job_level）多对一关联。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_position")
public class PositionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位编码（全局唯一） */
    private String positionCode;

    /** 岗位名称 */
    private String positionName;

    /** 所属部门 ID（关联 pmis_department.id） */
    private Long departmentId;

    /** 岗位职级（关联 pmis_job_level.level_code） */
    private String levelCode;

    /** 岗位职责说明 */
    private String description;

    /** 启用状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 租户 ID（单租户部署默认 1） */
    private Long tenantId;
}
