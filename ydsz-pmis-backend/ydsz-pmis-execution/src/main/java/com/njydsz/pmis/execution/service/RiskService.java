package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.RiskCreateDTO;
import com.njydsz.pmis.execution.dto.RiskStatusDTO;
import com.njydsz.pmis.execution.entity.RiskDO;

import java.util.List;
import java.util.Map;

/**
 * 项目风险服务
 */
public interface RiskService {

    Long create(RiskCreateDTO dto);

    void changeStatus(RiskStatusDTO dto);

    void delete(Long id);

    RiskDO getById(Long id);

    Page<RiskDO> page(int page, int size, String keyword, String status,
                      String riskLevel, Long initiationId);

    List<RiskDO> listByInitiation(Long initiationId);

    List<Map<String, Object>> aggregateByLevel(Long initiationId);
}
