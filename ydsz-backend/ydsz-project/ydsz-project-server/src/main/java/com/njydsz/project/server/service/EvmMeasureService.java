package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.evm.EvmMeasure;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface EvmMeasureService {
    EvmMeasure getById(String id);
    IPage<EvmMeasure> page(int pageNum, int pageSize);
    boolean save(EvmMeasure entity);
    boolean updateById(EvmMeasure entity);
    boolean removeById(String id);
}
