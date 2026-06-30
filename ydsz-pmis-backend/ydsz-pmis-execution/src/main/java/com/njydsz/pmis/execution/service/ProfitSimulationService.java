package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.execution.dto.SimulationStatusDTO;
import com.njydsz.pmis.execution.entity.ProfitSimulationDO;

import java.util.List;
import java.util.Map;

/**
 * 利润测算服务
 */
public interface ProfitSimulationService {

    Long create(ProfitSimulationCreateDTO dto);

    void changeStatus(SimulationStatusDTO dto);

    void delete(Long id);

    ProfitSimulationDO getById(Long id);

    List<ProfitSimulationDO> listByInitiation(Long initiationId);

    /** 多版本对比 */
    List<Map<String, Object>> compare(Long initiationId);

    Page<ProfitSimulationDO> page(int page, int size, Long initiationId, String scenarioType, String status);
}
