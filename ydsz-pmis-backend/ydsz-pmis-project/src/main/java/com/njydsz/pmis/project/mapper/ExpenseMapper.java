package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.ExpenseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 项目费用 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ExpenseMapper extends BaseMapper<ExpenseDO> {

    /**
     * 按编码查询费用记录
     *
     * @param code 费用编码
     * @return 费用对象，未找到返回 null
     */
    ExpenseDO selectByCode(@Param("code") String code);

    /**
     * 更新费用状态
     *
     * @param id           费用 ID
     * @param status       目标状态
     * @param approverId   审批人 ID
     * @param approverName 审批人姓名
     * @return 受影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approverId") Long approverId, @Param("approverName") String approverName);

    /**
     * 跨项目汇总所有费用金额
     *
     * @return 费用总金额
     */
    BigDecimal sumAllAmount();

    /**
     * 按项目汇总「已发生」费用金额（强管控用）
     *
     * @param initiationId 立项 ID
     * @return 项目费用总金额
     */
    BigDecimal sumByInitiation(@Param("initiationId") Long initiationId);
}
