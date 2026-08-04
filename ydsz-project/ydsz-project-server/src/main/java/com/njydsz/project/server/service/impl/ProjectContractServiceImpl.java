package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.domain.repository.project.IProjectContractRepository;
import com.njydsz.project.server.service.ProjectContractService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目合同 Service 实现
 *
 * <p>对 {@link ProjectContractService} 接口的完整实现，是「项目管理」业务域<b>合同环节</b>的核心业务逻辑层。
 * 维护 {@code ydsz_project_contract} 合同表，对标大厂 PMIS / 法务系统中的「销售合同 / 服务合同」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li>合同与立项（{@code ProjectInitiation}）通过 {@code projectId} 关联，
 *       一个项目可对应多个合同（主合同 + 补充合同 + 变更合同）</li>
 *   <li>合同金额、付款条款、结算条款等关键字段是后续「预算 / 收入 / 成本 / 利润分摊」计算的基础数据源</li>
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
 *   <li><b>合同多版本</b>：通过 {@link com.njydsz.project.server.service.impl.ProjectContractChangeServiceImpl}
 *       维护合同变更（金额 / 范围 / 工期），保留完整审计链</li>
 *   <li><b>合同模板</b>：通过 {@link com.njydsz.project.server.service.impl.ProjectContractTemplateServiceImpl}
 *       维护标准合同模板（按行业 / 客户类型分类）</li>
 *   <li><b>合同附件</b>：通过 {@link com.njydsz.project.server.service.impl.ProjectContractSupplementServiceImpl}
 *       维护合同附件、补充协议等</li>
 *   <li><b>软删除</b>：{@code ydsz_project_contract} 表采用 <b>逻辑删除</b>（{@code deleted} 字段），
 *       合同一旦签订<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建合同
 * ProjectContract contract = new ProjectContract();
 * contract.setProjectId("project_123");
 * contract.setContractNo("CT-2026-001");
 * contract.setContractName("某 ERP 实施项目销售合同");
 * contract.setTotalAmount(new BigDecimal("5000000"));
 * contract.setSignDate(LocalDate.now());
 * contract.setStatus("SIGNED");
 * projectContractService.save(contract);
 *
 * // 2. 反查合同
 * ProjectContract existing = projectContractService.getById(contractId);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContractService 合同 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectContract 合同实体
 * @see com.njydsz.project.server.service.impl.ProjectContractChangeServiceImpl 合同变更 Service
 * @see com.njydsz.project.server.service.impl.ProjectContractTemplateServiceImpl 合同模板 Service
 */
@Service
@RequiredArgsConstructor
public class ProjectContractServiceImpl implements ProjectContractService {

    /** 合同仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectContractRepository repository;

    /**
     * 根据主键查询合同
     *
     * @param id 合同主键
     * @return 合同实体，不存在返回 null
     */
    @Override
    public ProjectContract getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询合同
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectContract> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增合同
     *
     * <p>合同创建后应触发 {@code ContractSignedEvent} 领域事件（由调用方或后续 Service 处理），
     * 同步更新立项的 {@code contractAmount} 字段。
     *
     * @param contract 合同实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContract contract) {
        return repository.save(contract);
    }

    /**
     * 更新合同
     *
     * <p><b>注意：</b>已签订合同（{@code status=SIGNED}）的关键字段（金额 / 范围 / 工期）变更
     * 应通过 {@link com.njydsz.project.server.service.impl.ProjectContractChangeServiceImpl} 走「合同变更」流程，
     * <b>禁止</b>直接通过本方法修改，避免审计链断裂。
     *
     * @param contract 合同实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContract contract) {
        return repository.updateById(contract);
    }

    /**
     * 逻辑删除合同
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除，便于审计回溯。
     *
     * <p><b>注意：</b>合同一旦签订<b>严禁</b>物理删除，本方法通常<b>不建议</b>使用，
     * 推荐通过 {@code status=TERMINATED} 标记合同终止。
     *
     * @param id 合同主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
