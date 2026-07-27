package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionRiskDO;

public interface ExecutionRiskService {
    ExecutionRiskDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionRiskDO> page(int pageNum, int pageSize);
    boolean save(ExecutionRiskDO entity);
    boolean updateById(ExecutionRiskDO entity);
    boolean removeById(String id);
}
