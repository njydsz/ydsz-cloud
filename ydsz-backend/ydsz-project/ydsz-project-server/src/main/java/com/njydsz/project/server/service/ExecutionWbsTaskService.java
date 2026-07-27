package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionWbsTaskDO;

public interface ExecutionWbsTaskService {
    ExecutionWbsTaskDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionWbsTaskDO> page(int pageNum, int pageSize);
    boolean save(ExecutionWbsTaskDO entity);
    boolean updateById(ExecutionWbsTaskDO entity);
    boolean removeById(String id);
}
