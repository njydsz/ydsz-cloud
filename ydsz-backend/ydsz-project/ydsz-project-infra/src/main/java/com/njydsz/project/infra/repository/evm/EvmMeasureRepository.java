package com.njydsz.project.infra.repository.evm;

import com.njydsz.project.domain.entity.evm.EvmMeasureDO;
import com.njydsz.project.domain.repository.evm.IEvmMeasureRepository;
import com.njydsz.project.infra.mapper.evm.EvmMeasureMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * EvmMeasure Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class EvmMeasureRepository extends ServiceImpl<EvmMeasureMapper, EvmMeasureDO>
        implements IEvmMeasureRepository {
}
