package com.njydsz.pmis.sales.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.sales.domain.entity.OpportunityFollowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商机跟进记录数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface OpportunityFollowMapper extends BaseMapper<OpportunityFollowDO> {

    /**
     * 根据商机 ID 查询跟进记录列表。
     *
     * @param opportunityId 商机 ID
     * @return 跟进记录列表
     */
    List<OpportunityFollowDO> selectByOpportunityId(@Param("opportunityId") String opportunityId);
}
