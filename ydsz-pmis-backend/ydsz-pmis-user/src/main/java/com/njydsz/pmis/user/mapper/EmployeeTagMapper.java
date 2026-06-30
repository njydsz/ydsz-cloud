package com.njydsz.pmis.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.user.entity.EmployeeTagDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmployeeTagMapper extends BaseMapper<EmployeeTagDO> {

    List<EmployeeTagDO> selectByEmployee(@Param("employeeId") Long employeeId);

    List<EmployeeTagDO> selectByTag(@Param("tagType") String tagType, @Param("tagCode") String tagCode);

    void deleteByEmployee(@Param("employeeId") Long employeeId);
}
