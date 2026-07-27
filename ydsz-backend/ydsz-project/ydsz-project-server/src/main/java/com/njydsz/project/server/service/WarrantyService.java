package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.warranty.Warranty;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface WarrantyService {
    Warranty getById(String id);
    IPage<Warranty> page(int pageNum, int pageSize);
    boolean save(Warranty entity);
    boolean updateById(Warranty entity);
    boolean removeById(String id);
}
