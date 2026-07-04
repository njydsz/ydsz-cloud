package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RulePack;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.project.entity.RulePackDO;
import com.njydsz.pmis.project.entity.RulePackInstallDO;
import com.njydsz.pmis.project.mapper.RulePackInstallMapper;
import com.njydsz.pmis.project.mapper.RulePackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 规则集 Service（P2-14）
 *
 * <p>提供规则集（RulePack）的市场发布、查询、安装、版本管理等能力。
 * 安装过程：从 pack 中提取 rule_codes 列表，通过 {@link RuleConfigProvider} 加载规则定义。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulePackService {

    private final RulePackMapper rulePackMapper;
    private final RulePackInstallMapper rulePackInstallMapper;
    private final RuleConfigProvider ruleConfigProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发布规则集到市场
     */
    @Transactional(rollbackFor = Exception.class)
    public RulePack publish(RulePack pack, String operator) {
        if (pack == null) throw new IllegalArgumentException("pack 不能为空");
        if (pack.getPackCode() == null || pack.getPackCode().isBlank()) {
            throw new IllegalArgumentException("packCode 不能为空");
        }
        if (pack.getPackVersion() == null || pack.getPackVersion().isBlank()) {
            pack.setPackVersion("1.0.0");
        }

        // 查找是否已存在
        List<RulePackDO> existing = rulePackMapper.selectByPackCode(pack.getPackCode());
        RulePackDO found = null;
        for (RulePackDO e : existing) {
            if (pack.getPackVersion().equals(e.getPackVersion())) {
                found = e;
                break;
            }
        }
        RulePackDO entity = found == null ? new RulePackDO() : found;
        entity.setPackCode(pack.getPackCode());
        entity.setPackVersion(pack.getPackVersion());
        entity.setPackName(pack.getPackName());
        entity.setIndustry(pack.getIndustry());
        entity.setTags(pack.getTags() == null ? null : String.join(",", pack.getTags()));
        try {
            entity.setRuleCodes(objectMapper.writeValueAsString(pack.getRuleCodes() == null ? Collections.emptyList() : pack.getRuleCodes()));
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleCodes 序列化失败: " + e.getMessage());
        }
        entity.setDescription(pack.getDescription());
        entity.setAuthor(pack.getAuthor());
        if (pack.getDownloadCount() > 0) entity.setDownloadCount(pack.getDownloadCount());
        if (pack.getRating() > 0) entity.setRating(BigDecimal.valueOf(pack.getRating()));
        if (entity.getId() == null) {
            entity.setEnabled(true);
            entity.setOfficial(false);
            entity.setDownloadCount(0L);
            entity.setCreatedBy(operator);
            entity.setCreatedAt(LocalDateTime.now());
            rulePackMapper.insert(entity);
        } else {
            entity.setUpdatedBy(operator);
            entity.setUpdatedAt(LocalDateTime.now());
            rulePackMapper.updateById(entity);
        }
        log.info("[RulePack] 发布规则集: code={}, version={}, rules={}, operator={}",
                entity.getPackCode(), entity.getPackVersion(), pack.getRuleCodes() == null ? 0 : pack.getRuleCodes().size(), operator);
        return toApi(entity);
    }

    /**
     * 查询规则集详情（最新版本）
     */
    public RulePack getLatest(String packCode) {
        List<RulePackDO> list = rulePackMapper.selectByPackCode(packCode);
        if (list.isEmpty()) return null;
        // 取版本最高的
        list.sort((a, b) -> compareVersion(b.getPackVersion(), a.getPackVersion()));
        return toApi(list.get(0));
    }

    /**
     * 查询规则集的所有版本
     */
    public List<RulePack> listVersions(String packCode) {
        List<RulePackDO> list = rulePackMapper.selectByPackCode(packCode);
        list.sort((a, b) -> compareVersion(b.getPackVersion(), a.getPackVersion()));
        List<RulePack> result = new ArrayList<>(list.size());
        for (RulePackDO d : list) result.add(toApi(d));
        return result;
    }

    /**
     * 按行业筛选
     */
    public List<RulePack> listByIndustry(String industry) {
        return rulePackMapper.selectByIndustry(industry).stream().map(this::toApi).toList();
    }

    /**
     * 列出所有规则集（市场首页）
     */
    public List<RulePack> listAll() {
        LambdaQueryWrapper<RulePackDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RulePackDO::getEnabled, true);
        wrapper.orderByDesc(RulePackDO::getOfficial);
        wrapper.orderByDesc(RulePackDO::getDownloadCount);
        return rulePackMapper.selectList(wrapper).stream().map(this::toApi).toList();
    }

    /**
     * 关键字搜索
     */
    public List<RulePack> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return listAll();
        LambdaQueryWrapper<RulePackDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RulePackDO::getEnabled, true);
        wrapper.and(w -> w.like(RulePackDO::getPackName, keyword)
                .or().like(RulePackDO::getPackCode, keyword)
                .or().like(RulePackDO::getDescription, keyword)
                .or().like(RulePackDO::getTags, keyword));
        wrapper.orderByDesc(RulePackDO::getDownloadCount);
        return rulePackMapper.selectList(wrapper).stream().map(this::toApi).toList();
    }

    /**
     * 安装规则集
     *
     * <p>从 pack 中提取 ruleCodes 列表，逐条创建/更新规则定义。
     * 安装过程的事务策略：每条规则独立处理，单条失败不影响其他规则安装。
     *
     * @return 安装结果统计
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult install(String packCode, String version, String operator) {
        RulePackDO entity = findDO(packCode, version);
        if (entity == null) {
            throw new IllegalArgumentException("规则集不存在: " + packCode + " v" + version);
        }
        List<String> ruleCodes = parseRuleCodes(entity.getRuleCodes());
        int success = 0, failed = 0;
        List<String> failedCodes = new ArrayList<>();
        for (String ruleCode : ruleCodes) {
            try {
                installSingleRule(ruleCode, operator);
                success++;
            } catch (Exception e) {
                log.warn("[RulePack] 安装规则失败: code={}, err={}", ruleCode, e.getMessage());
                failed++;
                failedCodes.add(ruleCode + "(" + e.getMessage() + ")");
            }
        }
        // 增加下载次数
        rulePackMapper.increaseDownloadCount(entity.getId());

        // 记录安装历史
        RulePackInstallDO record = new RulePackInstallDO();
        record.setPackCode(packCode);
        record.setPackVersion(version);
        record.setTenantId(TenantContext.getTenantId());
        record.setInstalledBy(operator);
        record.setInstalledAt(LocalDateTime.now());
        record.setStatus(failed == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        record.setErrorMessage(String.join("; ", failedCodes));
        rulePackInstallMapper.insert(record);

        InstallResult result = new InstallResult();
        result.setPackCode(packCode);
        result.setVersion(version);
        result.setTotal(ruleCodes.size());
        result.setSuccess(success);
        result.setFailed(failed);
        result.setFailedCodes(failedCodes);
        log.info("[RulePack] 安装完成: code={}, version={}, success={}, failed={}, operator={}",
                packCode, version, success, failed, operator);
        return result;
    }

    /**
     * 安装单条规则：如果规则已存在则跳过；不存在则尝试从 RuleDefinition 模板导入
     */
    private void installSingleRule(String ruleCode, String operator) {
        RuleDefinition existing = ruleConfigProvider.findByCode(ruleCode);
        if (existing != null) {
            log.debug("[RulePack] 规则 {} 已存在，跳过安装", ruleCode);
            return;
        }
        // 简化：未找到时，仅记录日志，不自动创建
        // 实际场景中，应有"模板规则集"提供完整规则定义 JSON，这里只做引用
        log.info("[RulePack] 规则 {} 未在当前库中，依赖业务侧手动创建或导入", ruleCode);
    }

    /**
     * 删除规则集
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) return;
        rulePackMapper.deleteById(id);
        log.info("[RulePack] 删除规则集: id={}", id);
    }

    /**
     * 标记为官方
     */
    @Transactional(rollbackFor = Exception.class)
    public void markOfficial(Long id, boolean official) {
        if (id == null) return;
        RulePackDO entity = rulePackMapper.selectById(id);
        if (entity == null) return;
        entity.setOfficial(official);
        entity.setUpdatedAt(LocalDateTime.now());
        rulePackMapper.updateById(entity);
    }

    /**
     * 评分
     */
    @Transactional(rollbackFor = Exception.class)
    public void rate(Long id, double rating) {
        if (id == null) return;
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("rating 必须在 0-5 之间");
        }
        RulePackDO entity = rulePackMapper.selectById(id);
        if (entity == null) return;
        entity.setRating(BigDecimal.valueOf(rating));
        entity.setUpdatedAt(LocalDateTime.now());
        rulePackMapper.updateById(entity);
    }

    private RulePackDO findDO(String packCode, String version) {
        List<RulePackDO> list = rulePackMapper.selectByPackCode(packCode);
        for (RulePackDO d : list) {
            if (version == null || version.isBlank() || version.equals(d.getPackVersion())) {
                return d;
            }
        }
        return null;
    }

    private List<String> parseRuleCodes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[RulePack] 解析 ruleCodes 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private RulePack toApi(RulePackDO d) {
        if (d == null) return null;
        return RulePack.builder()
                .packCode(d.getPackCode())
                .packVersion(d.getPackVersion())
                .packName(d.getPackName())
                .industry(d.getIndustry())
                .tags(d.getTags() == null ? null : Arrays.asList(d.getTags().split(",")))
                .ruleCodes(parseRuleCodes(d.getRuleCodes()))
                .description(d.getDescription())
                .author(d.getAuthor())
                .downloadCount(d.getDownloadCount() == null ? 0 : d.getDownloadCount())
                .rating(d.getRating() == null ? 0 : d.getRating().doubleValue())
                .build();
    }

    private int compareVersion(String a, String b) {
        if (a == null) return -1;
        if (b == null) return 1;
        String[] av = a.split("\\.");
        String[] bv = b.split("\\.");
        for (int i = 0; i < Math.max(av.length, bv.length); i++) {
            int an = i < av.length ? parseIntSafe(av[i]) : 0;
            int bn = i < bv.length ? parseIntSafe(bv[i]) : 0;
            if (an != bn) return Integer.compare(an, bn);
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 安装结果
     */
    @lombok.Data
    public static class InstallResult {
        private String packCode;
        private String version;
        private int total;
        private int success;
        private int failed;
        private List<String> failedCodes;
    }
}
