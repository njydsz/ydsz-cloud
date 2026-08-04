package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContract;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目合同 Application Service
 *
 * <p>管理项目合同（{@code ydsz_project_contract}）的全生命周期：CRUD、合同变更、合同补充、合同模板应用等。
 * 合同是项目收入侧的核心依据，所有计费/开票/收款节点都以合同金额为基准。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>合同金额</b>：含税/不含税金额、付款条款</li>
 *   <li><b>跨服务</b>：与 {@link ProjectInvoiceService} 发票、{@link ProjectPaymentService} 回款关联</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectContract 合同实体
 * @see ProjectContractChangeService 合同变更 Service
 * @see ProjectContractSupplementService 合同补充协议 Service
 * @see ProjectContractTemplateService 合同模板 Service
 */
public interface ProjectContractService {

    /**
     * 按 ID 查询合同
     *
     * @param id 主键 ID
     * @return 合同实体
     */
    ProjectContract getById(String id);

    /**
     * 分页查询合同列表
     *
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<ProjectContract> page(int pageNum, int pageSize);

    /**
     * 创建合同
     *
     * @param entity 合同实体
     * @return 是否创建成功
     */
    boolean save(ProjectContract entity);

    /**
     * 更新合同
     *
     * @param entity 合同实体
     * @return 是否更新成功
     */
    boolean updateById(ProjectContract entity);

    /**
     * 按 ID 删除合同（逻辑删除）
     *
     * @param id 主键 ID
     * @return 是否删除成功
     */
    boolean removeById(String id);
}
