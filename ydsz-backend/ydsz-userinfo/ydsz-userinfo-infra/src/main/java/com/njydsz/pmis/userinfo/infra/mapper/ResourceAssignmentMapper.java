package com.njydsz.userinfo.infra.mapper.resource;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.userinfo.domain.entity.resource.ResourceAssignmentDO;

/**
 * 资源分配 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ResourceAssignmentMapper extends BaseMapper<ResourceAssignmentDO> {

    /**
     * 根据分配编码查询资源分配记录
     *
     * @param code 分配编码
     * @return 资源分配记录，未找到返回 null
     */
    ResourceAssignmentDO selectByCode(@Param("code") String code);

    /**
     * 查询某员工的全部资源分配记录
     *
     * @param employeeId 员工 ID
     * @return 资源分配记录列表
     */
    List<ResourceAssignmentDO> selectByEmployee(@Param("employeeId") String employeeId);

    /**
     * 查询某立项项目下的全部资源分配记录
     *
     * @param initiationId 立项 ID
     * @return 资源分配记录列表
     */
    List<ResourceAssignmentDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 根据状态查询资源分配记录列表
     *
     * @param status 状态编码
     * @return 资源分配记录列表
     */
    List<ResourceAssignmentDO> selectByStatus(@Param("status") String status);

    /**
     * 统计某员工在指定日期下的活跃分配
     *
     * @param employeeId 员工 ID
     * @param date 指定日期
     * @return 活跃分配记录列表
     */
    List<ResourceAssignmentDO> selectActiveOnDate(@Param("employeeId") String employeeId,
                                                  @Param("date") LocalDate date);

    /**
     * 员工当前活跃项目数
     *
     * @param employeeId 员工 ID
     * @return 活跃项目数
     */
    Integer countActiveByEmployee(@Param("employeeId") String employeeId);

    /**
     * 池内当前活跃人数
     *
     * @param poolId 资源池 ID
     * @return 活跃人数
     */
    Integer countActiveByPool(@Param("poolId") String poolId);
}
