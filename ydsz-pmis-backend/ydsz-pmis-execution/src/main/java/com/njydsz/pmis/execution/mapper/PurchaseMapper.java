package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.PurchaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface PurchaseMapper extends BaseMapper<PurchaseDO> {

    PurchaseDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId, @Param("approverName") String approverName);

    /** 跨项目汇总所有采购金额 */
    BigDecimal sumAllAmount();

    /**
     * 按项目汇总「已发生」采购金额（强管控用）
     */
    BigDecimal sumByInitiation(@Param("initiationId") Long initiationId);
}
