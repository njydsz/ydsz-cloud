package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值测量服务
 */
public interface EvmMeasureService {

    /** 录入或更新 EVM 测量（按 initiation+wbs+period 唯一） */
    Long save(EvmMeasureCreateDTO dto);

    EvmMeasureDO getById(Long id);

    List<EvmMeasureDO> listByInitiation(Long initiationId);

    List<EvmMeasureDO> listByWbs(Long wbsTaskId);

    /** WBS 节点级偏差趋势 */
    List<Map<String, Object>> trend(Long initiationId);

    /** 项目 EVM 健康汇总（最新一期） */
    Map<String, Object> dashboard(Long initiationId);

    Page<EvmMeasureDO> page(int page, int size, Long initiationId, String alertLevel);

    void delete(Long id);
}
