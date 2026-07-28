package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectChange;
import com.njydsz.project.domain.repository.project.IProjectChangeRepository;
import com.njydsz.project.server.service.ProjectChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目变更申请 Service 实现
 *
 * <p>对 {@link ProjectChangeService} 接口的完整实现，是「项目管理 / 项目变更管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_change} 项目变更申请表，
 * 对标大厂 PMIS / 项目管理系统中的「项目变更 / 项目调整 / 项目变更申请」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>项目范围 / 工期 / 预算变更</b>：执行中的项目发生范围 / 工期 / 预算调整时，
 *       通过本 Service 提交变更申请，走审批流</li>
 *   <li><b>变更类型</b>：支持多种变更类型（{@code SCOPE} 范围 / {@code SCHEDULE} 工期 /
 *       {@code BUDGET} 预算 / {@code RESOURCE} 资源 / {@code OTHER} 其他）</li>
 *   <li><b>审批集成</b>：每条变更申请对应一个 {@code ydsz-workflow} 流程实例，
 *       审批通过后自动同步到立项的对应字段</li>
 *   <li><b>审计链</b>：保留完整变更历史（变更前 / 变更后 / 变更原因 / 影响评估 / 变更人 / 变更时间），
 *       便于合规审计和复盘</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>变更审批通过后同步立项字段需与立项 Service 共享同一事务</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>影响评估必填</b>：所有变更申请<b>必须</b>填写 {@code impactAssessment}（对工期 / 成本 / 质量的影响），
 *       由 {@code ydsz-workflow} 流程引擎校验</li>
 *   <li><b>与合同变更区别</b>：本表是项目内部的「项目变更」（PM 可发起），
 *       合同变更走 {@link com.njydsz.project.server.service.impl.ProjectContractChangeServiceImpl}
 *       （客户 / 法务可发起）</li>
 *   <li><b>不可篡改</b>：变更申请一旦审批通过，<b>严禁</b>直接 update 变更前后字段</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       变更记录是合规审计的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. PM 提交项目工期变更
 * ProjectChange change = new ProjectChange();
 * change.setInitiationId("project_123");
 * change.setChangeType("SCHEDULE");
 * change.setBeforeValue("2026-12-31");
 * change.setAfterValue("2027-03-31");
 * change.setChangeReason("客户需求追加");
 * change.setImpactAssessment("工期延长 3 个月，成本增加 50 万");
 * change.setStatus("PENDING");
 * projectChangeService.save(change);
 *
 * // 2. 审批通过后自动同步立项工期
 * // 由 ydsz-workflow 流程实例审批通过事件回调触发
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectChangeService 项目变更 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectChange 项目变更实体
 * @see com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl 立项 Service（被同步）
 * @see com.njydsz.project.server.service.impl.ProjectContractChangeServiceImpl 合同变更（区别：本表是项目内部变更）
 */
@Service
@RequiredArgsConstructor
public class ProjectChangeServiceImpl implements ProjectChangeService {

    /** 项目变更仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectChangeRepository repository;

    /**
     * 根据主键查询项目变更
     *
     * @param id 项目变更主键
     * @return 项目变更实体，不存在返回 null
     */
    @Override
    public ProjectChange getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询项目变更
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code changeType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectChange> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增项目变更申请
     *
     * <p>新增后应触发 {@code ProjectChangeCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动变更审批流。
     *
     * @param change 项目变更实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectChange change) {
        return repository.save(change);
    }

    /**
     * 更新项目变更
     *
     * <p><b>注意：</b>仅在审批中（{@code status=PENDING}）的变更可修改，
     * 审批通过后（{@code status=APPROVED}）的变更<b>严禁</b>修改。
     *
     * @param change 项目变更实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectChange change) {
        return repository.updateById(change);
    }

    /**
     * 逻辑删除项目变更
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>项目变更记录是合规审计的法定依据，<b>严禁</b>物理删除，
     * 仅在录入错误等极端情况下使用本方法。
     *
     * @param id 项目变更主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
