package com.njydsz.pmis.sales.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.sales.domain.entity.OpportunityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商机数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OpportunityMapper extends BaseMapper<OpportunityDO> {

    /**
     * 根据商机编号查询商机。
     *
     * @param code 商机编号
     * @return 商机实体；不存在返回 null
     */
    OpportunityDO selectByCode(@Param("code") String code);

    /**
     * 按状态聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种状态对应的数量列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 按分级聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种分级对应的数量列表
     */
    List<Map<String, Object>> aggregateByLevel(@Param("tenantId") String tenantId);

    /**
     * 更新商机状态。
     *
     * @param id         商机 ID
     * @param status     目标状态码（OpportunityStatus.code）
     * @param lostReason 输单原因，可空
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("lostReason") String lostReason);

    /**
     * 统计指定状态的商机数量。
     *
     * @param status   状态码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long countByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
