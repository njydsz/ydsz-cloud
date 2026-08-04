package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.domain.repository.project.IProjectContractChangeRepository;
import com.njydsz.project.server.service.ProjectContractChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目合同变更 Service 实现
 *
 * <p>对 {@link ProjectContractChangeService} 接口的完整实现，是「项目管理 / 合同变更」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_contract_change} 合同变更表，对标大厂 PMIS / 法务系统中的「合同变更 / 合同修改」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>合同变更管理</b>：已签订合同（{@code status=SIGNED}）的关键字段（金额 / 范围 / 工期 / 交付物）变更
 *       必须走变更流程，<b>禁止</b>直接 update 原合同</li>
 *   <li><b>变更审批流</b>：每条变更记录对应一个审批流（{@code ydsz_flow_instance}），
 *       审批通过后自动同步到原合同</li>
 *   <li><b>审计链</b>：保留完整变更历史（变更前 / 变更后 / 变更原因 / 变更人 / 变更时间），
 *       便于合规审计和争议追溯</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>变更审批通过后同步原合同字段需与原 Service 共享同一事务</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>不可篡改</b>：合同变更记录一旦审批通过，<b>严禁</b>直接 update 变更前后字段，
 *       错误变更应通过「再变更」流程纠正</li>
 *   <li><b>关联原合同</b>：通过 {@code contractId} 关联 {@code ydsz_project_contract.id}，
 *       一个合同可有多次变更记录</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       变更记录是合规审计的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建合同变更申请
 * ProjectContractChange change = new ProjectContractChange();
 * change.setContractId("contract_123");
 * change.setChangeNo("CHG-2026-001");
 * change.setChangeType("AMOUNT");        // 金额变更
 * change.setBeforeValue("5000000");
 * change.setAfterValue("5500000");
 * change.setChangeReason("客户追加需求");
 * change.setStatus("PENDING");
 * projectContractChangeService.save(change);
 *
 * // 2. 审批通过后触发原合同字段同步
 * // 由 ydsz-workflow 流程实例审批通过事件回调触发
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContractChangeService 合同变更 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectContractChange 合同变更实体
 * @see com.njydsz.project.server.service.impl.ProjectContractServiceImpl 合同主表 Service（被同步）
 */
@Service
@RequiredArgsConstructor
public class ProjectContractChangeServiceImpl implements ProjectContractChangeService {

    /** 合同变更仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectContractChangeRepository repository;

    /**
     * 根据主键查询合同变更
     *
     * @param id 合同变更主键
     * @return 合同变更实体，不存在返回 null
     */
    @Override
    public ProjectContractChange getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询合同变更
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code contractId}、
     * {@code changeType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectContractChange> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增合同变更
     *
     * <p>新增后应触发 {@code ContractChangeCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动变更审批流。
     *
     * @param change 合同变更实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContractChange change) {
        return repository.save(change);
    }

    /**
     * 更新合同变更
     *
     * <p><b>注意：</b>仅在审批中（{@code status=PENDING}）的变更可修改，
     * 审批通过后（{@code status=APPROVED}）的变更<b>严禁</b>修改。
     *
     * @param change 合同变更实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContractChange change) {
        return repository.updateById(change);
    }

    /**
     * 逻辑删除合同变更
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>合同变更记录是合规审计的法定依据，<b>严禁</b>物理删除，
     * 仅在录入错误等极端情况下使用本方法。
     *
     * @param id 合同变更主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
