package com.njydsz.project.infra.repository.satisfaction;

import com.njydsz.project.domain.entity.satisfaction.SatisfactionDO;
import com.njydsz.project.domain.repository.satisfaction.ISatisfactionRepository;
import com.njydsz.project.infra.mapper.satisfaction.SatisfactionMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * Satisfaction Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class SatisfactionRepository extends ServiceImpl<SatisfactionMapper, SatisfactionDO>
        implements ISatisfactionRepository {
}
