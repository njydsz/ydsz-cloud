package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.ProjectClosureDO;
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

    /**
     * 按编码查询项目结项
     *
     * @param code 结项编码
     * @return 结项对象，未找到返回 null
     */
    ProjectClosureDO selectByCode(@Param("code") String code);

    /**
     * 按立项 ID 查询项目结项
     *
     * @param initiationId 立项 ID
     * @return 结项对象，未找到返回 null
     */
    ProjectClosureDO selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 更新结项状态
     *
     * @param id     结项 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新锁定状态
     *
     * @param id     结项 ID
     * @param locked 锁定状态（0/1）
     * @return 受影响行数
     */
    int updateLocked(@Param("id") String id, @Param("locked") Integer locked);

    /**
     * 按结项类型查询列表
     *
     * @param closureType 结项类型
     * @return 结项列表
     */
    List<ProjectClosureDO> selectByType(@Param("closureType") String closureType);

    /**
     * 按类型聚合统计
     *
     * @param tenantId 租户 ID，可选
     * @return 聚合统计列表
     */
    List<Map<String, Object>> aggregateByType(@Param("tenantId") String tenantId);

    /**
     * 按状态计数
     *
     * @param status   状态
     * @param tenantId 租户 ID，可选
     * @return 符合条件的记录数
     */
    long countByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
