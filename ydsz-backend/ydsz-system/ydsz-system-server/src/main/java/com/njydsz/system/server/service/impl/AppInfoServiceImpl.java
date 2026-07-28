package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.AppInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用注册 Service 实现
 *
 * <p>实现 {@link AppInfoService} 接口，封装应用注册的 CRUD、密钥校验、分页查询等能力。
 * 集成 BCrypt 密钥加密（强度可配置）、Micrometer 指标和行级数据权限过滤。
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>{@code appSecret} 字段在保存时自动 BCrypt 加密后存储，VO 不暴露密钥哈希</li>
 *   <li>{@code appKey} 字段保存明文，用于客户端身份标识（必须唯一）</li>
 *   <li>密钥校验结果同时上报 Micrometer 指标（成功/失败计数）</li>
 *   <li>更新时密钥非空才更新；为空时设为 null，MyBatis-Plus NOT_NULL 策略会跳过该字段，保持原密钥不变</li>
 * </ul>
 *
 * <p><b>数据权限：</b>读接口（{@code page / list}）启用 {@code @DataScope} 限制部门+创建人可见。
 *
 * @author ydsz-team
 * @since 1.0.0
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

    @Override
    public AppInfoVO getById(String id) {
        AppInfo entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 校验应用密钥。
     *
     * <p>通过 BCrypt 校验 {@code appSecret} 与数据库存储的密钥哈希是否匹配。
     * 校验结果同时上报 Micrometer 指标（成功/失败计数）。
     *
     * @param appKey    应用 Key（明文，对应 appKey 列）
     * @param appSecret 应用密钥明文
     * @return true-校验通过；false-应用不存在/未启用/密钥不匹配
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
        List<AppInfoVO> vos = page.getRecords().stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
        Page<AppInfoVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<AppInfoVO> list() {
        return mapper.selectList(null).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

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

    /**
     * DTO 转 Entity。
     *
     * <p>缺省 status = {@code "ENABLED"}，保证新创建的应用默认可用。
     * 注意：本方法不处理密钥加密，由调用方在 save / updateById 中按需加密。
     *
     * @param dto 应用 DTO
     * @return 应用 Entity
     */
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
