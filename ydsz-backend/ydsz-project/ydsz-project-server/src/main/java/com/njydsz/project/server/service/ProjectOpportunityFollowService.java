package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 商机跟进记录 Service
 *
 * <p>管理商机的跟进记录（{@code ydsz_project_opportunity_follow}）——销售与客户的
 * 每次沟通/报价/演示/谈判的过程记录,用于：
 * <ul>
 *   <li>复盘"为什么赢/为什么输"</li>
 *   <li>团队知识沉淀</li>
 *   <li>领导查阅商机进展</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>跟进类型</b>：PHONE / EMAIL / MEETING / DEMO / QUOTE / NEGOTIATION</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectOpportunityFollow 跟进记录实体
 * @see ProjectOpportunityService 商机 Service(1:N 关联)
 */
public interface ProjectOpportunityFollowService {
    ProjectOpportunityFollow getById(String id);
    IPage<ProjectOpportunityFollow> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunityFollow entity);
    boolean updateById(ProjectOpportunityFollow entity);
    boolean removeById(String id);
}
