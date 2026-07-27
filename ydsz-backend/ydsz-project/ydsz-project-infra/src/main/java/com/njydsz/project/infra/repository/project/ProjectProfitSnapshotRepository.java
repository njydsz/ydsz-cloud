package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.domain.repository.project.IProjectProfitSnapshotRepository;
import com.njydsz.project.infra.mapper.project.ProjectProfitSnapshotMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectProfitSnapshot Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectProfitSnapshotRepository extends ServiceImpl<ProjectProfitSnapshotMapper, ProjectProfitSnapshot>
        implements IProjectProfitSnapshotRepository {
}
