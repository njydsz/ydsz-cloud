package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.user.entity.ResourceAssignmentDO;

import java.util.List;
import java.util.Map;

/**
 * 资源分配服务
 */
public interface ResourceAssignmentService {

    /** 业务动作分发：RESERVE/START/TRANSFER/RELEASE/CANCEL */
    Long act(ResourceAssignmentCreateDTO dto);

    ResourceAssignmentDO getById(Long id);

    List<ResourceAssignmentDO> listByEmployee(Long employeeId);

    List<ResourceAssignmentDO> listByInitiation(Long initiationId);

    /** 员工活跃分配数（用于过载检测） */
    int activeCount(Long employeeId);

    /** 员工利用率统计 */
    Map<String, Object> utilization(Long employeeId);

    Page<ResourceAssignmentDO> page(int page, int size, Long employeeId, Long initiationId, String status);
}
