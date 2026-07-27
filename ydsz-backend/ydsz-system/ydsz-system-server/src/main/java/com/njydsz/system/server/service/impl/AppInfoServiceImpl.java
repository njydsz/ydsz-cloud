package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.AppInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 应用注册 Service 实现。
 *
 * <p>集成 BCrypt 密钥校验（强度可配置）、Micrometer 指标。
 * appSecret 字段在保存时自动 BCrypt 加密，VO 不暴露密钥哈希。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInfoServiceImpl implements AppInfoService {

    /** 应用注册 Mapper */
    private final AppInfoMapper mapper;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;
    /** BCrypt 密码编码器，用于 appSecret 加密存储 */
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     */
    @Override
    public AppInfoVO getById(String id) {
        AppInfo entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>通过 BCrypt 校验 appSecret 与数据库存储的密钥哈希是否匹配。
     * 校验结果同时上报 Micrometer 指标（成功/失败计数）。
     *
     * @param appKey   应用 Key
     * @param appSecret 应用密钥明文
     * @return true 表示校验通过
     */
    @Override
    public boolean validateClient(String appKey, String appSecret) {
        AppInfo app = mapper.selectEnabledByAppKey(appKey);
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
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public IPage<AppInfoVO> page(int pageNum, int pageSize, String appName, String status) {
        QueryWrapper<AppInfo> wrapper = new QueryWrapper<>();
        if (appName != null && !appName.isBlank()) {
            wrapper.like("app_name", appName);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<AppInfo> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AppInfoVO> vos = page.getRecords().stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
        Page<AppInfoVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<AppInfoVO> list() {
        return mapper.selectList(null).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 appKey 唯一性校验，appSecret 非空时自动 BCrypt 加密后存储。
     *
     * @throws IllegalArgumentException 当 appKey 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(AppInfoDTO dto) {
        // 唯一性校验：appKey 不能重复
        QueryWrapper<AppInfo> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("app_key", dto.getAppKey());
        if (mapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("应用 Key 已存在: " + dto.getAppKey());
        }
        AppInfo entity = toEntity(dto);
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
            entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
        }
        mapper.insert(entity);
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>appSecret 非空时 BCrypt 加密后更新；为空时跳过密钥字段（设为 null，
     * MyBatis-Plus NOT_NULL 策略自动跳过），保持原密钥不变。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AppInfoDTO dto) {
        AppInfo entity = toEntity(dto);
        if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
            entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
        } else {
            // 不更新密钥时设为 null，MyBatis-Plus NOT_NULL 策略会跳过此字段
            entity.setAppSecret(null);
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    private AppInfo toEntity(AppInfoDTO dto) {
        AppInfo entity = new AppInfo();
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
