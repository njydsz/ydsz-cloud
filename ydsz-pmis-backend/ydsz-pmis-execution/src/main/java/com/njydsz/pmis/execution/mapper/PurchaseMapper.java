package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.PurchaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 采购 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface PurchaseMapper extends BaseMapper<PurchaseDO> {

    /**
     * 按编码查询采购记录
     *
     * @param code 采购编码
     * @return 采购对象，未找到返回 null
     */
    PurchaseDO selectByCode(@Param("code") String code);

    /**
     * 更新采购状态
     *
     * @param id           采购 ID
     * @param status       目标状态
     * @param approverId   审批人 ID
     * @param approverName 审批人姓名
     * @return 受影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId, @Param("approverName") String approverName);

    /**
     * 跨项目汇总所有采购金额
     *
     * @return 采购总金额
     */
    BigDecimal sumAllAmount();

    /**
     * 按项目汇总「已发生」采购金额（强管控用）
     *
     * @param initiationId 立项 ID
     * @return 项目采购总金额
     */
    BigDecimal sumByInitiation(@Param("initiationId") Long initiationId);
}
