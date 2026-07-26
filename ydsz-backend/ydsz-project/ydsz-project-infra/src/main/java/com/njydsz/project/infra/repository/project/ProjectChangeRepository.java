package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectChangeDO;
import com.njydsz.project.domain.repository.project.IProjectChangeRepository;
import com.njydsz.project.infra.mapper.project.ProjectChangeMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectChange Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectChangeRepository extends ServiceImpl<ProjectChangeMapper, ProjectChangeDO>
        implements IProjectChangeRepository {
}
