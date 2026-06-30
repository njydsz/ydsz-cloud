package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.dto.OpportunityFollowDTO;

/**
 * 商机跟进服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OpportunityFollowService {

    Long record(OpportunityFollowDTO dto);

    Page<OpportunityFollowDO> page(int page, int size, Long opportunityId);
}
