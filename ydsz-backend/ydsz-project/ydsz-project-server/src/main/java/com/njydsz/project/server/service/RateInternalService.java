package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateInternalDO;

public interface RateInternalService {
    RateInternalDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<RateInternalDO> page(int pageNum, int pageSize);
    boolean save(RateInternalDO entity);
    boolean updateById(RateInternalDO entity);
    boolean removeById(String id);
}
