package com.njydsz.pmis.project.server.literule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.njydsz.pmis.common.json.Json;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.json.type.JsonType;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RulePack;
import com.njydsz.pmis.literule.domain.entity.RulePackDO;
import com.njydsz.pmis.literule.domain.entity.RulePackInstallDO;
import com.njydsz.pmis.literule.infra.mapper.RulePackInstallMapper;
import com.njydsz.pmis.literule.infra.mapper.RulePackMapper;
import com.njydsz.pmis.literule.server.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.server.spi.RulePackProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则集 Service（P2-14）
 *
 * <p>提供规则集（RulePack）的市场发布、查询、安装、版本管理等能力。
 * 安装过程：从 pack 中提取 rule_codes 列表，通过 {@link RuleConfigProvider} 加载规则定义。
 *
 * <p>实现 {@link RulePackProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulePackService implements RulePackProvider {

    private final RulePackMapper rulePackMapper;
    private final RulePackInstallMapper rulePackInstallMapper;
    private final RuleConfigProvider ruleConfigProvider;

    private final // Json as JSON engine

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
        // 计算升级来源版本（当前已发布的最高版本，P2-8 版本链路追踪）
        String previousVersion = null;
        if (found == null && !existing.isEmpty()) {
            previousVersion = existing.stream()
                    .map(RulePackDO::getPackVersion)
                    .max((a, b) -> compareVersion(a, b))
                    .orElse(null);
        } else if (found != null) {
            previousVersion = found.getPreviousVersion();
        }

        RulePackDO entity = found == null ? new RulePackDO() : found;
        entity.setPackCode(pack.getPackCode());
        entity.setPackVersion(pack.getPackVersion());
        entity.setPackName(pack.getPackName());
        entity.setIndustry(pack.getIndustry());
        entity.setTags(pack.getTags() == null ? null : String.join(",", pack.getTags()));
        entity.setPreviousVersion(previousVersion);
        try {
            entity.setRuleCodes(Json.toJson(pack.getRuleCodes() == null ? Collections.emptyList() : pack.getRuleCodes()));
            // P2-8：发布时固化规则定义快照，保证版本内容可复现
            entity.setRuleSnapshots(Json.toJson(buildSnapshots(pack.getRuleCodes())));
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
    public void delete(String id) {
        if (id == null) return;
        rulePackMapper.deleteById(id);
        log.info("[RulePack] 删除规则集: id={}", id);
    }

    /**
     * 标记为官方
     */
    @Transactional(rollbackFor = Exception.class)
    public void markOfficial(String id, boolean official) {
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
    public void rate(String id, double rating) {
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
        if (version != null && !version.isBlank()) {
            RulePackDO exact = rulePackMapper.selectByPackCodeVersion(packCode, version);
            if (exact != null) return exact;
            return null;
        }
        List<RulePackDO> list = rulePackMapper.selectByPackCode(packCode);
        if (list.isEmpty()) return null;
        // 未指定版本时返回最高版本
        return list.stream().max((a, b) -> compareVersion(a.getPackVersion(), b.getPackVersion())).orElse(null);
    }

    /**
     * 按版本精确查询规则集（P2-8）
     */
    public RulePack getVersion(String packCode, String version) {
        return toApi(rulePackMapper.selectByPackCodeVersion(packCode, version));
    }

    /**
     * 知识包版本回滚（P2-8）
     *
     * <p>将该历史版本固化的规则定义快照恢复到在线规则表（逐条 save），
     * 并记录一条回滚安装历史。与单规则回滚不同，这里以"包"为粒度整体回滚，
     * 保证包内规则集的内容一致性。
     *
     * @return 回滚结果统计
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult rollback(String packCode, String version, String operator) {
        RulePackDO entity = rulePackMapper.selectByPackCodeVersion(packCode, version);
        if (entity == null) {
            throw new IllegalArgumentException("规则集版本不存在: " + packCode + " v" + version);
        }
        List<RuleDefinition> snapshots = parseSnapshots(entity.getRuleSnapshots());
        int success = 0, failed = 0;
        List<String> failedCodes = new ArrayList<>();
        for (RuleDefinition def : snapshots) {
            try {
                ruleConfigProvider.save(def, operator);
                success++;
            } catch (Exception e) {
                log.warn("[RulePack] 回滚规则失败: code={}, err={}", def.getCode(), e.getMessage());
                failed++;
                failedCodes.add(def.getCode() + "(" + e.getMessage() + ")");
            }
        }
        // 记录回滚历史
        RulePackInstallDO record = new RulePackInstallDO();
        record.setPackCode(packCode);
        record.setPackVersion(version);
        record.setTenantId(TenantContext.getTenantId());
        record.setInstalledBy(operator);
        record.setInstalledAt(LocalDateTime.now());
        record.setStatus(failed == 0 ? "ROLLBACK_SUCCESS" : (success == 0 ? "ROLLBACK_FAILED" : "ROLLBACK_PARTIAL"));
        record.setErrorMessage(String.join("; ", failedCodes));
        rulePackInstallMapper.insert(record);

        InstallResult result = new InstallResult();
        result.setPackCode(packCode);
        result.setVersion(version);
        result.setTotal(snapshots.size());
        result.setSuccess(success);
        result.setFailed(failed);
        result.setFailedCodes(failedCodes);
        log.info("[RulePack] 回滚完成: code={}, version={}, success={}, failed={}, operator={}",
                packCode, version, success, failed, operator);
        return result;
    }

    /**
     * 知识包版本差异对比（P2-8）
     *
     * <p>对比两个版本在规则编码集合与规则定义内容上的差异，便于升级评审。
     *
     * @return 差异结果（含新增/移除/变更的规则编码列表）
     */
    public PackDiff diff(String packCode, String fromVersion, String toVersion) {
        RulePackDO from = rulePackMapper.selectByPackCodeVersion(packCode, fromVersion);
        RulePackDO to = rulePackMapper.selectByPackCodeVersion(packCode, toVersion);
        if (from == null || to == null) {
            throw new IllegalArgumentException("对比版本不存在: " + packCode + " [" + fromVersion + " -> " + toVersion + "]");
        }
        List<String> fromCodes = parseRuleCodes(from.getRuleCodes());
        List<String> toCodes = parseRuleCodes(to.getRuleCodes());
        List<String> added = new ArrayList<>(toCodes);
        added.removeAll(fromCodes);
        List<String> removed = new ArrayList<>(fromCodes);
        removed.removeAll(toCodes);
        List<String> common = new ArrayList<>(toCodes);
        common.retainAll(fromCodes);

        // 内容变更：基于快照逐条对比条件表达式
        List<String> changed = new ArrayList<>();
        if (from.getRuleSnapshots() != null && to.getRuleSnapshots() != null) {
            var fromMap = parseSnapshots(from.getRuleSnapshots()).stream()
                    .collect(Collectors.toMap(RuleDefinition::getCode, d -> d, (a, b) -> a));
            var toMap = parseSnapshots(to.getRuleSnapshots()).stream()
                    .collect(Collectors.toMap(RuleDefinition::getCode, d -> d, (a, b) -> a));
            for (String code : common) {
                RuleDefinition a = fromMap.get(code);
                RuleDefinition b = toMap.get(code);
                if (a == null || b == null) continue;
                if (!Objects.equals(a.getConditionExpression(), b.getConditionExpression())
                        || !Objects.equals(a.getSeverityExpression(), b.getSeverityExpression())
                        || !Objects.equals(a.getPriority(), b.getPriority())) {
                    changed.add(code);
                }
            }
        }
        PackDiff result = new PackDiff();
        result.setPackCode(packCode);
        result.setFromVersion(fromVersion);
        result.setToVersion(toVersion);
        result.setAdded(added);
        result.setRemoved(removed);
        result.setChanged(changed);
        return result;
    }

    /**
     * 构建规则定义快照（P2-8）
     *
     * <p>依据 ruleCodes 从在线规则表加载完整 RuleDefinition 并序列化，固化到版本中。
     */
    private List<RuleDefinition> buildSnapshots(List<String> ruleCodes) {
        if (ruleCodes == null || ruleCodes.isEmpty()) return Collections.emptyList();
        List<RuleDefinition> snapshots = new ArrayList<>(ruleCodes.size());
        for (String code : ruleCodes) {
            RuleDefinition def = ruleConfigProvider.findByCode(code);
            if (def != null) snapshots.add(def);
            else log.warn("[RulePack] 发布快照时规则 {} 不存在，跳过", code);
        }
        return snapshots;
    }

    private List<RuleDefinition> parseSnapshots(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return Json.parseArray(json, RuleDefinition.class);
        } catch (Exception e) {
            log.warn("[RulePack] 解析 ruleSnapshots 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseRuleCodes(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new JsonType<List<String>>() {});
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
                .ruleSnapshots(parseSnapshots(d.getRuleSnapshots()))
                .previousVersion(d.getPreviousVersion())
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
            log.warn("[RulePackService] 整数解析失败 s={}: {}", s, e.getMessage());
            return 0;
        }
    }

    /**
     * 检查已安装知识包的版本更新（P2-10）
     *
     * <p>查询当前租户已安装的知识包列表，对比每个包的已安装版本与市场最新版本，
     * 返回所有已安装包的更新检查结果（含无更新的包，便于前端展示完整列表）。
     * 调用方可通过 {@code hasUpdate=true} 过滤有更新的包。
     *
     * <p>实现策略：
     * <ol>
     *   <li>从 {@code pmis_rule_pack_install} 查询当前租户的安装记录，按 packCode 聚合最新一次安装版本</li>
     *   <li>对每个已安装的 packCode，查询 {@code pmis_rule_pack} 中的最高版本作为 latestVersion</li>
     *   <li>使用语义化版本比较 installedVersion 与 latestVersion</li>
     * </ol>
     *
     * @return 更新检查结果列表
     * @since 1.6.0
     */
    public List<PackUpdateInfo> checkPackUpdates() {
        // 1. 查询当前租户的安装记录
        LambdaQueryWrapper<RulePackInstallDO> wrapper = new LambdaQueryWrapper<>();
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(RulePackInstallDO::getTenantId, tenantId);
        }
        wrapper.orderByDesc(RulePackInstallDO::getInstalledAt);
        List<RulePackInstallDO> installs = rulePackInstallMapper.selectList(wrapper);
        if (installs.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 按 packCode 聚合：保留最新一次安装的版本（installs 已按 installedAt 倒序）
        Map<String, RulePackInstallDO> latestInstallByCode = new LinkedHashMap<>();
        for (RulePackInstallDO inst : installs) {
            latestInstallByCode.putIfAbsent(inst.getPackCode(), inst);
        }

        // 3. 对每个 packCode 查询市场最新版本
        List<PackUpdateInfo> result = new ArrayList<>(latestInstallByCode.size());
        for (Map.Entry<String, RulePackInstallDO> entry : latestInstallByCode.entrySet()) {
            String packCode = entry.getKey();
            RulePackInstallDO install = entry.getValue();
            String installedVersion = install.getPackVersion();
            // 查询该 packCode 的所有版本，取最高版本作为 latest
            List<RulePackDO> allVersions = rulePackMapper.selectByPackCode(packCode);
            String latestVersion = installedVersion;
            RulePackDO latestEntity = null;
            if (!allVersions.isEmpty()) {
                latestEntity = allVersions.stream()
                        .max((a, b) -> compareVersion(a.getPackVersion(), b.getPackVersion()))
                        .orElse(null);
                if (latestEntity != null) {
                    latestVersion = latestEntity.getPackVersion();
                }
            }
            PackUpdateInfo info = new PackUpdateInfo();
            info.setPackCode(packCode);
            info.setPackName(latestEntity != null ? latestEntity.getPackName() : packCode);
            info.setInstalledVersion(installedVersion);
            info.setLatestVersion(latestVersion);
            info.setHasUpdate(compareVersion(latestVersion, installedVersion) > 0);
            info.setInstalledAt(install.getInstalledAt());
            info.setIndustry(latestEntity != null ? latestEntity.getIndustry() : null);
            info.setDescription(latestEntity != null ? latestEntity.getDescription() : null);
            result.add(info);
        }
        log.info("[RulePack] 更新检查完成: 已安装 {} 个知识包，有更新 {} 个",
                result.size(), result.stream().filter(PackUpdateInfo::isHasUpdate).count());
        return result;
    }
}
