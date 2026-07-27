package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ExecutionWbsTaskService {
    ExecutionWbsTask getById(String id);
    IPage<ExecutionWbsTask> page(int pageNum, int pageSize);
    boolean save(ExecutionWbsTask entity);
    boolean updateById(ExecutionWbsTask entity);
    boolean removeById(String id);
}
