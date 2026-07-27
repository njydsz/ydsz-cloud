package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;

public interface ExecutionTimeEntryService {
    ExecutionTimeEntry getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionTimeEntry> page(int pageNum, int pageSize);
    boolean save(ExecutionTimeEntry entity);
    boolean updateById(ExecutionTimeEntry entity);
    boolean removeById(String id);
}
