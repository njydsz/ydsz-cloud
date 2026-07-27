package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateCard;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface RateCardService {
    RateCard getById(String id);
    IPage<RateCard> page(int pageNum, int pageSize);
    boolean save(RateCard entity);
    boolean updateById(RateCard entity);
    boolean removeById(String id);
}
