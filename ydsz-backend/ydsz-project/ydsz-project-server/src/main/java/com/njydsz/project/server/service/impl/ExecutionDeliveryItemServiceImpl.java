package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryItemRepository;
import com.njydsz.project.server.service.ExecutionDeliveryItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目交付物 Service 实现
 *
 * <p>对 {@link ExecutionDeliveryItemService} 接口的完整实现，是「项目管理 / 交付物管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_delivery_item} 项目交付物实例表，
 * 对标大厂 PMIS / 项目管理系统的「项目交付物 / 交付物清单 / 交付物验收」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>交付物实例化</b>：从 {@code ydsz_execution_delivery_standard} 交付物标准模板
 *       实例化生成每个立项的交付物清单</li>
 *   <li><b>交付物类别</b>：支持 {@code DOC} 文档 / {@code CODE} 代码 / {@code MODEL} 模型 /
 *       {@code RUNBOOK} 运行手册 / {@code REPORT} 报告 / {@code OTHER} 其他</li>
 *   <li><b>交付物验收</b>：跟踪交付物的计划提交日期 / 实际提交日期 / 验收日期，
 *       触发 TR（技术评审）流程</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>交付物实例化时批量插入需与父表（标准）共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>与标准模板关系</b>：通过 {@code standardId} 关联 {@code ydsz_execution_delivery_standard.id}，
 *       立项初始化时按项目类型 / 级别从标准模板批量生成</li>
 *   <li><b>文件存储</b>：交付物文件统一上传到 {@code ydsz-common-file} 文件存储服务，
 *       本表只存储文件元数据（{@code fileId / fileName / fileSize / fileUrl}）</li>
 *   <li><b>状态机</b>：{@code PENDING → IN_PROGRESS → SUBMITTED → ACCEPTED / REJECTED}</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       交付物是项目验收的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 立项初始化时从标准模板批量生成交付物
 * List<ExecutionDeliveryItem> items = deliveryStandardService.listByProjectType(type)
 *         .stream()
 *         .map(std -> new ExecutionDeliveryItem().setStandardId(std.getId())
 *                 .setInitiationId("project_123")
 *                 .setDeliveryName(std.getDeliveryName())
 *                 .setDeliveryCategory(std.getDeliveryCategory())
 *                 .setStage(std.getStage())
 *                 .setRequired(std.getRequired()))
 *         .collect(Collectors.toList());
 * executionDeliveryItemService.saveBatch(items);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionDeliveryItemService 交付物 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem 交付物实体
 * @see com.njydsz.project.server.service.impl.ExecutionDeliveryStandardServiceImpl 交付物标准 Service
 * @see com.njydsz.common.file.storage.SecureStorage 文件加密存储（交付物实际文件）
 */
@Service
@RequiredArgsConstructor
public class ExecutionDeliveryItemServiceImpl implements ExecutionDeliveryItemService {

    /** 交付物仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionDeliveryItemRepository repository;

    /**
     * 根据主键查询交付物
     *
     * @param id 交付物主键
     * @return 交付物实体，不存在返回 null
     */
    @Override
    public ExecutionDeliveryItem getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询交付物
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code deliveryCategory}、{@code stage} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionDeliveryItem> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增交付物
     *
     * <p>典型调用方：立项初始化（从标准模板批量生成）。
     *
     * @param item 交付物实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionDeliveryItem item) {
        return repository.save(item);
    }

    /**
     * 更新交付物
     *
     * <p>典型场景：提交交付物、补充提交说明、记录验收结果。
     *
     * @param item 交付物实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionDeliveryItem item) {
        return repository.updateById(item);
    }

    /**
     * 逻辑删除交付物
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>交付物是项目验收的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 交付物主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
