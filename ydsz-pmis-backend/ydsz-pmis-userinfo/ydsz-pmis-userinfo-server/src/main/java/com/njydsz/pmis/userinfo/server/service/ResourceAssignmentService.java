package com.njydsz.pmis.userinfo.server.service.resource;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.domain.dto.resource.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.resource.ResourceAssignmentDO;

/**
 * 资源分配服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ResourceAssignmentService {

    /**
     * 业务动作分发：RESERVE/START/TRANSFER/RELEASE/CANCEL
     *
     * @param dto 资源分配表单
     * @return 分配记录 ID
     */
    String act(ResourceAssignmentCreateDTO dto);

    /**
     * 根据 ID 查询分配记录
     *
     * @param id 分配记录 ID
     * @return 分配记录，不存在时返回 null
     */
    ResourceAssignmentDO getById(String id);

    /**
     * 查询员工的分配记录列表
     *
     * @param employeeId 员工 ID
     * @return 分配记录列表
     */
    List<ResourceAssignmentDO> listByEmployee(String employeeId);

    /**
     * 查询项目的分配记录列表
     *
     * @param initiationId 项目 ID
     * @return 分配记录列表
     */
    List<ResourceAssignmentDO> listByInitiation(String initiationId);

    /**
     * 员工活跃分配数（用于过载检测）
     *
     * @param employeeId 员工 ID
     * @return 活跃分配数
     */
    int activeCount(String employeeId);

    /**
     * 员工利用率统计
     *
     * @param employeeId 员工 ID
     * @return 利用率统计结果
     */
    Map<String, Object> utilization(String employeeId);

    /**
     * 分页查询分配记录
     *
     * @param page         页码
     * @param size         每页条数
     * @param employeeId   员工 ID（可空）
     * @param initiationId 项目 ID（可空）
     * @param status       状态（可空）
     * @return 分页结果
     */
    Page<ResourceAssignmentDO> page(int page, int size, String employeeId, String initiationId, String status);
}
