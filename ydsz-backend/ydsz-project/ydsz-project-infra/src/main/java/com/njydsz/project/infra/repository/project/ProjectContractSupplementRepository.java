package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.domain.repository.project.IProjectContractSupplementRepository;
import com.njydsz.project.infra.mapper.project.ProjectContractSupplementMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectContractSupplement Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectContractSupplementRepository extends ServiceImpl<ProjectContractSupplementMapper, ProjectContractSupplement>
        implements IProjectContractSupplementRepository {
}
