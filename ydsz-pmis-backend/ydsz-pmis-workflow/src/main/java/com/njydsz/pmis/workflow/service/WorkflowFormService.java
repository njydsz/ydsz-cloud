package com.njydsz.pmis.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.workflow.entity.WorkflowFormDO;

import java.util.List;

/**
 * 流程表单服务
 */
public interface WorkflowFormService {

    /**
     * 新增表单
     */
    Long create(WorkflowFormDO form);

    /**
     * 更新表单
     */
    void update(WorkflowFormDO form);

    /**
     * 删除表单
     */
    void delete(Long id);

    /**
     * 获取表单详情
     */
    WorkflowFormDO getById(Long id);

    /**
     * 根据 formKey 查询
     */
    WorkflowFormDO getByFormKey(String formKey);

    /**
     * 根据流程定义 KEY 查询
     */
    List<WorkflowFormDO> listByProcessKey(String processKey);

    /**
     * 分页查询
     */
    Page<WorkflowFormDO> page(int page, int size, String keyword, String processKey, String status);
}
