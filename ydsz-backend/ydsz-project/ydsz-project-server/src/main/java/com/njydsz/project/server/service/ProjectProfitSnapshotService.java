package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSnapshotDO;

public interface ProjectProfitSnapshotService {
    ProjectProfitSnapshotDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectProfitSnapshotDO> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSnapshotDO entity);
    boolean updateById(ProjectProfitSnapshotDO entity);
    boolean removeById(String id);
}
