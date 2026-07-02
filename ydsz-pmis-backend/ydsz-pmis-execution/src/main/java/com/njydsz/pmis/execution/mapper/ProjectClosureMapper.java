package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.ProjectClosureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目结项 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ProjectClosureMapper extends BaseMapper<ProjectClosureDO> {

    ProjectClosureDO selectByCode(@Param("code") String code);

    ProjectClosureDO selectByInitiation(@Param("initiationId") Long initiationId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateLocked(@Param("id") Long id, @Param("locked") Integer locked);

    List<ProjectClosureDO> selectByType(@Param("closureType") String closureType);

    List<Map<String, Object>> aggregateByType(@Param("tenantId") Long tenantId);

    long countByStatus(@Param("status") String status, @Param("tenantId") Long tenantId);
}
