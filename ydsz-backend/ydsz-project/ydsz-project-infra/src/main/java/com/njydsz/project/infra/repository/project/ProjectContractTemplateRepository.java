package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.domain.repository.project.IProjectContractTemplateRepository;
import com.njydsz.project.infra.mapper.project.ProjectContractTemplateMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectContractTemplate Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectContractTemplateRepository extends ServiceImpl<ProjectContractTemplateMapper, ProjectContractTemplate>
        implements IProjectContractTemplateRepository {
}
