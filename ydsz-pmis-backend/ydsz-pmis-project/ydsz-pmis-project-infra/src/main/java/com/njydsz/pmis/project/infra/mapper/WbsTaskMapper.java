package com.njydsz.pmis.project.infra.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.WbsTaskDO;

/**
 * WBS 任务 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface WbsTaskMapper extends BaseMapper<WbsTaskDO> {

    /**
     * 按任务编码查询 WBS 任务
     *
     * @param code 任务编码
     * @return WBS 任务对象，未找到返回 null
     */
    WbsTaskDO selectByCode(@Param("code") String code);

    /**
     * 更新任务状态
     *
     * @param id     任务 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新任务进度
     *
     * @param id            任务 ID
     * @param progressPct   进度百分比
     * @param actualEffort  实际工时
     * @return 受影响行数
     */
    int updateProgress(@Param("id") String id, @Param("progressPct") BigDecimal progressPct,
                       @Param("actualEffort") BigDecimal actualEffort);

    /**
     * 按立项 ID 查询 WBS 任务列表
     *
     * @param initiationId 立项 ID
     * @return WBS 任务列表
     */
    List<WbsTaskDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 查询子任务列表
     *
     * @param parentId 父任务 ID
     * @return 子任务列表
     */
    List<WbsTaskDO> selectChildren(@Param("parentId") String parentId);

    /**
     * 查询里程碑任务列表
     *
     * @param initiationId 立项 ID
     * @return 里程碑任务列表
     */
    List<WbsTaskDO> selectMilestones(@Param("initiationId") String initiationId);

    /**
     * 按状态聚合同一立项下的任务计数
     *
     * @param initiationId 立项 ID
     * @return 状态聚合结果列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") String initiationId);
}
