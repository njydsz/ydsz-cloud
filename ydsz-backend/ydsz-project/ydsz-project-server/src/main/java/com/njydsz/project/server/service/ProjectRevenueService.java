package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectRevenue;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目收入 Service
 *
 * <p>管理项目收入（{@code ydsz_project_revenue}）的入账、确认、核销。
 * 项目收入来源于合同条款（里程碑/工时/开票），确认后进入利润计算。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>收入确认</b>：按合同里程碑或工时汇总</li>
 *   <li><b>与开票关联</b>：发票回执触发收入入账</li>
 * </ul>
 *
 * <p><b>收入类型：</b>合同收入 / 工时计费 / 变更收入 / 杂项收入。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectRevenue 收入实体
 * @see ProjectInvoiceService 发票 Service(开票触发收入入账)
 * @see ProjectProfitSnapshotService 利润快照 Service(收入侧数据源)
 */
public interface ProjectRevenueService {
    ProjectRevenue getById(String id);
    IPage<ProjectRevenue> page(int pageNum, int pageSize);
    boolean save(ProjectRevenue entity);
    boolean updateById(ProjectRevenue entity);
    boolean removeById(String id);
}
