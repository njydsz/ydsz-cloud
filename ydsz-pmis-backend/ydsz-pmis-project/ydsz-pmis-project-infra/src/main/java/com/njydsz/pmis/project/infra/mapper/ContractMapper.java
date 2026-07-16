package com.njydsz.pmis.project.infra.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.ContractDO;

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
    int adjustTotalAmount(@Param("id") String id, @Param("delta") BigDecimal delta);

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

    /**
     * 统计所有合同总金额。
     *
     * @return 合同总金额合计
     */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM pmis_project_contract WHERE deleted = 0")
    BigDecimal sumAllAmount();

    /**
     * 按立项 ID 统计合同金额。
     *
     * @param initiationId 立项 ID
     * @return 合同金额合计
     */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM pmis_project_contract " +
            "WHERE deleted = 0 AND initiation_id = #{initiationId}")
    BigDecimal sumByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按客户统计合同金额。
     *
     * @return 每个客户的合同金额列表
     */
    @Select("SELECT customer_id AS customerId, customer_name AS customerName, " +
            "COALESCE(SUM(total_amount), 0) AS totalAmount, COUNT(*) AS contractCount " +
            "FROM pmis_project_contract WHERE deleted = 0 " +
            "GROUP BY customer_id, customer_name ORDER BY totalAmount DESC")
    List<Map<String, Object>> sumByCustomer();

    /**
     * 按年度统计合同金额。
     *
     * @return 每年的合同金额列表
     */
    @Select("SELECT EXTRACT(YEAR FROM sign_date) AS year, " +
            "COALESCE(SUM(total_amount), 0) AS totalAmount, COUNT(*) AS contractCount " +
            "FROM pmis_project_contract WHERE deleted = 0 " +
            "GROUP BY EXTRACT(YEAR FROM sign_date) ORDER BY year DESC")
    List<Map<String, Object>> sumByYear();

    /**
     * 按最近 N 个月统计合同金额。
     *
     * @param limit 返回月份数量
     * @return 每月合同金额列表
     */
    @Select("SELECT TO_CHAR(sign_date, 'YYYY-MM') AS month, " +
            "COALESCE(SUM(total_amount), 0) AS totalAmount, COUNT(*) AS contractCount " +
            "FROM pmis_project_contract WHERE deleted = 0 " +
            "GROUP BY TO_CHAR(sign_date, 'YYYY-MM') " +
            "ORDER BY month DESC LIMIT #{limit}")
    List<Map<String, Object>> sumByRecentMonth(@Param("limit") Integer limit);

    /**
     * 按项目类型统计合同金额。
     *
     * @return 每种项目类型的合同金额列表
     */
    @Select("SELECT contract_type AS contractType, " +
            "COALESCE(SUM(total_amount), 0) AS totalAmount, COUNT(*) AS contractCount " +
            "FROM pmis_project_contract WHERE deleted = 0 " +
            "GROUP BY contract_type ORDER BY totalAmount DESC")
    List<Map<String, Object>> sumByProjectType();
}
