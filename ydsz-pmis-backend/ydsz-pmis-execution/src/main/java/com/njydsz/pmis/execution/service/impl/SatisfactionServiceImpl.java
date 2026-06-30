package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.SatisfactionCreateDTO;
import com.njydsz.pmis.execution.engine.AfterSalesCodeGen;
import com.njydsz.pmis.execution.entity.SatisfactionDO;
import com.njydsz.pmis.execution.enums.SatisfactionLevel;
import com.njydsz.pmis.execution.mapper.SatisfactionMapper;
import com.njydsz.pmis.execution.service.SatisfactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 满意度评价服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SatisfactionServiceImpl implements SatisfactionService {

    private final SatisfactionMapper satisfactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(SatisfactionCreateDTO dto) {
        validate(dto);
        SatisfactionDO s = new SatisfactionDO();
        BeanUtils.copyProperties(dto, s);
        if (!StringUtils.hasText(s.getSurveyCode())) {
            s.setSurveyCode(AfterSalesCodeGen.surveyCode(LocalDate.now()));
        }
        if (s.getScore() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "总体评分不能为空");
        }
        SatisfactionLevel level = SatisfactionLevel.fromScore(s.getScore());
        if (level == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "评分必须为 1-5");
        }
        s.setLevel(level.getCode());
        if (s.getEvaluatedAt() == null) s.setEvaluatedAt(LocalDateTime.now());
        // 不满意 / 非常不满意 默认 followUp=true（提醒运营人员跟进）
        if (s.getFollowUp() == null) {
            s.setFollowUp(level == SatisfactionLevel.DISSATISFIED
                    || level == SatisfactionLevel.VERY_DISSATISFIED);
        }
        if (s.getAnonymous() == null) s.setAnonymous(false);
        if (s.getTenantId() == null) s.setTenantId(1L);
        satisfactionMapper.insert(s);
        log.info("[Satisfaction] 提交评价: code={} score={} level={} followUp={}",
                s.getSurveyCode(), s.getScore(), s.getLevel(), s.getFollowUp());
        return s.getId();
    }

    @Override
    public void markFollowUp(Long id, String note) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "评价 ID 不能为空");
        SatisfactionDO s = satisfactionMapper.selectById(id);
        if (s == null) throw new BizException(BizErrorCode.NOT_FOUND, "评价不存在");
        s.setFollowUp(true);
        if (StringUtils.hasText(note)) s.setFollowUpNote(note);
        satisfactionMapper.updateById(s);
    }

    @Override
    public void closeFollowUp(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "评价 ID 不能为空");
        SatisfactionDO s = satisfactionMapper.selectById(id);
        if (s == null) throw new BizException(BizErrorCode.NOT_FOUND, "评价不存在");
        s.setFollowUp(false);
        satisfactionMapper.updateById(s);
    }

    @Override
    public Map<String, Object> overall() {
        return satisfactionMapper.aggregateOverall();
    }

    @Override
    public List<Map<String, Object>> levelDistribution() {
        return satisfactionMapper.aggregateByLevel();
    }

    @Override
    public Page<SatisfactionDO> page(int page, int size, String level, Long initiationId, String keyword) {
        Page<SatisfactionDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SatisfactionDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(level)) w.eq(SatisfactionDO::getLevel, level);
        if (initiationId != null) w.eq(SatisfactionDO::getInitiationId, initiationId);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(SatisfactionDO::getSurveyCode, keyword)
                    .or().like(SatisfactionDO::getComments, keyword));
        }
        w.orderByDesc(SatisfactionDO::getEvaluatedAt);
        return satisfactionMapper.selectPage(p, w);
    }

    private void validate(SatisfactionCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (dto.getScore() == null || dto.getScore() < 1 || dto.getScore() > 5) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "评分必须为 1-5");
        }
        if (dto.getProfessionalism() != null && (dto.getProfessionalism() < 1 || dto.getProfessionalism() > 5)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "专业度评分 1-5");
        }
        if (dto.getTimeliness() != null && (dto.getTimeliness() < 1 || dto.getTimeliness() > 5)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "及时性评分 1-5");
        }
        if (dto.getQuality() != null && (dto.getQuality() < 1 || dto.getQuality() > 5)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "质量评分 1-5");
        }
        if (dto.getAttitude() != null && (dto.getAttitude() < 1 || dto.getAttitude() > 5)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "态度评分 1-5");
        }
    }
}
