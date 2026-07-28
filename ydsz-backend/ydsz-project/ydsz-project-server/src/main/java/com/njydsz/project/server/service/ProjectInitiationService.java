package com.njydsz.project.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.dto.ProjectInitiationPageQuery;
import com.njydsz.project.domain.vo.ProjectInitiationVO;

/**
 * 项目立项 Application Service。
 *
 * <p>提供项目立项全生命周期管理能力，包括 CRUD、阶段推进、门审等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ProjectInitiationService {

    /**
     * 按 ID 查询项目立项。
     *
     * @param id 主键 ID
     * @return 项目立项 VO
     */
    ProjectInitiationVO getById(String id);

    /**
     * 按项目编号查询。
     *
     * @param projectCode 项目编号
     * @return 项目立项 VO
     */
    ProjectInitiationVO getByCode(String projectCode);

    /**
     * 分页查询项目立项。
     *
     * @param query 分页查询条件
     * @return 分页结果
     */
    IPage<ProjectInitiationVO> page(ProjectInitiationPageQuery query);

    /**
     * 创建项目立项。
     *
     * @param dto 项目立项 DTO
     * @return 主键 ID
     */
    String save(ProjectInitiation entity);

    /**
     * 更新项目立项。
     *
     * @param dto 项目立项 DTO
     * @return 是否成功
     */
    boolean updateById(ProjectInitiation entity);

    /**
     * 删除项目立项（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);

    /**
     * 推进项目阶段。
     *
     * @param id      项目 ID
     * @param stage   目标阶段
     * @param gate    门审阶段（可选）
     * @return 是否成功
     */
    boolean advanceStage(String id, String stage, String gate);

    /**
     * 查询项目经理负责的项目列表。
     *
     * @param pmId 项目经理 ID
     * @return 项目立项 VO 列表
     */
    List<ProjectInitiationVO> listByPmId(String pmId);

    /**
     * 同步工作流审批状态到立项状态。
     *
     * <p>由 {@code FlowEventQueueSubscriber} 消费 workflow 模块发布的
     * {@code INITIATION_STATUS_SYNC} 事件后调用，实现 project↔workflow 联动闭环。
     *
     * <p><b>状态映射：</b>
     * <ul>
     *   <li>{@code markProcessing} → status = "PROCESSING"（审批中）</li>
     *   <li>{@code markApproved} → status = "APPROVED", stage = "INITIATION"（审批通过，推进到立项阶段）</li>
     *   <li>{@code markRejected} → status = "REJECTED"（审批驳回）</li>
     * </ul>
     *
     * @param id     立项主键
     * @param action 工作流动作（markProcessing / markApproved / markRejected）
     * @return true=同步成功，false=立项不存在
     * @since 1.0.0
     */
    boolean syncWorkflowStatus(String id, String action);
}
