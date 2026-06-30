package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OpportunityFollowMapper extends BaseMapper<OpportunityFollowDO> {

    List<OpportunityFollowDO> selectByOpportunityId(@Param("opportunityId") Long opportunityId);
}
