package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.OpportunityDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商机服务接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OpportunityService {

    Long create(OpportunityCreateDTO dto);

    void update(OpportunityUpdateDTO dto);

    void changeStatus(OpportunityStatusDTO dto);

    void delete(Long id);

    OpportunityDO getById(Long id);

    Page<OpportunityDO> page(int page, int size, String keyword, String status, String level, Long ownerId);

    /**
     * 计算并返回赢率（带模型）
     */
    BigDecimal evaluateWinRate(Long id, String customerCredit, boolean hasHistory);

    /**
     * 状态分布
     */
    List<Map<String, Object>> aggregateByStatus(Long tenantId);

    /**
     * 分级分布
     */
    List<Map<String, Object>> aggregateByLevel(Long tenantId);
}
