package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ContractDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 合同数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ContractMapper extends BaseMapper<ContractDO> {

    /**
     * 根据合同编号查询合同。
     *
     * @param code 合同编号
     * @return 合同实体；不存在返回 null
     */
    ContractDO selectByCode(@Param("code") String code);

    /**
     * 更新合同状态。
     *
     * @param id     合同 ID
     * @param status 目标状态码（ContractStatus.code）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 调整合同总金额（用于补充协议生效后累计变更）。
     *
     * @param id    合同 ID
     * @param delta 变更金额（正=增加，负=减少）
     * @return 受影响行数
     */
    int adjustTotalAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 按状态聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种状态对应的数量列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("tenantId") String tenantId);

    /**
     * 按风险等级聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种风险等级对应的数量列表
     */
    List<Map<String, Object>> aggregateByRisk(@Param("tenantId") String tenantId);

    /**
     * 统计指定状态的合同数量。
     *
     * @param status   状态码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long countByStatus(@Param("status") String status, @Param("tenantId") String tenantId);
}
