package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门径评审记录实体。
 *
 * <p>对应数据库表 {@code ydsz_project_gate_review}，记录项目门径（Stage-Gate）评审全过程。
 * 门径评审是项目管理方法论中"阶段-门"模型的具体实现，
 * 每个门（Gate）对应一个评审节点，评审通过后方可进入下一阶段。
 *
 * <p><b>评审流程：</b>
 * <ul>
 *   <li>Gate 0：立项评审 — 概念验证</li>
 *   <li>Gate 1：方案评审 — 详细方案</li>
 *   <li>Gate 2：执行评审 — 开发实施</li>
 *   <li>Gate 3：结项评审 — 验收交付</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项（含 {@code currentGate} 当前门审阶段）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_gate_review")
public class ProjectGateReview extends MpBaseEntity<String> {


}
