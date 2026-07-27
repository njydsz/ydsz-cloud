package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectChangeDO;

public interface ProjectChangeService {
    ProjectChangeDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectChangeDO> page(int pageNum, int pageSize);
    boolean save(ProjectChangeDO entity);
    boolean updateById(ProjectChangeDO entity);
    boolean removeById(String id);
}
