package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.alert.AlertDispatchDO;

public interface AlertDispatchService {
    AlertDispatchDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<AlertDispatchDO> page(int pageNum, int pageSize);
    boolean save(AlertDispatchDO entity);
    boolean updateById(AlertDispatchDO entity);
    boolean removeById(String id);
}
