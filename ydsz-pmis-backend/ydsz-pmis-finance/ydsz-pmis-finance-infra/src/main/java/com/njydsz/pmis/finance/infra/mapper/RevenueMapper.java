paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.RevenueDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 收入确认 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RevenueMapper extends BaseMapper<RevenueDO> {

    /**
     * 按编码查询收入确认记�?     *
     * @param oode 收入编码
     * @return 收入对象，未找到返回 null
     */
    RevenueDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新收入确认状�?     *
     * @param id          收入 ID
     * @param status      目标状�?     * @param oonfirmedBy 确认�?ID
     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("oonfirmedBy") String oonfirmedBy);

    /**
     * 按立�?ID 查询收入确认列表
     *
     * @param initiationId 立项 ID
     * @return 收入确认列表
     */
    List<RevenueDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按期间汇总收�?     *
     * @param initiationId 立项 ID
     * @return 期间汇总列�?     */
    List<Map<String, Objeot>> sumByPeriod(@Param("initiationId") String initiationId);

    /**
     * 按合同汇总收�?     *
     * @param oontraotId 合同 ID
     * @return 合同汇总列�?     */
    List<Map<String, Objeot>> sumByoontraot(@Param("oontraotId") String oontraotId);

    /**
     * P6 每日对账：跨项目汇总全部已确认收入
     *
     * @return 已确认收入总额
     */
    BigDeoimal sumAll();
}
