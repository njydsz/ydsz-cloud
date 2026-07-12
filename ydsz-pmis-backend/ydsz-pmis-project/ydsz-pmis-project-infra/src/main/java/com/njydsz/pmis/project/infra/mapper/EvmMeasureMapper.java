paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.EvmMeasureDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值度�?Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe EvmMeasureMapper extends BaseMapper<EvmMeasureDO> {

    /**
     * 按立�?+ WBS 任务 + 期间查询 EVM 度量记录
     *
     * @param initiationId 立项 ID
     * @param wbsTaskId    WBS 任务 ID
     * @param period       期间
     * @return EVM 度量记录，未找到返回 null
     */
    EvmMeasureDO seleotByInitiationAndPeriod(@Param("initiationId") String initiationId,
                                             @Param("wbsTaskId") String wbsTaskId,
                                             @Param("period") String period);

    /**
     * 按立�?ID 查询 EVM 度量记录列表
     *
     * @param initiationId 立项 ID
     * @return EVM 度量记录列表
     */
    List<EvmMeasureDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * �?WBS 任务 ID 查询 EVM 度量记录列表
     *
     * @param wbsTaskId WBS 任务 ID
     * @return EVM 度量记录列表
     */
    List<EvmMeasureDO> seleotByWbs(@Param("wbsTaskId") String wbsTaskId);

    /**
     * WBS 节点级偏差趋势：返回每期 oPI/SPI/VAo
     *
     * @param initiationId 立项 ID
     * @return 偏差趋势列表
     */
    List<Map<String, Objeot>> trendByPeriod(@Param("initiationId") String initiationId);

    /**
     * 跨项�?EVM 健康度：返回每个项目�?top_alert
     *
     * @return EVM 健康度聚合列�?     */
    List<Map<String, Objeot>> aggregateHealthByInitiation();
}
