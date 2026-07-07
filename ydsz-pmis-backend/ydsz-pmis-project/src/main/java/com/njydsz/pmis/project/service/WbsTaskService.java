package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.project.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.project.entity.WbsTaskDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务服务
 *
 * <p>提供 WBS 任务的创建、状态变更、进度更新、查询与聚合统计能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface WbsTaskService {

    /**
     * 创建 WBS 任务
     *
     * @param dto 任务创建参数
     * @return 任务ID
     */
    String create(WbsTaskCreateDTO dto);

    /**
     * 变更任务状态
     *
     * @param dto 状态变更参数
     */
    void changeStatus(WbsTaskStatusDTO dto);

    /**
     * 更新进度（包含实际工时）
     *
     * @param id           任务ID
     * @param progressPct  进度百分比
     * @param actualEffort 实际工时（人天）
     */
    void updateProgress(String id, BigDecimal progressPct, BigDecimal actualEffort);

    /**
     * 删除任务
     *
     * @param id 任务ID
     */
    void delete(String id);

    /**
     * 根据ID查询任务
     *
     * @param id 任务ID
     * @return 任务实体
     */
    WbsTaskDO getById(String id);

    /**
     * 分页查询任务
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（任务名称/编号）
     * @param status       状态过滤
     * @param taskType     任务类型
     * @param initiationId 项目立项ID
     * @param ownerId      责任人ID
     * @return 分页结果
     */
    Page<WbsTaskDO> page(int page, int size, String keyword, String status,
                         String taskType, String initiationId, String ownerId);

    /**
     * 查询项目下所有任务
     *
     * @param initiationId 项目立项ID
     * @return 任务列表
     */
    List<WbsTaskDO> listByInitiation(String initiationId);

    /**
     * 查询项目下所有里程碑任务
     *
     * @param initiationId 项目立项ID
     * @return 里程碑任务列表
     */
    List<WbsTaskDO> listMilestones(String initiationId);

    /**
     * 计算整体进度（任务加权平均）
     *
     * @param initiationId 项目立项ID
     * @return 整体进度百分比（0-100）
     */
    BigDecimal calcOverallProgress(String initiationId);

    /**
     * 状态分布统计
     *
     * @param initiationId 项目立项ID
     * @return 各状态任务数量列表
     */
    List<Map<String, Object>> aggregateByStatus(String initiationId);
}
