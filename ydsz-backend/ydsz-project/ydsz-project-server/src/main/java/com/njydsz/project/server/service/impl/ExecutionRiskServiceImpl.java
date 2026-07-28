package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.domain.repository.execution.IExecutionRiskRepository;
import com.njydsz.project.server.service.ExecutionRiskService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目风险登记 Service 实现
 *
 * <p>对 {@link ExecutionRiskService} 接口的完整实现，是「项目管理 / 项目风险管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_risk} 项目风险登记表，
 * 对标大厂 PMIS / 项目管理系统的「项目风险 / 风险登记 / 风险跟踪 / 风险闭环」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>风险识别</b>：项目执行过程中识别的风险（技术风险 / 资源风险 / 客户风险 / 进度风险等）</li>
 *   <li><b>风险评估</b>：按 {@code probability}（概率）× {@code impact}（影响）矩阵评估风险等级
 *       （{@code HIGH} 高 / {@code MEDIUM} 中 / {@code LOW} 低）</li>
 *   <li><b>风险跟踪</b>：跟踪风险状态（{@code OPEN} 开放 / {@code MITIGATING} 处理中 /
 *       {@code CLOSED} 已关闭 / {@code OCCURRED} 已发生）</li>
 *   <li><b>风险闭环</b>：风险应对措施的执行跟踪、复盘</li>
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
 *   <li><b>风险等级计算</b>：{@code riskLevel = probability × impact}，自动计算并由 PM 调整</li>
 *   <li><b>风险预警</b>：高风险（{@code riskLevel=HIGH}）自动触发 {@link com.njydsz.project.server.service.impl.AlertDispatchServiceImpl}
 *       告警派发</li>
 *   <li><b>风险审计</b>：风险登记记录是项目复盘和审计的依据，<b>严禁</b>物理删除</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 登记项目风险
 * ExecutionRisk risk = new ExecutionRisk();
 * risk.setInitiationId("project_123");
 * risk.setRiskTitle("客户关键决策人离职");
 * risk.setRiskType("CUSTOMER");
 * risk.setProbability("MEDIUM");
 * risk.setImpact("HIGH");
 * risk.setRiskLevel("HIGH");
 * risk.setMitigation("联系客户 HR 部门尽快对接新决策人，建立定期沟通机制");
 * risk.setOwner("user_456");
 * risk.setStatus("OPEN");
 * executionRiskService.save(risk);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionRiskService 项目风险 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionRisk 项目风险实体
 * @see com.njydsz.project.server.service.impl.AlertDispatchServiceImpl 告警派发（高风险联动）
 */
@Service
@RequiredArgsConstructor
public class ExecutionRiskServiceImpl implements ExecutionRiskService {

    /** 项目风险仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionRiskRepository repository;

    /**
     * 根据主键查询项目风险
     *
     * @param id 项目风险主键
     * @return 项目风险实体，不存在返回 null
     */
    @Override
    public ExecutionRisk getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询项目风险
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code riskLevel}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionRisk> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增项目风险
     *
     * <p>新增后高风险会自动触发告警派发。
     *
     * @param risk 项目风险实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionRisk risk) {
        return repository.save(risk);
    }

    /**
     * 更新项目风险
     *
     * <p>典型场景：更新风险状态、补充应对措施、记录复盘。
     *
     * @param risk 项目风险实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionRisk risk) {
        return repository.updateById(risk);
    }

    /**
     * 逻辑删除项目风险
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>风险登记记录是项目复盘和审计的依据，<b>严禁</b>物理删除。
     *
     * @param id 项目风险主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
