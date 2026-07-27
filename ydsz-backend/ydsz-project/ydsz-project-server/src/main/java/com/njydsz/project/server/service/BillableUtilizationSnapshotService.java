package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshotDO;

public interface BillableUtilizationSnapshotService {
    BillableUtilizationSnapshotDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<BillableUtilizationSnapshotDO> page(int pageNum, int pageSize);
    boolean save(BillableUtilizationSnapshotDO entity);
    boolean updateById(BillableUtilizationSnapshotDO entity);
    boolean removeById(String id);
}
