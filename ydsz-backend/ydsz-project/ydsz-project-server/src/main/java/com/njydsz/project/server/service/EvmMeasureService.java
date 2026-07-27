package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.evm.EvmMeasure;

public interface EvmMeasureService {
    EvmMeasure getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<EvmMeasure> page(int pageNum, int pageSize);
    boolean save(EvmMeasure entity);
    boolean updateById(EvmMeasure entity);
    boolean removeById(String id);
}
