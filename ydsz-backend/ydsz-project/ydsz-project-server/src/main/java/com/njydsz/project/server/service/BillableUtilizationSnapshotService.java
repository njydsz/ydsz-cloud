package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface BillableUtilizationSnapshotService {
    BillableUtilizationSnapshot getById(String id);
    IPage<BillableUtilizationSnapshot> page(int pageNum, int pageSize);
    boolean save(BillableUtilizationSnapshot entity);
    boolean updateById(BillableUtilizationSnapshot entity);
    boolean removeById(String id);
}
