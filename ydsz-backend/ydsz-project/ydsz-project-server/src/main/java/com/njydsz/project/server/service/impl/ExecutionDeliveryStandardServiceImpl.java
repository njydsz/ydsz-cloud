package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryStandardRepository;
import com.njydsz.project.server.service.ExecutionDeliveryStandardService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目交付物标准 / 模板 Service 实现
 *
 * <p>对 {@link ExecutionDeliveryStandardService} 接口的完整实现，是「项目管理 / 交付物标准管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_delivery_standard} 交付物标准表，
 * 对标大厂 PMIS / 项目管理系统的「交付物标准 / 交付物模板 / 交付物规范」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>交付物标准模板</b>：维护按项目类型 / 级别分类的标准交付物清单（如实施类项目必交付
 *       「需求规格说明书 / 详细设计 / 测试报告 / 上线报告」等）</li>
 *   <li><b>必交付控制</b>：通过 {@code required} 字段控制每个交付物是否必交付，
 *       必交付缺失时阻断项目阶段门</li>
 *   <li><b>TR 触发</b>：通过 {@code triggerTr} 字段控制交付物是否触发技术评审（Technical Review）</li>
 *   <li><b>模板审批</b>：新标准上线前需经 PMO / 质量部审批</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>模板分类</b>：{@code projectType}（项目类型 IMPLEMENTATION 实施 / CUSTOMIZATION 定制 /
 *       OPERATION 运维 等） + {@code projectLevel}（A / B / C 级）二维分类</li>
 *   <li><b>阶段关联</b>：通过 {@code stage} 字段关联项目阶段（如 DESIGN 设计 / BUILD 建设 /
 *       UAT 用户验收 / GO_LIVE 上线）</li>
 *   <li><b>模板引用</b>：已被实际交付物引用的标准模板修改应通过「新建版本」流程，
 *       而非直接修改本记录</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       已被历史交付物引用的标准模板<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建实施类 C 级项目交付物标准
 * ExecutionDeliveryStandard standard = new ExecutionDeliveryStandard();
 * standard.setProjectType("IMPLEMENTATION");
 * standard.setProjectLevel("C");
 * standard.setDeliveryName("详细设计说明书");
 * standard.setDeliveryCategory("DOC");
 * standard.setStage("DESIGN");
 * standard.setRequired(1);
 * standard.setTriggerTr(1);
 * standard.setAcceptanceCriteria("设计符合需求规格，方案可落地");
 * executionDeliveryStandardService.save(standard);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionDeliveryStandardService 交付物标准 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard 交付物标准实体
 * @see com.njydsz.project.server.service.impl.ExecutionDeliveryItemServiceImpl 交付物 Service（使用标准）
 */
@Service
@RequiredArgsConstructor
public class ExecutionDeliveryStandardServiceImpl implements ExecutionDeliveryStandardService {

    /** 交付物标准仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionDeliveryStandardRepository repository;

    /**
     * 根据主键查询交付物标准
     *
     * @param id 交付物标准主键
     * @return 交付物标准实体，不存在返回 null
     */
    @Override
    public ExecutionDeliveryStandard getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询交付物标准
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code projectType}、
     * {@code stage} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionDeliveryStandard> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增交付物标准
     *
     * <p>新增后应触发 {@code DeliveryStandardCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动模板审批流。
     *
     * @param standard 交付物标准实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionDeliveryStandard standard) {
        return repository.save(standard);
    }

    /**
     * 更新交付物标准
     *
     * <p><b>注意：</b>已被实际交付物引用的标准模板修改应通过「新建版本」流程，
     * 而非直接修改本记录，避免历史交付物回溯失效。
     *
     * @param standard 交付物标准实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionDeliveryStandard standard) {
        return repository.updateById(standard);
    }

    /**
     * 逻辑删除交付物标准
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>交付物标准一旦被历史交付物引用，<b>严禁</b>物理删除。
     *
     * @param id 交付物标准主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
