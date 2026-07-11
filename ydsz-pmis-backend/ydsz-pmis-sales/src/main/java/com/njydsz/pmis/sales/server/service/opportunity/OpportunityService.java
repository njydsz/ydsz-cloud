package com.njydsz.pmis.sales.server.service.opportunity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.sales.domain.dto.OpportunityCreateDTO;
import com.njydsz.pmis.sales.domain.dto.OpportunityStatusDTO;
import com.njydsz.pmis.sales.domain.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.sales.domain.entity.OpportunityDO;

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

    /**
     * 创建商机。
     *
     * @param dto 商机创建参数
     * @return 商机 ID
     */
    String create(OpportunityCreateDTO dto);

    /**
     * 更新商机信息。
     *
     * @param dto 商机更新参数
     */
    void update(OpportunityUpdateDTO dto);

    /**
     * 变更商机状态（遵循 OpportunityStatus 状态机）。
     *
     * @param dto 状态迁移参数
     */
    void changeStatus(OpportunityStatusDTO dto);

    /**
     * 删除商机（逻辑删除）。
     *
     * @param id 商机 ID
     */
    void delete(String id);

    /**
     * 根据商机 ID 查询商机详情。
     *
     * @param id 商机 ID
     * @return 商机实体；不存在返回 null
     */
    OpportunityDO getById(String id);

    /**
     * 分页查询商机列表。
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param keyword  关键词（商机编号/名称模糊匹配），可空
     * @param status   状态码，可空
     * @param level    分级（A/B/C），可空
     * @param ownerId  责任人 ID，可空
     * @return 分页结果
     */
    Page<OpportunityDO> page(int page, int size, String keyword, String status, String level, String ownerId);

    /**
     * 计算并返回赢率（带模型）。
     *
     * @param id             商机 ID
     * @param customerCredit 客户信用等级，可空
     * @param hasHistory     是否有历史合作
     * @return 赢单率（0-1）
     */
    BigDecimal evaluateWinRate(String id, String customerCredit, boolean hasHistory);

    /**
     * 状态分布。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种状态对应的数量列表
     */
    List<Map<String, Object>> aggregateByStatus(String tenantId);

    /**
     * 分级分布。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种分级对应的数量列表
     */
    List<Map<String, Object>> aggregateByLevel(String tenantId);

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
    String convertToInitiation(String opportunityId, String sponsorId, String pmId);
}
