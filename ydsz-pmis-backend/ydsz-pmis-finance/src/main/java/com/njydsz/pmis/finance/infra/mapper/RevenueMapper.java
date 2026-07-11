package com.njydsz.pmis.finance.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.finance.domain.entity.RevenueDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 收入确认 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface RevenueMapper extends BaseMapper<RevenueDO> {

    /**
     * 按编码查询收入确认记录
     *
     * @param code 收入编码
     * @return 收入对象，未找到返回 null
     */
    RevenueDO selectByCode(@Param("code") String code);

    /**
     * 更新收入确认状态
     *
     * @param id          收入 ID
     * @param status      目标状态
     * @param confirmedBy 确认人 ID
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("confirmedBy") String confirmedBy);

    /**
     * 按立项 ID 查询收入确认列表
     *
     * @param initiationId 立项 ID
     * @return 收入确认列表
     */
    List<RevenueDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按期间汇总收入
     *
     * @param initiationId 立项 ID
     * @return 期间汇总列表
     */
    List<Map<String, Object>> sumByPeriod(@Param("initiationId") String initiationId);

    /**
     * 按合同汇总收入
     *
     * @param contractId 合同 ID
     * @return 合同汇总列表
     */
    List<Map<String, Object>> sumByContract(@Param("contractId") String contractId);

    /**
     * P6 每日对账：跨项目汇总全部已确认收入
     *
     * @return 已确认收入总额
     */
    BigDecimal sumAll();
}
