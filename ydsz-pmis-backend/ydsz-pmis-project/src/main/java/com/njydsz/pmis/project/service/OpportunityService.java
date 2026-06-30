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

    /**
     * 商机转立项自动化：
     * <ol>
     *   <li>校验商机状态必须是 WON</li>
     *   <li>创建立项申请(草稿态 PRE_INITIATION)</li>
     *   <li>同步商机客户/金额/业主/预计周期到立项</li>
     *   <li>将商机状态推进到 CONVERTED</li>
     *   <li>返回新建立项 ID</li>
     * </ol>
     *
     * @param opportunityId 商机 ID
     * @param sponsorId     发起人 ID
     * @param pmId          项目经理 ID(可空)
     * @return 新建立项 ID
     */
    Long convertToInitiation(Long opportunityId, Long sponsorId, Long pmId);
}
