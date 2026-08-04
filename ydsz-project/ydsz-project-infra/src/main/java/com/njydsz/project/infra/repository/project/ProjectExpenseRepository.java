package com.njydsz.project.infra.repository.project;

import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.domain.repository.project.IProjectExpenseRepository;
import com.njydsz.project.infra.mapper.project.ProjectExpenseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ProjectExpense Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ProjectExpenseRepository extends ServiceImpl<ProjectExpenseMapper, ProjectExpense>
        implements IProjectExpenseRepository {
}
