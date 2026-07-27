package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateCard;

public interface RateCardService {
    RateCard getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<RateCard> page(int pageNum, int pageSize);
    boolean save(RateCard entity);
    boolean updateById(RateCard entity);
    boolean removeById(String id);
}
