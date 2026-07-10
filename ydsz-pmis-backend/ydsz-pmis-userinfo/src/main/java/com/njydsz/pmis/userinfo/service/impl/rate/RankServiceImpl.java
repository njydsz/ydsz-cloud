package com.njydsz.pmis.userinfo.service.impl.rate;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.entity.rate.RankDO;
import com.njydsz.pmis.userinfo.entity.rate.RankRateDO;
import com.njydsz.pmis.userinfo.mapper.rate.RankMapper;
import com.njydsz.pmis.userinfo.mapper.rate.RankRateMapper;
import com.njydsz.pmis.userinfo.service.rate.RankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 职级费率服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class RankServiceImpl implements RankService {

    private final RankMapper rankMapper;
    private final RankRateMapper rankRateMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RankDO> listAllLevels() {
        return rankMapper.selectAllEnabled();
    }

    @Override
    @Transactional(readOnly = true)
    public RankRateDO getEffectiveRate(String levelCode, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        RankRateDO rate = rankRateMapper.selectEffective(levelCode, date);
        if (rate == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_c23b2b34", levelCode);
        }
        return rate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RankRateDO> listAllVersions(String levelCode) {
        return rankRateMapper.selectAllVersions(levelCode);
    }
}
