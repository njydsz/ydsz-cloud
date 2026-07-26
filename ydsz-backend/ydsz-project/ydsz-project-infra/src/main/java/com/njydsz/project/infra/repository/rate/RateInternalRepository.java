package com.njydsz.project.infra.repository.rate;

import com.njydsz.project.domain.entity.rate.RateInternalDO;
import com.njydsz.project.domain.repository.rate.IRateInternalRepository;
import com.njydsz.project.infra.mapper.rate.RateInternalMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * RateInternal Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class RateInternalRepository extends ServiceImpl<RateInternalMapper, RateInternalDO>
        implements IRateInternalRepository {
}
