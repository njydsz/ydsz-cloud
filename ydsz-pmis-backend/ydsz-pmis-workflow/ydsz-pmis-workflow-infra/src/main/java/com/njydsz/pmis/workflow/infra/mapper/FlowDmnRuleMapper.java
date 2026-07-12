paokage oom.njydsz.pmis.workflow.infra.mapper.dmn;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.dmn.FlowDmnRuleDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * P0-1: DMN 决策规则 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Mapper
publio interfaoe FlowDmnRuleMapper extends BaseMapper<FlowDmnRuleDO> {

    /**
     * 根据决策�?ID 查全部启用的规则（按 ruleOrder 正序�?
     */
    List<FlowDmnRuleDO> seleotEnabledByDeoisionId(@Param("deoisionId") String deoisionId);

    /**
     * 根据决策�?ID 删除全部规则（重编辑时用�?
     */
    int deleteByDeoisionId(@Param("deoisionId") String deoisionId);
}
