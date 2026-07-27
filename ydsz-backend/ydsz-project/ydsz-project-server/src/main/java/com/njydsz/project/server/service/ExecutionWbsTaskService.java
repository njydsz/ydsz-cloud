package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;

public interface ExecutionWbsTaskService {
    ExecutionWbsTask getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionWbsTask> page(int pageNum, int pageSize);
    boolean save(ExecutionWbsTask entity);
    boolean updateById(ExecutionWbsTask entity);
    boolean removeById(String id);
}
