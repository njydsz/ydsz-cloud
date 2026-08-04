package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.domain.repository.project.IProjectContractRepository;
import com.njydsz.project.infra.mapper.project.ProjectContractMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectContract Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectContractRepository extends ServiceImpl<ProjectContractMapper, ProjectContract>
        implements IProjectContractRepository {
}
