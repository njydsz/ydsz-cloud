package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshotDO;
import com.njydsz.project.domain.repository.billable.IBillableUtilizationSnapshotRepository;
import com.njydsz.project.server.service.BillableUtilizationSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillableUtilizationSnapshotServiceImpl implements BillableUtilizationSnapshotService {
    private final IBillableUtilizationSnapshotRepository repository;

    public BillableUtilizationSnapshotDO getById(String id) { return repository.getById(id); }
    public IPage<BillableUtilizationSnapshotDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(BillableUtilizationSnapshotDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(BillableUtilizationSnapshotDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
