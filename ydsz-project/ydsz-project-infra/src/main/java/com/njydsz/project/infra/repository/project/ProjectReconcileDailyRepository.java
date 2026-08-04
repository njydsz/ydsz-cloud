package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;
import com.njydsz.project.domain.repository.project.IProjectReconcileDailyRepository;
import com.njydsz.project.infra.mapper.project.ProjectReconcileDailyMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectReconcileDaily Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectReconcileDailyRepository extends ServiceImpl<ProjectReconcileDailyMapper, ProjectReconcileDaily>
        implements IProjectReconcileDailyRepository {
}
