package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectGateReviewDO;
import com.njydsz.project.domain.repository.project.IProjectGateReviewRepository;
import com.njydsz.project.infra.mapper.project.ProjectGateReviewMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectGateReview Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectGateReviewRepository extends ServiceImpl<ProjectGateReviewMapper, ProjectGateReviewDO>
        implements IProjectGateReviewRepository {
}
