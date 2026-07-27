package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionClosure;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ExecutionClosureService {
    ExecutionClosure getById(String id);
    IPage<ExecutionClosure> page(int pageNum, int pageSize);
    boolean save(ExecutionClosure entity);
    boolean updateById(ExecutionClosure entity);
    boolean removeById(String id);
}
