package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.domain.repository.project.IProjectOpportunityRepository;
import com.njydsz.project.infra.mapper.project.ProjectOpportunityMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectOpportunity Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectOpportunityRepository extends ServiceImpl<ProjectOpportunityMapper, ProjectOpportunity>
        implements IProjectOpportunityRepository {
}
