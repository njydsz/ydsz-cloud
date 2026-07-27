package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContract;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectContractService {
    ProjectContract getById(String id);
    IPage<ProjectContract> page(int pageNum, int pageSize);
    boolean save(ProjectContract entity);
    boolean updateById(ProjectContract entity);
    boolean removeById(String id);
}
