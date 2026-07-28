package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectGateReview;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目门审 Service
 *
 * <p>管理项目门审（{@code ydsz_project_gate_review}）的发起、评审、决策。
 * 门审（Stage-Gate）是将项目生命周期划分为多个阶段（启动/规划/执行/收尾）,
 * 每个阶段结束设置评审门,通过则进入下一阶段,否则需返工。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>门审流程</b>：发起 → 资料准备 → 评审会 → 决策(通过/有条件通过/驳回)</li>
 *   <li><b>阶段推进</b>：门审通过后项目自动进入下一阶段</li>
 * </ul>
 *
 * <p><b>门审阶段：</b>START(启动) / PLANNING(规划) / EXECUTION(执行) / CLOSURE(收尾)。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectGateReview 门审实体
 * @see ProjectInitiationService 立项 Service(门审通过后项目阶段推进)
 */
public interface ProjectGateReviewService {
    ProjectGateReview getById(String id);
    IPage<ProjectGateReview> page(int pageNum, int pageSize);
    boolean save(ProjectGateReview entity);
    boolean updateById(ProjectGateReview entity);
    boolean removeById(String id);
}
