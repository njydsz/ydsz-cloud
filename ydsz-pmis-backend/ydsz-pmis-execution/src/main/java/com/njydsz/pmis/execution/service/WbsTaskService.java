package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.execution.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.execution.entity.WbsTaskDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务服务
 */
public interface WbsTaskService {

    Long create(WbsTaskCreateDTO dto);

    void changeStatus(WbsTaskStatusDTO dto);

    /**
     * 更新进度（包含实际工时）
     */
    void updateProgress(Long id, BigDecimal progressPct, BigDecimal actualEffort);

    void delete(Long id);

    WbsTaskDO getById(Long id);

    Page<WbsTaskDO> page(int page, int size, String keyword, String status,
                         String taskType, Long initiationId, Long ownerId);

    List<WbsTaskDO> listByInitiation(Long initiationId);

    List<WbsTaskDO> listMilestones(Long initiationId);

    /**
     * 计算整体进度（任务加权平均）
     */
    BigDecimal calcOverallProgress(Long initiationId);

    /**
     * 状态分布
     */
    List<Map<String, Object>> aggregateByStatus(Long initiationId);
}
