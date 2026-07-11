package com.njydsz.pmis.userinfo.server.service.user;

import com.njydsz.pmis.userinfo.domain.dto.user.EmployeeTagCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.user.EmployeeTagDO;

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
    String add(EmployeeTagCreateDTO dto);

    /**
     * 删除标签
     *
     * @param id 标签 ID
     */
    void remove(String id);

    /**
     * 按员工替换标签集（先删后增）
     *
     * @param employeeId 员工 ID
     * @param tags       标签表单列表
     */
    void replaceByEmployee(String employeeId, List<EmployeeTagCreateDTO> tags);

    /**
     * 查询员工标签列表
     *
     * @param employeeId 员工 ID
     * @return 标签列表
     */
    List<EmployeeTagDO> listByEmployee(String employeeId);

    /**
     * 按标签类型与编码查询候选人员
     *
     * @param tagType 标签类型（TagType.code）
     * @param tagCode 标签编码
     * @return 候选人员标签列表
     */
    List<EmployeeTagDO> findCandidates(String tagType, String tagCode);
}
