package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateCardDO;

public interface RateCardService {
    RateCardDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<RateCardDO> page(int pageNum, int pageSize);
    boolean save(RateCardDO entity);
    boolean updateById(RateCardDO entity);
    boolean removeById(String id);
}
