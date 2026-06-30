package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.ProjectClosureCreateDTO;
import com.njydsz.pmis.execution.dto.ProjectClosureStatusDTO;
import com.njydsz.pmis.execution.engine.ClosureAdmissionValidator;
import com.njydsz.pmis.execution.entity.ProjectClosureDO;

import java.util.List;
import java.util.Map;

/**
 * 项目结项服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ProjectClosureService {

    Long create(ProjectClosureCreateDTO dto);

    void changeStatus(ProjectClosureStatusDTO dto);

    void delete(Long id);

    ProjectClosureDO getById(Long id);

    ProjectClosureDO getByInitiation(Long initiationId);

    Page<ProjectClosureDO> page(int page, int size, String keyword,
                                String closureType, String status);

    List<ProjectClosureDO> listByType(String closureType);

    List<Map<String, Object>> aggregateByType(Long tenantId);

    ClosureAdmissionValidator.AdmissionCheck checkAdmission(Long id);
}
