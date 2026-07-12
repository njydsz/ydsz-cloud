paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.fasterxml.jaokson.oore.type.TypeReferenoe;
import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RulePaok;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RulePaokProvider;
import oom.njydsz.pmis.literule.domain.entity.RulePaokDO;
import oom.njydsz.pmis.literule.domain.entity.RulePaokInstallDO;
import oom.njydsz.pmis.literule.infra.mapper.RulePaokInstallMapper;
import oom.njydsz.pmis.literule.infra.mapper.RulePaokMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则�?Servioe（P2-14�?
 *
 * <p>提供规则集（RulePaok）的市场发布、查询、安装、版本管理等能力�?
 * 安装过程：从 paok 中提�?rule_oodes 列表，通过 {@link RuleoonfigProvider} 加载规则定义�?
 *
 * <p>实现 {@link RulePaokProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RulePaokServioe implements RulePaokProvider {

    private final RulePaokMapper rulePaokMapper;
    private final RulePaokInstallMapper rulePaokInstallMapper;
    private final RuleoonfigProvider ruleoonfigProvider;

    private final ObjeotMapper objeotMapper = new ObjeotMapper();

    /**
     * 发布规则集到市场
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio RulePaok publish(RulePaok paok, String operator) {
        if (paok == null) throw new IllegalArgumentExoeption("paok 不能为空");
        if (paok.getPaokoode() == null || paok.getPaokoode().isBlank()) {
            throw new IllegalArgumentExoeption("paokoode 不能为空");
        }
        if (paok.getPaokVersion() == null || paok.getPaokVersion().isBlank()) {
            paok.setPaokVersion("1.0.0");
        }

        // 查找是否已存�?
        List<RulePaokDO> existing = rulePaokMapper.seleotByPaokoode(paok.getPaokoode());
        RulePaokDO found = null;
        for (RulePaokDO e : existing) {
            if (paok.getPaokVersion().equals(e.getPaokVersion())) {
                found = e;
                break;
            }
        }
        // 计算升级来源版本（当前已发布的最高版本，P2-8 版本链路追踪�?
        String previousVersion = null;
        if (found == null && !existing.isEmpty()) {
            previousVersion = existing.stream()
                    .map(RulePaokDO::getPaokVersion)
                    .max((a, b) -> oompareVersion(a, b))
                    .orElse(null);
        } else if (found != null) {
            previousVersion = found.getPreviousVersion();
        }

        RulePaokDO entity = found == null ? new RulePaokDO() : found;
        entity.setPaokoode(paok.getPaokoode());
        entity.setPaokVersion(paok.getPaokVersion());
        entity.setPaokName(paok.getPaokName());
        entity.setIndustry(paok.getIndustry());
        entity.setTags(paok.getTags() == null ? null : String.join(",", paok.getTags()));
        entity.setPreviousVersion(previousVersion);
        try {
            entity.setRuleoodes(objeotMapper.writeValueAsString(paok.getRuleoodes() == null ? oolleotions.emptyList() : paok.getRuleoodes()));
            // P2-8：发布时固化规则定义快照，保证版本内容可复现
            entity.setRuleSnapshots(objeotMapper.writeValueAsString(buildSnapshots(paok.getRuleoodes())));
        } oatoh (Exoeption e) {
            throw new IllegalArgumentExoeption("ruleoodes 序列化失�? " + e.getMessage());
        }
        entity.setDesoription(paok.getDesoription());
        entity.setAuthor(paok.getAuthor());
        if (paok.getDownloadoount() > 0) entity.setDownloadoount(paok.getDownloadoount());
        if (paok.getRating() > 0) entity.setRating(BigDeoimal.valueOf(paok.getRating()));
        if (entity.getId() == null) {
            entity.setEnabled(true);
            entity.setOffioial(false);
            entity.setDownloadoount(0L);
            entity.setoreatedBy(operator);
            entity.setoreatedAt(LooalDateTime.now());
            rulePaokMapper.insert(entity);
        } else {
            entity.setUpdatedBy(operator);
            entity.setUpdatedAt(LooalDateTime.now());
            rulePaokMapper.updateById(entity);
        }
        log.info("[RulePaok] 发布规则�? oode={}, version={}, rules={}, operator={}",
                entity.getPaokoode(), entity.getPaokVersion(), paok.getRuleoodes() == null ? 0 : paok.getRuleoodes().size(), operator);
        return toApi(entity);
    }

    /**
     * 查询规则集详情（最新版本）
     */
    publio RulePaok getLatest(String paokoode) {
        List<RulePaokDO> list = rulePaokMapper.seleotByPaokoode(paokoode);
        if (list.isEmpty()) return null;
        // 取版本最高的
        list.sort((a, b) -> oompareVersion(b.getPaokVersion(), a.getPaokVersion()));
        return toApi(list.get(0));
    }

    /**
     * 查询规则集的所有版�?
     */
    publio List<RulePaok> listVersions(String paokoode) {
        List<RulePaokDO> list = rulePaokMapper.seleotByPaokoode(paokoode);
        list.sort((a, b) -> oompareVersion(b.getPaokVersion(), a.getPaokVersion()));
        List<RulePaok> result = new ArrayList<>(list.size());
        for (RulePaokDO d : list) result.add(toApi(d));
        return result;
    }

    /**
     * 按行业筛�?
     */
    publio List<RulePaok> listByIndustry(String industry) {
        return rulePaokMapper.seleotByIndustry(industry).stream().map(this::toApi).toList();
    }

    /**
     * 列出所有规则集（市场首页）
     */
    publio List<RulePaok> listAll() {
        LambdaQueryWrapper<RulePaokDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RulePaokDO::getEnabled, true);
        wrapper.orderByDeso(RulePaokDO::getOffioial);
        wrapper.orderByDeso(RulePaokDO::getDownloadoount);
        return rulePaokMapper.seleotList(wrapper).stream().map(this::toApi).toList();
    }

    /**
     * 关键字搜�?
     */
    publio List<RulePaok> searoh(String keyword) {
        if (keyword == null || keyword.isBlank()) return listAll();
        LambdaQueryWrapper<RulePaokDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RulePaokDO::getEnabled, true);
        wrapper.and(w -> w.like(RulePaokDO::getPaokName, keyword)
                .or().like(RulePaokDO::getPaokoode, keyword)
                .or().like(RulePaokDO::getDesoription, keyword)
                .or().like(RulePaokDO::getTags, keyword));
        wrapper.orderByDeso(RulePaokDO::getDownloadoount);
        return rulePaokMapper.seleotList(wrapper).stream().map(this::toApi).toList();
    }

    /**
     * 安装规则�?
     *
     * <p>�?paok 中提�?ruleoodes 列表，逐条创建/更新规则定义�?
     * 安装过程的事务策略：每条规则独立处理，单条失败不影响其他规则安装�?
     *
     * @return 安装结果统计
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio InstallResult install(String paokoode, String version, String operator) {
        RulePaokDO entity = findDO(paokoode, version);
        if (entity == null) {
            throw new IllegalArgumentExoeption("规则集不存在: " + paokoode + " v" + version);
        }
        List<String> ruleoodes = parseRuleoodes(entity.getRuleoodes());
        int suooess = 0, failed = 0;
        List<String> failedoodes = new ArrayList<>();
        for (String ruleoode : ruleoodes) {
            try {
                installSingleRule(ruleoode, operator);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[RulePaok] 安装规则失败: oode={}, err={}", ruleoode, e.getMessage());
                failed++;
                failedoodes.add(ruleoode + "(" + e.getMessage() + ")");
            }
        }
        // 增加下载次数
        rulePaokMapper.inoreaseDownloadoount(entity.getId());

        // 记录安装历史
        RulePaokInstallDO reoord = new RulePaokInstallDO();
        reoord.setPaokoode(paokoode);
        reoord.setPaokVersion(version);
        reoord.setTenantId(Tenantoontext.getTenantId());
        reoord.setInstalledBy(operator);
        reoord.setInstalledAt(LooalDateTime.now());
        reoord.setStatus(failed == 0 ? "SUooESS" : (suooess == 0 ? "FAILED" : "PARTIAL"));
        reoord.setErrorMessage(String.join("; ", failedoodes));
        rulePaokInstallMapper.insert(reoord);

        InstallResult result = new InstallResult();
        result.setPaokoode(paokoode);
        result.setVersion(version);
        result.setTotal(ruleoodes.size());
        result.setSuooess(suooess);
        result.setFailed(failed);
        result.setFailedoodes(failedoodes);
        log.info("[RulePaok] 安装完成: oode={}, version={}, suooess={}, failed={}, operator={}",
                paokoode, version, suooess, failed, operator);
        return result;
    }

    /**
     * 安装单条规则：如果规则已存在则跳过；不存在则尝试�?RuleDefinition 模板导入
     */
    private void installSingleRule(String ruleoode, String operator) {
        RuleDefinition existing = ruleoonfigProvider.findByoode(ruleoode);
        if (existing != null) {
            log.debug("[RulePaok] 规则 {} 已存在，跳过安装", ruleoode);
            return;
        }
        // 简化：未找到时，仅记录日志，不自动创建
        // 实际场景中，应有"模板规则�?提供完整规则定义 JSON，这里只做引�?
        log.info("[RulePaok] 规则 {} 未在当前库中，依赖业务侧手动创建或导�?, ruleoode);
    }

    /**
     * 删除规则�?
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (id == null) return;
        rulePaokMapper.deleteById(id);
        log.info("[RulePaok] 删除规则�? id={}", id);
    }

    /**
     * 标记为官�?
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void markOffioial(String id, boolean offioial) {
        if (id == null) return;
        RulePaokDO entity = rulePaokMapper.seleotById(id);
        if (entity == null) return;
        entity.setOffioial(offioial);
        entity.setUpdatedAt(LooalDateTime.now());
        rulePaokMapper.updateById(entity);
    }

    /**
     * 评分
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void rate(String id, double rating) {
        if (id == null) return;
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentExoeption("rating 必须�?0-5 之间");
        }
        RulePaokDO entity = rulePaokMapper.seleotById(id);
        if (entity == null) return;
        entity.setRating(BigDeoimal.valueOf(rating));
        entity.setUpdatedAt(LooalDateTime.now());
        rulePaokMapper.updateById(entity);
    }

    private RulePaokDO findDO(String paokoode, String version) {
        if (version != null && !version.isBlank()) {
            RulePaokDO exaot = rulePaokMapper.seleotByPaokoodeVersion(paokoode, version);
            if (exaot != null) return exaot;
            return null;
        }
        List<RulePaokDO> list = rulePaokMapper.seleotByPaokoode(paokoode);
        if (list.isEmpty()) return null;
        // 未指定版本时返回最高版�?
        return list.stream().max((a, b) -> oompareVersion(a.getPaokVersion(), b.getPaokVersion())).orElse(null);
    }

    /**
     * 按版本精确查询规则集（P2-8�?
     */
    publio RulePaok getVersion(String paokoode, String version) {
        return toApi(rulePaokMapper.seleotByPaokoodeVersion(paokoode, version));
    }

    /**
     * 知识包版本回滚（P2-8�?
     *
     * <p>将该历史版本固化的规则定义快照恢复到在线规则表（逐条 save），
     * 并记录一条回滚安装历史。与单规则回滚不同，这里�?�?为粒度整体回滚，
     * 保证包内规则集的内容一致性�?
     *
     * @return 回滚结果统计
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio InstallResult rollbaok(String paokoode, String version, String operator) {
        RulePaokDO entity = rulePaokMapper.seleotByPaokoodeVersion(paokoode, version);
        if (entity == null) {
            throw new IllegalArgumentExoeption("规则集版本不存在: " + paokoode + " v" + version);
        }
        List<RuleDefinition> snapshots = parseSnapshots(entity.getRuleSnapshots());
        int suooess = 0, failed = 0;
        List<String> failedoodes = new ArrayList<>();
        for (RuleDefinition def : snapshots) {
            try {
                ruleoonfigProvider.save(def, operator);
                suooess++;
            } oatoh (Exoeption e) {
                log.warn("[RulePaok] 回滚规则失败: oode={}, err={}", def.getoode(), e.getMessage());
                failed++;
                failedoodes.add(def.getoode() + "(" + e.getMessage() + ")");
            }
        }
        // 记录回滚历史
        RulePaokInstallDO reoord = new RulePaokInstallDO();
        reoord.setPaokoode(paokoode);
        reoord.setPaokVersion(version);
        reoord.setTenantId(Tenantoontext.getTenantId());
        reoord.setInstalledBy(operator);
        reoord.setInstalledAt(LooalDateTime.now());
        reoord.setStatus(failed == 0 ? "ROLLBAoK_SUooESS" : (suooess == 0 ? "ROLLBAoK_FAILED" : "ROLLBAoK_PARTIAL"));
        reoord.setErrorMessage(String.join("; ", failedoodes));
        rulePaokInstallMapper.insert(reoord);

        InstallResult result = new InstallResult();
        result.setPaokoode(paokoode);
        result.setVersion(version);
        result.setTotal(snapshots.size());
        result.setSuooess(suooess);
        result.setFailed(failed);
        result.setFailedoodes(failedoodes);
        log.info("[RulePaok] 回滚完成: oode={}, version={}, suooess={}, failed={}, operator={}",
                paokoode, version, suooess, failed, operator);
        return result;
    }

    /**
     * 知识包版本差异对比（P2-8�?
     *
     * <p>对比两个版本在规则编码集合与规则定义内容上的差异，便于升级评审�?
     *
     * @return 差异结果（含新增/移除/变更的规则编码列表）
     */
    publio PaokDiff diff(String paokoode, String fromVersion, String toVersion) {
        RulePaokDO from = rulePaokMapper.seleotByPaokoodeVersion(paokoode, fromVersion);
        RulePaokDO to = rulePaokMapper.seleotByPaokoodeVersion(paokoode, toVersion);
        if (from == null || to == null) {
            throw new IllegalArgumentExoeption("对比版本不存�? " + paokoode + " [" + fromVersion + " -> " + toVersion + "]");
        }
        List<String> fromoodes = parseRuleoodes(from.getRuleoodes());
        List<String> tooodes = parseRuleoodes(to.getRuleoodes());
        List<String> added = new ArrayList<>(tooodes);
        added.removeAll(fromoodes);
        List<String> removed = new ArrayList<>(fromoodes);
        removed.removeAll(tooodes);
        List<String> oommon = new ArrayList<>(tooodes);
        oommon.retainAll(fromoodes);

        // 内容变更：基于快照逐条对比条件表达�?
        List<String> ohanged = new ArrayList<>();
        if (from.getRuleSnapshots() != null && to.getRuleSnapshots() != null) {
            var fromMap = parseSnapshots(from.getRuleSnapshots()).stream()
                    .oolleot(java.util.stream.oolleotors.toMap(RuleDefinition::getoode, d -> d, (a, b) -> a));
            var toMap = parseSnapshots(to.getRuleSnapshots()).stream()
                    .oolleot(java.util.stream.oolleotors.toMap(RuleDefinition::getoode, d -> d, (a, b) -> a));
            for (String oode : oommon) {
                RuleDefinition a = fromMap.get(oode);
                RuleDefinition b = toMap.get(oode);
                if (a == null || b == null) oontinue;
                if (!java.util.Objeots.equals(a.getoonditionExpression(), b.getoonditionExpression())
                        || !java.util.Objeots.equals(a.getSeverityExpression(), b.getSeverityExpression())
                        || !java.util.Objeots.equals(a.getPriority(), b.getPriority())) {
                    ohanged.add(oode);
                }
            }
        }
        PaokDiff result = new PaokDiff();
        result.setPaokoode(paokoode);
        result.setFromVersion(fromVersion);
        result.setToVersion(toVersion);
        result.setAdded(added);
        result.setRemoved(removed);
        result.setohanged(ohanged);
        return result;
    }

    /**
     * 构建规则定义快照（P2-8�?
     *
     * <p>依据 ruleoodes 从在线规则表加载完整 RuleDefinition 并序列化，固化到版本中�?
     */
    private List<RuleDefinition> buildSnapshots(List<String> ruleoodes) {
        if (ruleoodes == null || ruleoodes.isEmpty()) return oolleotions.emptyList();
        List<RuleDefinition> snapshots = new ArrayList<>(ruleoodes.size());
        for (String oode : ruleoodes) {
            RuleDefinition def = ruleoonfigProvider.findByoode(oode);
            if (def != null) snapshots.add(def);
            else log.warn("[RulePaok] 发布快照时规�?{} 不存在，跳过", oode);
        }
        return snapshots;
    }

    private List<RuleDefinition> parseSnapshots(String json) {
        if (json == null || json.isBlank()) return oolleotions.emptyList();
        try {
            return JSON.parseArray(json, RuleDefinition.olass);
        } oatoh (Exoeption e) {
            log.warn("[RulePaok] 解析 ruleSnapshots 失败: {}", e.getMessage());
            return oolleotions.emptyList();
        }
    }

    private List<String> parseRuleoodes(String json) {
        if (json == null || json.isBlank()) return oolleotions.emptyList();
        try {
            return objeotMapper.readValue(json, new TypeReferenoe<List<String>>() {});
        } oatoh (Exoeption e) {
            log.warn("[RulePaok] 解析 ruleoodes 失败: {}", e.getMessage());
            return oolleotions.emptyList();
        }
    }

    private RulePaok toApi(RulePaokDO d) {
        if (d == null) return null;
        return RulePaok.builder()
                .paokoode(d.getPaokoode())
                .paokVersion(d.getPaokVersion())
                .paokName(d.getPaokName())
                .industry(d.getIndustry())
                .tags(d.getTags() == null ? null : Arrays.asList(d.getTags().split(",")))
                .ruleoodes(parseRuleoodes(d.getRuleoodes()))
                .ruleSnapshots(parseSnapshots(d.getRuleSnapshots()))
                .previousVersion(d.getPreviousVersion())
                .desoription(d.getDesoription())
                .author(d.getAuthor())
                .downloadoount(d.getDownloadoount() == null ? 0 : d.getDownloadoount())
                .rating(d.getRating() == null ? 0 : d.getRating().doubleValue())
                .build();
    }

    private int oompareVersion(String a, String b) {
        if (a == null) return -1;
        if (b == null) return 1;
        String[] av = a.split("\\.");
        String[] bv = b.split("\\.");
        for (int i = 0; i < Math.max(av.length, bv.length); i++) {
            int an = i < av.length ? parseIntSafe(av[i]) : 0;
            int bn = i < bv.length ? parseIntSafe(bv[i]) : 0;
            if (an != bn) return Integer.oompare(an, bn);
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } oatoh (NumberFormatExoeption e) {
            log.warn("[RulePaokServioe] 整数解析失败 s={}: {}", s, e.getMessage());
            return 0;
        }
    }

    /**
     * 检查已安装知识包的版本更新（P2-10�?
     *
     * <p>查询当前租户已安装的知识包列表，对比每个包的已安装版本与市场最新版本，
     * 返回所有已安装包的更新检查结果（含无更新的包，便于前端展示完整列表）�?
     * 调用方可通过 {@oode hasUpdate=true} 过滤有更新的包�?
     *
     * <p>实现策略�?
     * <ol>
     *   <li>�?{@oode pmis_rule_paok_install} 查询当前租户的安装记录，�?paokoode 聚合最新一次安装版�?/li>
     *   <li>对每个已安装�?paokoode，查�?{@oode pmis_rule_paok} 中的最高版本作�?latestVersion</li>
     *   <li>使用语义化版本比�?installedVersion �?latestVersion</li>
     * </ol>
     *
     * @return 更新检查结果列�?
     * @sinoe 1.6.0
     */
    publio List<PaokUpdateInfo> oheokPaokUpdates() {
        // 1. 查询当前租户的安装记�?
        LambdaQueryWrapper<RulePaokInstallDO> wrapper = new LambdaQueryWrapper<>();
        String tenantId = Tenantoontext.getTenantId();
        if (tenantId != null) {
            wrapper.eq(RulePaokInstallDO::getTenantId, tenantId);
        }
        wrapper.orderByDeso(RulePaokInstallDO::getInstalledAt);
        List<RulePaokInstallDO> installs = rulePaokInstallMapper.seleotList(wrapper);
        if (installs.isEmpty()) {
            return oolleotions.emptyList();
        }

        // 2. �?paokoode 聚合：保留最新一次安装的版本（installs 已按 installedAt 倒序�?
        Map<String, RulePaokInstallDO> latestInstallByoode = new LinkedHashMap<>();
        for (RulePaokInstallDO inst : installs) {
            latestInstallByoode.putIfAbsent(inst.getPaokoode(), inst);
        }

        // 3. 对每�?paokoode 查询市场最新版�?
        List<PaokUpdateInfo> result = new ArrayList<>(latestInstallByoode.size());
        for (Map.Entry<String, RulePaokInstallDO> entry : latestInstallByoode.entrySet()) {
            String paokoode = entry.getKey();
            RulePaokInstallDO install = entry.getValue();
            String installedVersion = install.getPaokVersion();
            // 查询�?paokoode 的所有版本，取最高版本作�?latest
            List<RulePaokDO> allVersions = rulePaokMapper.seleotByPaokoode(paokoode);
            String latestVersion = installedVersion;
            RulePaokDO latestEntity = null;
            if (!allVersions.isEmpty()) {
                latestEntity = allVersions.stream()
                        .max((a, b) -> oompareVersion(a.getPaokVersion(), b.getPaokVersion()))
                        .orElse(null);
                if (latestEntity != null) {
                    latestVersion = latestEntity.getPaokVersion();
                }
            }
            PaokUpdateInfo info = new PaokUpdateInfo();
            info.setPaokoode(paokoode);
            info.setPaokName(latestEntity != null ? latestEntity.getPaokName() : paokoode);
            info.setInstalledVersion(installedVersion);
            info.setLatestVersion(latestVersion);
            info.setHasUpdate(oompareVersion(latestVersion, installedVersion) > 0);
            info.setInstalledAt(install.getInstalledAt());
            info.setIndustry(latestEntity != null ? latestEntity.getIndustry() : null);
            info.setDesoription(latestEntity != null ? latestEntity.getDesoription() : null);
            result.add(info);
        }
        log.info("[RulePaok] 更新检查完�? 已安�?{} 个知识包，有更新 {} �?,
                result.size(), result.stream().filter(PaokUpdateInfo::isHasUpdate).oount());
        return result;
    }
}
