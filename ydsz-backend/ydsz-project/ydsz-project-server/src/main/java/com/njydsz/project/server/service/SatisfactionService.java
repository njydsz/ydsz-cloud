package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.satisfaction.SatisfactionDO;

public interface SatisfactionService {
    SatisfactionDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<SatisfactionDO> page(int pageNum, int pageSize);
    boolean save(SatisfactionDO entity);
    boolean updateById(SatisfactionDO entity);
    boolean removeById(String id);
}
