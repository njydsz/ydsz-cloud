package com.njydsz.pmis.project.server.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.expr.VariableDefinition;
import com.njydsz.pmis.literule.expr.VariableRegistry;
import com.njydsz.pmis.literule.entity.RuleVariableDefDO;
import com.njydsz.pmis.literule.mapper.RuleVariableDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库变量注册表
 *
 * <p>从 {@code pmis_rule_variable_def} 表加载变量定义，实现 {@link VariableRegistry}。
 * 作为 {@link com.njydsz.pmis.literule.expr.ExpressionValidationService} 的变量空间数据源，
 * 启用 UNDEFINED_VARIABLE 校验和前端编辑器自动补全。
 *
 * <p>缓存策略：使用 {@link ConcurrentHashMap} + {@code volatile long lastLoadTime} 实现
 * 简单的 TTL 缓存（5 分钟），过期后下次访问触发重新加载；
 * {@link #register} / {@link #unregister} / {@link #refresh()} 会立即失效缓存。
 *
 * <p>线程安全：缓存引用为 volatile，读取走 double-check 锁定，写入时整体替换缓存引用。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseVariableRegistry implements VariableRegistry {

    /** 缓存 TTL：5 分钟 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 默认租户 ID（单租户部署） */
    private static final String DEFAULT_TENANT_ID = "1";

    private final RuleVariableDefMapper ruleVariableDefMapper;

    /** 缓存：varName -> VariableDefinition（仅 enabled=true） */
    private volatile Map<String, VariableDefinition> cache = new ConcurrentHashMap<>();

    /** 缓存最后加载时间戳；0 表示需要重新加载 */
    private volatile long lastLoadTime = 0L;

    @Override
    public VariableDefinition lookup(String name) {
        if (name == null) {
            return null;
        }
        ensureCacheFresh();
        return cache.get(name);
    }

    @Override
    public List<VariableDefinition> listAll() {
        ensureCacheFresh();
        return new ArrayList<>(cache.values());
    }

    @Override
    public boolean isEmpty() {
        ensureCacheFresh();
        return cache.isEmpty();
    }

    /**
     * 注册（upsert）变量定义到数据库，并刷新缓存
     *
     * <p>{@code enabled} 字段不在 {@link VariableDefinition} 中：
     * <ul>
     *   <li>新增：默认 enabled=true</li>
     *   <li>更新：保留原 enabled 值</li>
     * </ul>
     */
    @Override
    public void register(VariableDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("变量定义及 name 不能为空");
        }
        RuleVariableDefDO existing = ruleVariableDefMapper.selectByVarName(definition.getName());
        RuleVariableDefDO DO = toDO(definition);
        if (existing == null) {
            DO.setEnabled(true);
            DO.setTenantId(DEFAULT_TENANT_ID);
            DO.setCreatedBy("SYSTEM");
            DO.setCreatedAt(LocalDateTime.now());
            ruleVariableDefMapper.insert(DO);
            log.info("[VariableRegistry] 变量新增: name={}, type={}", definition.getName(), definition.getType());
        } else {
            DO.setId(existing.getId());
            DO.setEnabled(existing.getEnabled());
            DO.setTenantId(existing.getTenantId() != null ? existing.getTenantId() : DEFAULT_TENANT_ID);
            DO.setCreatedBy(existing.getCreatedBy());
            DO.setCreatedAt(existing.getCreatedAt());
            DO.setUpdatedBy("SYSTEM");
            DO.setUpdatedAt(LocalDateTime.now());
            ruleVariableDefMapper.updateById(DO);
            log.info("[VariableRegistry] 变量更新: name={}", definition.getName());
        }
        refresh();
    }

    /**
     * 按变量名删除（物理删除），并刷新缓存
     *
     * @param varName 变量名
     */
    public void unregister(String varName) {
        if (varName == null || varName.isBlank()) {
            return;
        }
        RuleVariableDefDO existing = ruleVariableDefMapper.selectByVarName(varName);
        if (existing == null) {
            log.warn("[VariableRegistry] 删除的变量不存在: name={}", varName);
            return;
        }
        ruleVariableDefMapper.deleteById(existing.getId());
        log.info("[VariableRegistry] 变量删除: name={}", varName);
        refresh();
    }

    /**
     * 手动刷新缓存（立即失效，下次访问重新加载）
     */
    public void refresh() {
        lastLoadTime = 0L;
    }

    // ==================== 缓存加载 ====================

    /**
     * 确保缓存未过期；过期则 double-check 锁定后重新加载
     */
    private void ensureCacheFresh() {
        if (System.currentTimeMillis() - lastLoadTime > CACHE_TTL_MS) {
            synchronized (this) {
                if (System.currentTimeMillis() - lastLoadTime > CACHE_TTL_MS) {
                    reloadCache();
                    lastLoadTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * 从数据库加载所有 enabled=true 的变量到缓存
     */
    private void reloadCache() {
        List<RuleVariableDefDO> list = ruleVariableDefMapper.selectList(
                new LambdaQueryWrapper<RuleVariableDefDO>()
                        .eq(RuleVariableDefDO::getEnabled, true));
        Map<String, VariableDefinition> newCache = new ConcurrentHashMap<>(list.size() * 2);
        for (RuleVariableDefDO DO : list) {
            VariableDefinition def = toDefinition(DO);
            if (def != null && def.getName() != null) {
                newCache.put(def.getName(), def);
            }
        }
        this.cache = newCache;
        log.debug("[VariableRegistry] 缓存已加载，共 {} 个变量", newCache.size());
    }

    // ==================== DO ↔ Definition 转换 ====================

    private VariableDefinition toDefinition(RuleVariableDefDO DO) {
        if (DO == null) {
            return null;
        }
        return VariableDefinition.builder()
                .name(DO.getVarName())
                .type(DO.getVarType())
                .description(DO.getDescription())
                .sampleValue(coerceSampleValue(DO.getSampleValue(), DO.getVarType()))
                .category(DO.getCategory())
                .required(Boolean.TRUE.equals(DO.getRequired()))
                .build();
    }

    private RuleVariableDefDO toDO(VariableDefinition def) {
        RuleVariableDefDO DO = new RuleVariableDefDO();
        DO.setVarName(def.getName());
        DO.setVarType(def.getType());
        DO.setDescription(def.getDescription());
        DO.setSampleValue(def.getSampleValue() != null ? String.valueOf(def.getSampleValue()) : null);
        DO.setCategory(def.getCategory());
        DO.setRequired(def.isRequired());
        return DO;
    }

    /**
     * 按 varType 将示例值字符串强转为对应 Java 类型，供 dryRun 默认 facts 使用
     *
     * <p>解析失败时保留原始字符串，避免阻断加载流程。
     */
    private Object coerceSampleValue(String sampleValue, String varType) {
        if (sampleValue == null || sampleValue.isBlank()) {
            return null;
        }
        if (varType == null) {
            return sampleValue;
        }
        String t = varType.toLowerCase();
        try {
            if (t.contains("boolean")) {
                return Boolean.parseBoolean(sampleValue);
            }
            if (t.contains("number") || t.contains("integer") || t.contains("long")
                    || t.contains("double") || t.contains("float") || t.contains("bigdecimal")) {
                if (sampleValue.contains(".") || sampleValue.contains("e") || sampleValue.contains("E")) {
                    return Double.parseDouble(sampleValue);
                }
                return Long.parseLong(sampleValue);
            }
        } catch (NumberFormatException e) {
            log.warn("[VariableRegistry] sampleValue 解析失败，保留原始字符串: value={}, type={}",
                    sampleValue, varType);
        }
        return sampleValue;
    }
}
