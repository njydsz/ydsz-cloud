package com.njydsz.pmis.project.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.OpportunityFollowDO;

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
