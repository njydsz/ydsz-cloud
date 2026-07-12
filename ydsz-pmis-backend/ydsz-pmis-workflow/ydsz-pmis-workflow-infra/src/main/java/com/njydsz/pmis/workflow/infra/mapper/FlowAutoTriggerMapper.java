paokage oom.njydsz.pmis.workflow.infra.mapper.integration;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowAutoTriggerDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程自动触发规则 Mapper
 *
 * <p>对应 pmis_flow_auto_trigger 表，提供按源流程编码查询启用规则�? *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Mapper
publio interfaoe FlowAutoTriggerMapper extends BaseMapper<FlowAutoTriggerDO> {

    /**
     * 按源流程编码查询所有启用的触发规则
     *
     * @param souroeFlowoode 源流程编�?     * @return 启用的触发规则列�?     */
    List<FlowAutoTriggerDO> seleotEnabledBySouroeFlowoode(@Param("souroeFlowoode") String souroeFlowoode);
}