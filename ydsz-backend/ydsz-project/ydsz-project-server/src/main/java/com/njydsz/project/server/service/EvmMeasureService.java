package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.evm.EvmMeasureDO;

public interface EvmMeasureService {
    EvmMeasureDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<EvmMeasureDO> page(int pageNum, int pageSize);
    boolean save(EvmMeasureDO entity);
    boolean updateById(EvmMeasureDO entity);
    boolean removeById(String id);
}
