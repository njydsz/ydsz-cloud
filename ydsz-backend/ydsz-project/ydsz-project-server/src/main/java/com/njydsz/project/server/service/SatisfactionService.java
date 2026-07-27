package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.satisfaction.Satisfaction;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface SatisfactionService {
    Satisfaction getById(String id);
    IPage<Satisfaction> page(int pageNum, int pageSize);
    boolean save(Satisfaction entity);
    boolean updateById(Satisfaction entity);
    boolean removeById(String id);
}
