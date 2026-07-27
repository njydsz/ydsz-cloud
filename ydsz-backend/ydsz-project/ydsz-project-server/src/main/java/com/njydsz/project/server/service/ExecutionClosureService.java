package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionClosureDO;

public interface ExecutionClosureService {
    ExecutionClosureDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionClosureDO> page(int pageNum, int pageSize);
    boolean save(ExecutionClosureDO entity);
    boolean updateById(ExecutionClosureDO entity);
    boolean removeById(String id);
}
