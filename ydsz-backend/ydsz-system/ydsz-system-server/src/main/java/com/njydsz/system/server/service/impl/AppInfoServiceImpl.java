package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.entity.AppInfoDO;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.AppInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用注册 Service 实现。
 *
 * <p>集成 BCrypt 密钥校验、Micrometer 指标。appSecret 字段在保存时自动 BCrypt 加密。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInfoServiceImpl implements AppInfoService {

    private final AppInfoMapper mapper;
    private final SystemMetrics metrics;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public AppInfoVO getById(String id) {
        AppInfoDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public boolean validateClient(String appKey, String appSecret) {
        AppInfoDO app = mapper.selectEnabledByAppKey(appKey);
        if (app == null) {
            metrics.recordAppValidateFail();
            log.warn("应用校验失败: appKey={} 不存在或未启用", appKey);
            return false;
        }
        if (app.getAppSecret() == null || app.getAppSecret().isBlank()) {
            metrics.recordAppValidateFail();
            log.warn("应用校验失败: appKey={} 密钥为空", appKey);
            return false;
        }
        boolean matched = passwordEncoder.matches(appSecret, app.getAppSecret());
        if (matched) {
            metrics.recordAppValidateSuccess();
        } else {
            metrics.recordAppValidateFail();
            log.warn("应用校验失败: appKey={} 密钥不匹配", appKey);
        }
        return matched;
    }

    @Override
    public IPage<AppInfoDO> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    @Override
    public List<AppInfoDO> list() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(AppInfoDTO dto) {
        AppInfoDO entity = toEntity(dto);
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
            entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
        }
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppInfoDTO dto) {
        AppInfoDO entity = toEntity(dto);
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
            entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
        } else {
            entity.setAppSecret(null);
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    private AppInfoVO toVO(AppInfoDO entity) {
        if (entity == null) {
            return null;
        }
        AppInfoVO vo = new AppInfoVO();
        vo.setId(entity.getId());
        vo.setAppCode(entity.getAppCode());
        vo.setAppName(entity.getAppName());
        vo.setAppKey(entity.getAppKey());
        vo.setRedirectUrl(entity.getRedirectUrl());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private AppInfoDO toEntity(AppInfoDTO dto) {
        AppInfoDO entity = new AppInfoDO();
        entity.setId(dto.getId());
        entity.setAppCode(dto.getAppCode());
        entity.setAppName(dto.getAppName());
        entity.setAppKey(dto.getAppKey());
        entity.setAppSecret(dto.getAppSecret());
        entity.setRedirectUrl(dto.getRedirectUrl());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
