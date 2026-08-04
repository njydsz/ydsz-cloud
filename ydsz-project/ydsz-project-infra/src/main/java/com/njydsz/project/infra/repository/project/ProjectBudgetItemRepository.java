package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.domain.repository.project.IProjectBudgetItemRepository;
import com.njydsz.project.infra.mapper.project.ProjectBudgetItemMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectBudgetItem Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectBudgetItemRepository extends ServiceImpl<ProjectBudgetItemMapper, ProjectBudgetItem>
        implements IProjectBudgetItemRepository {
}
