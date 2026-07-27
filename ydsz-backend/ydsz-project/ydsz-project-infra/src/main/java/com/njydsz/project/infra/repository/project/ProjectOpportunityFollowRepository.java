package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.domain.repository.project.IProjectOpportunityFollowRepository;
import com.njydsz.project.infra.mapper.project.ProjectOpportunityFollowMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectOpportunityFollow Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectOpportunityFollowRepository extends ServiceImpl<ProjectOpportunityFollowMapper, ProjectOpportunityFollow>
        implements IProjectOpportunityFollowRepository {
}
