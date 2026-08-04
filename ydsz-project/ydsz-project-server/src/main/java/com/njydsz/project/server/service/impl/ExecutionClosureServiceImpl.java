package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.domain.repository.execution.IExecutionClosureRepository;
import com.njydsz.project.server.service.ExecutionClosureService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目收尾 Service 实现
 *
 * <p>对 {@link ExecutionClosureService} 接口的完整实现，是「项目管理 / 项目收尾」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_closure} 项目收尾表，
 * 对标大厂 PMIS / 项目管理系统的「项目收尾 / 项目关闭 / 项目验收 / 经验教训总结」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>项目验收</b>：管理项目最终验收（客户验收 / 内部验收），含验收清单 / 验收结论 / 遗留问题</li>
 *   <li><b>经验教训</b>：记录项目过程的经验教训（{@code lessonsLearned}），
 *       形成组织过程资产（OPA），支撑未来项目复用</li>
 *   <li><b>项目关闭</b>：项目关闭前必须完成的所有事项（合同尾款 / 文档归档 / 资源释放），
 *       通过 {@code closureChecklist} 字段管理</li>
 *   <li><b>客户满意度</b>：联动 {@code ydsz_satisfaction} 客户满意度表，采集客户反馈</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>项目关闭时联动多个数据源（合同尾款 / 文档归档）需与相关 Service 共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>项目状态联动</b>：收尾完成后联动 {@link com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl}
 *       将项目状态推进到 {@code CLOSURE}</li>
 *   <li><b>保期管理</b>：与 {@code ydsz_warranty} 售后保期表联动，验收后进入保期</li>
 *   <li><b>经验库</b>：{@code lessonsLearned} 字段沉淀为组织过程资产，由独立
 *       {@code ydsz-knowledge-base} 知识库模块管理（独立模块）</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       收尾记录是合规审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 提交项目收尾
 * ExecutionClosure closure = new ExecutionClosure();
 * closure.setInitiationId("project_123");
 * closure.setAcceptanceDate(LocalDate.now());
 * closure.setAcceptanceResult("PASSED");
 * closure.setLessonsLearned("本期项目踩坑：客户需求变更频繁，需加强前期调研");
 * closure.setClosureChecklist("[\"合同尾款已结清\",\"文档已归档\",\"资源已释放\"]");
 * closure.setStatus("COMPLETED");
 * executionClosureService.save(closure);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionClosureService 项目收尾 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionClosure 项目收尾实体
 * @see com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl 立项 Service（状态联动）
 * @see com.njydsz.project.server.service.impl.WarrantyServiceImpl 售后保期 Service（保期联动）
 * @see com.njydsz.project.server.service.impl.SatisfactionServiceImpl 客户满意度 Service（满意度采集）
 */
@Service
@RequiredArgsConstructor
public class ExecutionClosureServiceImpl implements ExecutionClosureService {

    /** 项目收尾仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionClosureRepository repository;

    /**
     * 根据主键查询项目收尾
     *
     * @param id 项目收尾主键
     * @return 项目收尾实体，不存在返回 null
     */
    @Override
    public ExecutionClosure getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询项目收尾
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code acceptanceResult} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionClosure> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增项目收尾
     *
     * <p>新增后应触发 {@code ProjectClosureCompletedEvent} 领域事件，
     * 联动立项状态推进、客户满意度采集、售后保期启动等下游链路。
     *
     * @param closure 项目收尾实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionClosure closure) {
        return repository.save(closure);
    }

    /**
     * 更新项目收尾
     *
     * <p><b>注意：</b>已完成的收尾（{@code status=COMPLETED}）的关键字段（验收结论 / 经验教训）
     * <b>严禁</b>修改，错误应通过「补充记录」纠正。
     *
     * @param closure 项目收尾实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionClosure closure) {
        return repository.updateById(closure);
    }

    /**
     * 逻辑删除项目收尾
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>项目收尾记录是合规审计的依据，<b>严禁</b>物理删除。
     *
     * @param id 项目收尾主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
