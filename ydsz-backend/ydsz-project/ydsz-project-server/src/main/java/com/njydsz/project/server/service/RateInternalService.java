package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateInternal;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface RateInternalService {
    RateInternal getById(String id);
    IPage<RateInternal> page(int pageNum, int pageSize);
    boolean save(RateInternal entity);
    boolean updateById(RateInternal entity);
    boolean removeById(String id);
}
