package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.PartTimeRateCreateDTO;
import com.njydsz.pmis.userinfo.dto.PartTimeRateUpdateDTO;
import com.njydsz.pmis.userinfo.entity.PartTimeRateDO;
import com.njydsz.pmis.userinfo.mapper.PartTimeRateMapper;
import com.njydsz.pmis.userinfo.service.PartTimeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 兼职工时单价服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartTimeRateServiceImpl implements PartTimeRateService {

    /** 合法的级别段位 */
    private static final Set<String> VALID_SEGMENTS =
            Set.of("PRIMARY", "MIDDLE", "SENIOR", "EXPERT", "STRATEGIC");

    /** 默认状态 */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认版本号 */
    private static final int DEFAULT_VERSION = 1;

    /** 兼职工时单价 Mapper */
    private final PartTimeRateMapper partTimeRateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(PartTimeRateCreateDTO dto) {
        validateCreate(dto);
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus(DEFAULT_STATUS);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(DEFAULT_VERSION);
        }
        partTimeRateMapper.insert(entity);
        log.info("[PartTimeRate] 创建兼职费率: code={} version={}", entity.getRateCode(), entity.getVersion());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, PartTimeRateUpdateDTO dto) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职工时单价 ID 不能为空");
        }
        PartTimeRateDO exists = partTimeRateMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职工时单价不存在: " + id);
        }
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职工时单价参数不能为空");
        }
        if (dto.getHourlyRate() != null && dto.getHourlyRate().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "工作日时薪必须大于 0");
        }
        if (StringUtils.hasText(dto.getSegment()) && !VALID_SEGMENTS.contains(dto.getSegment())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "级别段位非法: " + dto.getSegment());
        }
        // 日期校验：合并已有字段后判断失效日期不早于生效日期
        LocalDate effective = dto.getEffectiveDate() != null ? dto.getEffectiveDate() : exists.getEffectiveDate();
        LocalDate expire = dto.getExpireDate() != null ? dto.getExpireDate() : exists.getExpireDate();
        if (expire != null && expire.isBefore(effective)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验（排除自身）
        String code = dto.getRateCode() != null ? dto.getRateCode() : exists.getRateCode();
        Integer ver = dto.getVersion() != null ? dto.getVersion() : exists.getVersion();
        PartTimeRateDO dup = partTimeRateMapper.selectOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateCode, code)
                .eq(PartTimeRateDO::getVersion, ver)
                .ne(PartTimeRateDO::getId, id));
        if (dup != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "兼职级别编码已存在: " + code);
        }
        PartTimeRateDO entity = new PartTimeRateDO();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        partTimeRateMapper.updateById(entity);
        log.info("[PartTimeRate] 更新兼职费率: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职工时单价 ID 不能为空");
        }
        if (partTimeRateMapper.selectById(id) == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职工时单价不存在: " + id);
        }
        partTimeRateMapper.deleteById(id);
        log.info("[PartTimeRate] 删除兼职费率: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public PartTimeRateDO getById(String id) {
        PartTimeRateDO rate = partTimeRateMapper.selectById(id);
        if (rate == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "兼职工时单价不存在: " + id);
        }
        return rate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartTimeRateDO> page(int page, int size, String keyword, String segment, String status) {
        Page<PartTimeRateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PartTimeRateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PartTimeRateDO::getRateCode, keyword)
                    .or().like(PartTimeRateDO::getRateName, keyword));
        }
        if (StringUtils.hasText(segment)) {
            w.eq(PartTimeRateDO::getSegment, segment);
        }
        if (StringUtils.hasText(status)) {
            w.eq(PartTimeRateDO::getStatus, status);
        }
        w.orderByAsc(PartTimeRateDO::getSortOrder).orderByDesc(PartTimeRateDO::getId);
        return partTimeRateMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public PartTimeRateDO matchEffective(String rateCode, LocalDate date) {
        if (!StringUtils.hasText(rateCode)) {
            return null;
        }
        if (date == null) {
            date = LocalDate.now();
        }
        return partTimeRateMapper.selectEffective(rateCode, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartTimeRateDO> listEffective(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return partTimeRateMapper.listEffective(date);
    }

    /**
     * 创建参数校验
     *
     * @param dto 创建参数
     */
    private void validateCreate(PartTimeRateCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职工时单价参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职级别编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRateName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "兼职级别名称不能为空");
        }
        if (dto.getHourlyRate() == null || dto.getHourlyRate().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "工作日时薪必须大于 0");
        }
        validateSegment(dto.getSegment());
        if (dto.getEffectiveDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "生效日期不能为空");
        }
        if (dto.getExpireDate() != null && dto.getExpireDate().isBefore(dto.getEffectiveDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "失效日期不能早于生效日期");
        }
        // rate_code + version 唯一性校验
        Integer version = dto.getVersion() != null ? dto.getVersion() : DEFAULT_VERSION;
        PartTimeRateDO dup = partTimeRateMapper.selectOne(new LambdaQueryWrapper<PartTimeRateDO>()
                .eq(PartTimeRateDO::getRateCode, dto.getRateCode())
                .eq(PartTimeRateDO::getVersion, version));
        if (dup != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "兼职级别编码已存在: " + dto.getRateCode());
        }
    }

    /**
     * 校验级别段位合法性
     *
     * @param segment 级别段位
     */
    private void validateSegment(String segment) {
        if (!StringUtils.hasText(segment) || !VALID_SEGMENTS.contains(segment)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "级别段位非法: " + segment);
        }
    }
}
