package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;

public interface BillableUtilizationSnapshotService {
    BillableUtilizationSnapshot getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<BillableUtilizationSnapshot> page(int pageNum, int pageSize);
    boolean save(BillableUtilizationSnapshot entity);
    boolean updateById(BillableUtilizationSnapshot entity);
    boolean removeById(String id);
}
