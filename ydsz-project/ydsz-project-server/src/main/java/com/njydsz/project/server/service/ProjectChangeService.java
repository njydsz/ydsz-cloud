package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectChange;
/**
 * 项目变更 Service
 *
 * <p>管理项目变更（{@code ydsz_project_change}）的申请与审批。</p>
 * <p>项目变更是项目执行过程中对项目计划/范围/目标的调整，区别于合同变更：</p>
 * <p><ul><li>合同变更：双方合同条款的修改</li><li>项目变更：项目内部范围/计划的调整（不一定涉及合同金额变化）</li></ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>变更分类：范围变更 / 计划变更 / 资源变更</b></li>
 *   <li><b>变更审批：走 workflow 审批流程</b></li>
 * </ul>
 *
 * <p><b>与合同变更区别：</b>合同变更侧重钱/账期，项目变更侧重范围/计划/资源。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectChange 项目变更实体
 * @see ProjectContractChangeService 合同变更 Service(联动关系)
 * @see ProjectInitiationService 立项 Service(变更后项目状态联动)
 */
public interface ProjectChangeService {
    ProjectChange getById(String id);
    IPage<ProjectChange> page(int pageNum, int pageSize);
    boolean save(ProjectChange entity);
    boolean updateById(ProjectChange entity);
    boolean removeById(String id);
}
