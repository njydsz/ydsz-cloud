package com.njydsz.message.server.service.impl.canary;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.dto.canary.CanaryUpsertDTO;
import com.njydsz.message.domain.entity.canary.MsgCanary;
import com.njydsz.message.infra.mapper.canary.MsgCanaryMapper;
import com.njydsz.message.server.service.canary.CanaryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度桶服务实现。
 *
 * <p>按 canaryKey upsert；命中判定按 {@code Math.floorMod(canaryKey.hashCode() ^ bucketValue.hashCode(), 100) < percentage}。
 * upsert 时重算 bucketSelected（前 percentage 个桶号）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanaryServiceImpl implements CanaryService {

    /** 默认灰度桶总数 */
    private static final int DEFAULT_BUCKET_TOTAL = 100;

    /** 灰度配置 Mapper */
    private final MsgCanaryMapper msgCanaryMapper;

    @Override
    public MsgCanary upsert(CanaryUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getCanaryKey())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "灰度键不能为空");
        }
        int total = dto.getBucketTotal() == null || dto.getBucketTotal() <= 0 ? DEFAULT_BUCKET_TOTAL : dto.getBucketTotal();
        int percentage = dto.getPercentage() == null ? 0 : Math.max(0, Math.min(100, dto.getPercentage()));
        MsgCanary existing = msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanary>()
                .eq(MsgCanary::getCanaryKey, dto.getCanaryKey())
                .last("LIMIT 1"));
        String bucketSelected = buildBucketSelected(total, percentage);
        if (existing == null) {
            MsgCanary entity = new MsgCanary();
            entity.setCanaryKey(dto.getCanaryKey());
            entity.setBucketTotal(total);
            entity.setBucketSelected(bucketSelected);
            entity.setPercentage(percentage);
            entity.setExperimentTemplateCode(dto.getExperimentTemplateCode());
            entity.setExperimentChannel(dto.getExperimentChannel());
            entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "ENABLED");
            entity.setDescription(dto.getDescription());
            entity.setTenantId(TenantContext.getTenantId());
            msgCanaryMapper.insert(entity);
            log.info("[Canary] 新建灰度桶: key={} percentage={} expTpl={} expChan={}",
                    dto.getCanaryKey(), percentage, dto.getExperimentTemplateCode(), dto.getExperimentChannel());
            return entity;
        }
        existing.setBucketTotal(total);
        existing.setBucketSelected(bucketSelected);
        existing.setPercentage(percentage);
        existing.setExperimentTemplateCode(dto.getExperimentTemplateCode());
        existing.setExperimentChannel(dto.getExperimentChannel());
        if (StringUtils.hasText(dto.getStatus())) {
            existing.setStatus(dto.getStatus());
        }
        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }
        msgCanaryMapper.updateById(existing);
        return existing;
    }

    @Override
    public boolean hit(String canaryKey, String bucketValue) {
        return matchConfig(canaryKey, bucketValue) != null;
    }

    @Override
    public MsgCanary matchConfig(String canaryKey, String bucketValue) {
        if (!StringUtils.hasText(canaryKey) || !StringUtils.hasText(bucketValue)) {
            return null;
        }
        MsgCanary config = msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanary>()
                .eq(MsgCanary::getCanaryKey, canaryKey)
                .eq(MsgCanary::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (config == null || config.getPercentage() == null || config.getPercentage() <= 0) {
            return null;
        }
        int bucket = Math.floorMod(canaryKey.hashCode() ^ bucketValue.hashCode(), 100);
        return bucket < config.getPercentage() ? config : null;
    }

    @Override
    public MsgCanary getByKey(String canaryKey) {
        if (!StringUtils.hasText(canaryKey)) {
            return null;
        }
        return msgCanaryMapper.selectOne(new LambdaQueryWrapper<MsgCanary>()
                .eq(MsgCanary::getCanaryKey, canaryKey)
                .last("LIMIT 1"));
    }

    @Override
    public Page<MsgCanary> page(PageQuery query) {
        Page<MsgCanary> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        return msgCanaryMapper.selectPage(page, new LambdaQueryWrapper<MsgCanary>()
                .orderByDesc(MsgCanary::getCreatedAt));
    }

    /**
     * 构造命中桶列表 JSON（前 percentage 个桶号）。
     *
     * @param total      桶总数
     * @param percentage 灰度比例
     * @return 形如 [0,1,2] 的 JSON 字符串
     */
    private String buildBucketSelected(int total, int percentage) {
        int count = Math.min(percentage, total);
        List<Integer> buckets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buckets.add(i);
        }
        return YdszJson.toJson(buckets);
    }
}
