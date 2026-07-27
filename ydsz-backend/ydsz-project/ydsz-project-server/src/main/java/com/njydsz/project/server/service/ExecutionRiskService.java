package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionRisk;

public interface ExecutionRiskService {
    ExecutionRisk getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionRisk> page(int pageNum, int pageSize);
    boolean save(ExecutionRisk entity);
    boolean updateById(ExecutionRisk entity);
    boolean removeById(String id);
}
