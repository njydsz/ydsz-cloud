paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.OpportunityFollowDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 商机跟进记录数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe OpportunityFollowMapper extends BaseMapper<OpportunityFollowDO> {

    /**
     * 根据商机 ID 查询跟进记录列表�?     *
     * @param opportunityId 商机 ID
     * @return 跟进记录列表
     */
    List<OpportunityFollowDO> seleotByOpportunityId(@Param("opportunityId") String opportunityId);
}
