package com.njydsz.pmis.iam.service;

import com.njydsz.pmis.iam.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.iam.entity.EmployeeTagDO;

import java.util.List;

/**
 * 人员标签服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface EmployeeTagService {

    /**
     * 新增标签
     *
     * @param dto 标签表单
     * @return 新建标签 ID
     */
    Long add(EmployeeTagCreateDTO dto);

    /**
     * 删除标签
     *
     * @param id 标签 ID
     */
    void remove(Long id);

    /**
     * 按员工替换标签集（先删后增）
     *
     * @param employeeId 员工 ID
     * @param tags       标签表单列表
     */
    void replaceByEmployee(Long employeeId, List<EmployeeTagCreateDTO> tags);

    /**
     * 查询员工标签列表
     *
     * @param employeeId 员工 ID
     * @return 标签列表
     */
    List<EmployeeTagDO> listByEmployee(Long employeeId);

    /**
     * 按标签类型与编码查询候选人员
     *
     * @param tagType 标签类型（TagType.code）
     * @param tagCode 标签编码
     * @return 候选人员标签列表
     */
    List<EmployeeTagDO> findCandidates(String tagType, String tagCode);
}
