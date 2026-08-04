package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunity;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目商机 Service
 *
 * <p>管理项目商机（{@code ydsz_project_opportunity}）的录入、跟进、转立项。
 * 商机是销售阶段的"潜在项目",经过跟进/报价/谈判/合同签订后转为正式项目立项。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>跟进</b>：销售跟进记录（沟通/报价/演示/谈判）</li>
 *   <li><b>阶段流转</b>：LEAD → CONTACTED → QUALIFIED → PROPOSAL → NEGOTIATION → WON/LOST</li>
 *   <li><b>转立项</b>：WON 状态可转 {@link ProjectInitiationService} 立项</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectOpportunity 商机实体
 * @see ProjectOpportunityFollowService 商机跟进 Service
 * @see ProjectInitiationService 立项 Service(WON 后转立项)
 */
public interface ProjectOpportunityService {
    ProjectOpportunity getById(String id);
    IPage<ProjectOpportunity> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunity entity);
    boolean updateById(ProjectOpportunity entity);
    boolean removeById(String id);
}
