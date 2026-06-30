package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.user.entity.EmployeeTagDO;

import java.util.List;

/**
 * 人员标签服务
 */
public interface EmployeeTagService {

    Long add(EmployeeTagCreateDTO dto);

    void remove(Long id);

    void replaceByEmployee(Long employeeId, List<EmployeeTagCreateDTO> tags);

    List<EmployeeTagDO> listByEmployee(Long employeeId);

    List<EmployeeTagDO> findCandidates(String tagType, String tagCode);
}
