package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.entity.ProjectChangeDO;

import java.util.List;
import java.util.Map;

/**
 * 项目变更服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ProjectChangeService {

    Long create(ProjectChangeCreateDTO dto);

    void changeStatus(ProjectChangeStatusDTO dto);

    void delete(Long id);

    ProjectChangeDO getById(Long id);

    Page<ProjectChangeDO> page(int page, int size, String keyword,
                               String changeType, String status, Long initiationId);

    List<ProjectChangeDO> listByInitiation(Long initiationId);

    List<Map<String, Object>> aggregateByType(Long tenantId);

    List<Map<String, Object>> aggregateByStatus(Long tenantId);

    long countMajorByInitiation(Long initiationId);
}
