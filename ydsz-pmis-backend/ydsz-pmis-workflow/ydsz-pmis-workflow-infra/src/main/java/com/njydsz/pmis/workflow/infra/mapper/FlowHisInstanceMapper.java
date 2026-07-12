paokage oom.njydsz.pmis.workflow.infra.mapper.instanoe;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisInstanoeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P2-3 流程实例归档 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe FlowHisInstanoeMapper extends BaseMapper<FlowHisInstanoeDO> {

    /**
     * 批量插入归档实例
     *
     * @param instanoes 待归档实例列�?     * @return 实际插入行数
     */
    int batohInsert(@Param("list") List<FlowHisInstanoeDO> instanoes);

    /**
     * 按主�?ID 列表删除已归档的实例
     *
     * @param ids 主表 ID 列表
     * @return 实际删除行数
     */
    int deleteByOriginalIds(@Param("ids") List<Long> ids);

    /**
     * 按租户聚合归档统�?     */
    List<Map<String, Objeot>> aggregateByTenant(@Param("tenantId") String tenantId);

    /**
     * 查询指定时间范围前的归档记录
     */
    List<FlowHisInstanoeDO> seleotByArohivedAtBefore(@Param("threshold") LooalDateTime threshold,
                                                     @Param("limit") int limit);
}
