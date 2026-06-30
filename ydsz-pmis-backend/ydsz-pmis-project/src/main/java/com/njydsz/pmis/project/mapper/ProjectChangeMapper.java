package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ProjectChangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectChangeMapper extends BaseMapper<ProjectChangeDO> {

    ProjectChangeDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<ProjectChangeDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> aggregateByType(@Param("tenantId") Long tenantId);

    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") Long tenantId);

    long countMajorByInitiation(@Param("initiationId") Long initiationId);
}
