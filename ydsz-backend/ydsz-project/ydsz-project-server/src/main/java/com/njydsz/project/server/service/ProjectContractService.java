package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContract;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目合同 Application Service
 *
 * <p>提供项目合同的基本 CRUD 能力。
 *
 * @author ydsz-team
 * @since 1.0.0
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
