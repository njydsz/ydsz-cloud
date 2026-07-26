package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectRevenueDO;
import com.njydsz.project.domain.repository.project.IProjectRevenueRepository;
import com.njydsz.project.infra.mapper.project.ProjectRevenueMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectRevenue Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectRevenueRepository extends ServiceImpl<ProjectRevenueMapper, ProjectRevenueDO>
        implements IProjectRevenueRepository {
}
