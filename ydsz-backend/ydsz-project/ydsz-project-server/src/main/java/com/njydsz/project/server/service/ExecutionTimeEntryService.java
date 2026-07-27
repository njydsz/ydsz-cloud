package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionTimeEntryDO;

public interface ExecutionTimeEntryService {
    ExecutionTimeEntryDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionTimeEntryDO> page(int pageNum, int pageSize);
    boolean save(ExecutionTimeEntryDO entity);
    boolean updateById(ExecutionTimeEntryDO entity);
    boolean removeById(String id);
}
