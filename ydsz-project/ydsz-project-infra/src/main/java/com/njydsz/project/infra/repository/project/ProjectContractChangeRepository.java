package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.domain.repository.project.IProjectContractChangeRepository;
import com.njydsz.project.infra.mapper.project.ProjectContractChangeMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectContractChange Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectContractChangeRepository extends ServiceImpl<ProjectContractChangeMapper, ProjectContractChange>
        implements IProjectContractChangeRepository {
}
