package com.njydsz.project.infra.repository.rate;

import com.njydsz.project.domain.entity.rate.RateCardDO;
import com.njydsz.project.domain.repository.rate.IRateCardRepository;
import com.njydsz.project.infra.mapper.rate.RateCardMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * RateCard Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class RateCardRepository extends ServiceImpl<RateCardMapper, RateCardDO>
        implements IRateCardRepository {
}
