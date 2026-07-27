package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.warranty.WarrantyDO;

public interface WarrantyService {
    WarrantyDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<WarrantyDO> page(int pageNum, int pageSize);
    boolean save(WarrantyDO entity);
    boolean updateById(WarrantyDO entity);
    boolean removeById(String id);
}
