package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.BudgetItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 立项预算明细数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface BudgetItemMapper extends BaseMapper<BudgetItemDO> {

    /**
     * 根据立项 ID 查询预算明细列表。
     *
     * @param initiationId 立项 ID
     * @return 预算明细列表
     */
    List<BudgetItemDO> selectByInitiationId(@Param("initiationId") String initiationId);

    /**
     * 按预算大类汇总金额。
     *
     * @param initiationId 立项 ID
     * @return 每个大类对应的金额汇总列表
     */
    List<Map<String, Object>> sumByCategory(@Param("initiationId") String initiationId);

    /**
     * 根据立项 ID 物理删除所有预算明细（用于重新提交时清理）。
     *
     * @param initiationId 立项 ID
     * @return 受影响行数
     */
    int deleteByInitiationId(@Param("initiationId") String initiationId);
}
