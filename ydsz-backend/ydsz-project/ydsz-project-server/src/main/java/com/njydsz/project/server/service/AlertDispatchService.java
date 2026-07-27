package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.alert.AlertDispatch;

public interface AlertDispatchService {
    AlertDispatch getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<AlertDispatch> page(int pageNum, int pageSize);
    boolean save(AlertDispatch entity);
    boolean updateById(AlertDispatch entity);
    boolean removeById(String id);
}
