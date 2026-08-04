package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目变更记录实体。
 *
 * <p>对应数据库表 {@code ydsz_project_change}，记录项目执行过程中的变更历史。
 * 包括范围变更、计划变更、预算调整等，与工作流审批联动确保变更可追溯。
 *
 * <p><b>变更类型：</b>
 * <ul>
 *   <li>SCOPE：项目范围变更</li>
 *   <li>SCHEDULE：项目计划变更</li>
 *   <li>BUDGET：预算调整</li>
 *   <li>RESOURCE：资源调配</li>
 * </ul>
 *
 * <p><b>业务约束：</b>变更需经过审批流程方可生效，
 * 通过 {@code changeStatus} 字段跟踪审批状态（DRAFT / PENDING / APPROVED / REJECTED）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_change")
public class ProjectChange extends MpBaseEntity<String> {


}
