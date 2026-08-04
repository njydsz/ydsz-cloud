package com.njydsz.project.domain.entity.project;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机实体
 *
 * <p>对应数据库表 {@code ydsz_project_opportunity}，存储项目商机（Sales Opportunity）信息。
 * 商机是项目立项的前置阶段，记录潜在客户、预计金额、预计签约日期等销售线索数据，
 * 商机转化后通过立项流程创建 {@link ProjectInitiation} 记录。
 *
 * <p><b>商机生命周期：</b>
 * <ul>
 *   <li>OPEN（开放） — 商机初始状态，销售跟进中</li>
 *   <li>WON（赢单） — 商机赢单，转为立项申请</li>
 *   <li>LOST（输单） — 商机输单，记录输单原因</li>
 * </ul>
 *
 * <p><b>关联关系：</b>
 * <ul>
 *   <li>商机 → 项目立项（{@code 1:0..1}）：赢单后创建一条立项记录</li>
 *   <li>商机 → 跟进记录（{@code 1:N}）：通过 {@link ProjectOpportunityFollow} 记录每次跟进</li>
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 * <ul>
 *   <li>CRM 商机管理列表 / 详情</li>
 *   <li>销售漏斗分析（按 stage 分组统计）</li>
 *   <li>赢单转立项（触发立项审批流程）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInitiation 项目立项实体（商机转化后创建）
 * @see ProjectOpportunityFollow 商机跟进记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_opportunity")
public class ProjectOpportunity extends MpBaseEntity<String> {


}
