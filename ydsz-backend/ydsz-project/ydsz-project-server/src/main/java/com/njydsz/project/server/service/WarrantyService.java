package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.warranty.Warranty;

public interface WarrantyService {
    Warranty getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<Warranty> page(int pageNum, int pageSize);
    boolean save(Warranty entity);
    boolean updateById(Warranty entity);
    boolean removeById(String id);
}
