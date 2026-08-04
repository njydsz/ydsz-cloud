package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.domain.repository.project.IProjectContractTemplateRepository;
import com.njydsz.project.server.service.ProjectContractTemplateService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目合同模板 Service 实现
 *
 * <p>对 {@link ProjectContractTemplateService} 接口的完整实现，是「项目管理 / 合同模板管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_contract_template} 合同模板表，
 * 对标大厂 PMIS / 法务系统中的「合同模板 / 合同范本 / 标准合同」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>标准合同模板管理</b>：维护公司级标准合同模板（销售合同 / 服务合同 / 采购合同 / 外包合同等），
 *       按行业 / 客户类型 / 业务场景分类</li>
 *   <li><b>模板版本管理</b>：同一类合同模板支持多版本（如 V1.0 / V1.1 / V2.0），
 *       通过 {@code version} 字段管理</li>
 *   <li><b>模板审批</b>：新模板上线前需经法务 / 业务部门审批，通过 {@code ydsz-workflow} 流程引擎走审批流</li>
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
 *   <li><b>模板内容</b>：合同正文（条款 / 变量 / 占位符）通过 {@code ydsz-common-file} 存储，
 *       本表只存储模板元数据</li>
 *   <li><b>占位符语法</b>：支持 {@code ${var}} 嵌套变量替换，与消息模板的变量替换机制一致</li>
 *   <li><b>使用追踪</b>：合同模板被实际合同时应记录 {@code usageCount}，便于法务评估模板使用率</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       已被历史合同引用的模板<b>严禁</b>物理删除，否则历史合同无法回溯模板版本</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建标准销售合同模板
 * ProjectContractTemplate template = new ProjectContractTemplate();
 * template.setTemplateCode("TPL-SALES-V1.0");
 * template.setTemplateName("标准销售合同 V1.0");
 * template.setTemplateType("SALES");
 * template.setIndustry("GENERAL");
 * template.setVersion("1.0");
 * template.setStatus("ACTIVE");
 * projectContractTemplateService.save(template);
 *
 * // 2. 从模板生成新合同时调用
 * // 由 ProjectContractService.copyFromTemplate(templateId, ...) 触发
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContractTemplateService 合同模板 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectContractTemplate 合同模板实体
 * @see com.njydsz.project.server.service.impl.ProjectContractServiceImpl 合同主表 Service（使用模板）
 */
@Service
@RequiredArgsConstructor
public class ProjectContractTemplateServiceImpl implements ProjectContractTemplateService {

    /** 合同模板仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectContractTemplateRepository repository;

    /**
     * 根据主键查询合同模板
     *
     * @param id 合同模板主键
     * @return 合同模板实体，不存在返回 null
     */
    @Override
    public ProjectContractTemplate getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询合同模板
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code templateType}、
     * {@code industry}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectContractTemplate> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增合同模板
     *
     * <p>新增后应触发 {@code ContractTemplateCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动模板审批流。
     *
     * @param template 合同模板实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContractTemplate template) {
        return repository.save(template);
    }

    /**
     * 更新合同模板
     *
     * <p><b>注意：</b>已被实际合同引用的模板修改应通过「新建版本」流程，
     * 而非直接修改本记录，避免历史合同模板回溯失效。
     *
     * @param template 合同模板实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContractTemplate template) {
        return repository.updateById(template);
    }

    /**
     * 逻辑删除合同模板
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>合同模板一旦被历史合同引用，<b>严禁</b>物理删除，
     * 否则历史合同无法回溯原始模板内容。
     *
     * @param id 合同模板主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
