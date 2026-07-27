package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectPaymentDO;

public interface ProjectPaymentService {
    ProjectPaymentDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectPaymentDO> page(int pageNum, int pageSize);
    boolean save(ProjectPaymentDO entity);
    boolean updateById(ProjectPaymentDO entity);
    boolean removeById(String id);
}
