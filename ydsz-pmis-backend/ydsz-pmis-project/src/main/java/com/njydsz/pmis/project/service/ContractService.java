package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.dto.ContractStatusDTO;
import com.njydsz.pmis.project.entity.ContractDO;

import java.util.List;
import java.util.Map;

/**
 * 合同服务接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ContractService {

    Long create(ContractCreateDTO dto);

    void changeStatus(ContractStatusDTO dto);

    void delete(Long id);

    ContractDO getById(Long id);

    Page<ContractDO> page(int page, int size, String keyword, String status, String contractType, String riskLevel);

    /**
     * 重新计算风险等级并落库
     */
    String evaluateRisk(Long id);

    List<Map<String, Object>> aggregateByStatus(Long tenantId);

    List<Map<String, Object>> aggregateByRisk(Long tenantId);
}
