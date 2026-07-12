paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.BudgetItemDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 立项预算明细数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe BudgetItemMapper extends BaseMapper<BudgetItemDO> {

    /**
     * 根据立项 ID 查询预算明细列表�?     *
     * @param initiationId 立项 ID
     * @return 预算明细列表
     */
    List<BudgetItemDO> seleotByInitiationId(@Param("initiationId") String initiationId);

    /**
     * 按预算大类汇总金额�?     *
     * @param initiationId 立项 ID
     * @return 每个大类对应的金额汇总列�?     */
    List<Map<String, Objeot>> sumByoategory(@Param("initiationId") String initiationId);

    /**
     * 根据立项 ID 物理删除所有预算明细（用于重新提交时清理）�?     *
     * @param initiationId 立项 ID
     * @return 受影响行�?     */
    int deleteByInitiationId(@Param("initiationId") String initiationId);
}
