package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.ResourceAssignmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ResourceAssignmentMapper extends BaseMapper<ResourceAssignmentDO> {

    ResourceAssignmentDO selectByCode(@Param("code") String code);

    List<ResourceAssignmentDO> selectByEmployee(@Param("employeeId") Long employeeId);

    List<ResourceAssignmentDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<ResourceAssignmentDO> selectByStatus(@Param("status") String status);

    /** 统计某员工在指定日期下的活跃分配 */
    List<ResourceAssignmentDO> selectActiveOnDate(@Param("employeeId") Long employeeId,
                                                  @Param("date") LocalDate date);

    /** 员工当前活跃项目数 */
    Integer countActiveByEmployee(@Param("employeeId") Long employeeId);

    /** 池内当前活跃人数 */
    Integer countActiveByPool(@Param("poolId") Long poolId);
}
