package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ProjectChangeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 项目变更数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ProjectChangeMapper extends BaseMapper<ProjectChangeDO> {

    /**
     * 根据变更单号查询项目变更。
     *
     * @param code 变更单号
     * @return 变更记录；不存在返回 null
     */
    ProjectChangeDO selectByCode(@Param("code") String code);

    /**
     * 更新变更状态。
     *
     * @param id     变更 ID
     * @param status 目标状态码（ChangeStatus.code）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 根据立项 ID 查询变更记录列表。
     *
     * @param initiationId 立项 ID
     * @return 变更记录列表
     */
    List<ProjectChangeDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按变更类型聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种变更类型对应的数量列表
     */
    List<Map<String, Object>> aggregateByType(@Param("tenantId") String tenantId);

    /**
     * 按变更状态聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种变更状态对应的数量列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 统计指定立项下的重大变更数量。
     *
     * @param initiationId 立项 ID
     * @return 重大变更数量
     */
    Integer countMajorByInitiation(@Param("initiationId") String initiationId);
}
