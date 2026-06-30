package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.RevenueCreateDTO;
import com.njydsz.pmis.execution.entity.RevenueDO;

import java.util.List;
import java.util.Map;

/**
 * 收入确认服务
 */
public interface RevenueService {

    Long create(RevenueCreateDTO dto);

    void confirm(Long id, Long confirmedBy);

    void reverse(Long id);

    void delete(Long id);

    RevenueDO getById(Long id);

    Page<RevenueDO> page(int page, int size, String keyword, String status,
                          Long contractId, Long initiationId, String period);

    List<RevenueDO> listByInitiation(Long initiationId);

    /**
     * 按合同汇总
     */
    List<Map<String, Object>> sumByContract(Long contractId);

    /**
     * 按期间汇总
     */
    List<Map<String, Object>> sumByPeriod(Long initiationId);
}
