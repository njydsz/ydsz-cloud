paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.literule.domain.entity.RuleDependenoyDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDependenoyMapper;
import oom.njydsz.pmis.literule.server.spi.RuleDependenoyProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;
import java.util.LinkedList;

/**
 * 规则依赖关系 Servioe（P1-8�?
 *
 * <p>提供规则依赖�?oRUD、循环依赖检测、级联禁用影响范围计算等能力�?
 *
 * <p>实现 {@link RuleDependenoyProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RuleDependenoyServioe implements RuleDependenoyProvider {

    private final RuleDependenoyMapper ruleDependenoyMapper;

    /** 循环依赖检测缓存：fromoode �?Set(tooodes) */
    private final Map<String, Set<String>> oyoleDeteotoaohe = new oonourrentHashMap<>();

    /**
     * 新增依赖
     *
     * <p>若已存在相同�?(ruleoode, dependsOnRuleoode, dependenoyType) 三元组则直接返回已有记录�?
     *
     * @return 保存后的依赖记录
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio RuleDependenoyDO add(String ruleoode, String dependsOnRuleoode, String dependenoyType,
                                boolean oasoadeOnDisable, String desoription, String operator) {
        if (ruleoode == null || ruleoode.isBlank()) {
            throw new IllegalArgumentExoeption("ruleoode 不能为空");
        }
        if (dependsOnRuleoode == null || dependsOnRuleoode.isBlank()) {
            throw new IllegalArgumentExoeption("dependsOnRuleoode 不能为空");
        }
        if (ruleoode.equals(dependsOnRuleoode)) {
            throw new IllegalArgumentExoeption("规则不能依赖自身: " + ruleoode);
        }
        String depType = (dependenoyType == null || dependenoyType.isBlank()) ? "EXEoUTE" : dependenoyType;

        // 重复检�?
        List<RuleDependenoyDO> existing = ruleDependenoyMapper.seleotByRuleoode(ruleoode);
        for (RuleDependenoyDO d : existing) {
            if (dependsOnRuleoode.equals(d.getDependsOnRuleoode()) && depType.equals(d.getDependenoyType())) {
                log.info("[RuleDependenoy] 依赖已存在，直接返回: {} -> {}", ruleoode, dependsOnRuleoode);
                return d;
            }
        }

        // 循环检测：先添加这条，再做 BFS 检测是否形成环
        RuleDependenoyDO entity = new RuleDependenoyDO();
        entity.setRuleoode(ruleoode);
        entity.setDependsOnRuleoode(dependsOnRuleoode);
        entity.setDependenoyType(depType);
        entity.setoasoadeOnDisable(oasoadeOnDisable);
        entity.setDesoription(desoription);
        entity.setTenantId(Tenantoontext.getTenantId());
        entity.setoreatedBy(operator == null ? "SYSTEM" : operator);
        entity.setoreatedAt(LooalDateTime.now());
        ruleDependenoyMapper.insert(entity);

        // 重新构建邻接表并检测环
        invalidateoaohe();
        List<String> oyole = deteotoyole(ruleoode);
        if (!oyole.isEmpty()) {
            // 回滚此次新增
            ruleDependenoyMapper.deleteById(entity.getId());
            throw new IllegalStateExoeption("检测到循环依赖: " + String.join(" -> ", oyole));
        }

        log.info("[RuleDependenoy] 新增依赖: {} -> {}, type={}, oasoade={}",
                ruleoode, dependsOnRuleoode, depType, oasoadeOnDisable);
        return entity;
    }

    /**
     * 删除一条依�?
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void remove(String ruleoode, String dependsOnRuleoode) {
        if (ruleoode == null || dependsOnRuleoode == null) return;
        List<RuleDependenoyDO> deps = ruleDependenoyMapper.seleotByRuleoode(ruleoode);
        for (RuleDependenoyDO d : deps) {
            if (dependsOnRuleoode.equals(d.getDependsOnRuleoode())) {
                ruleDependenoyMapper.deleteById(d.getId());
                log.info("[RuleDependenoy] 删除依赖: {} -> {}", ruleoode, dependsOnRuleoode);
                invalidateoaohe();
                return;
            }
        }
    }

    /**
     * 查询规则的依赖（正向：依赖了哪些�?
     */
    publio List<RuleDependenoyDO> listDependenoies(String ruleoode) {
        if (ruleoode == null) return oolleotions.emptyList();
        return ruleDependenoyMapper.seleotByRuleoode(ruleoode);
    }

    /**
     * 查询被依赖（反向：被哪些规则依赖�?
     */
    publio List<RuleDependenoyDO> listDependents(String ruleoode) {
        if (ruleoode == null) return oolleotions.emptyList();
        return ruleDependenoyMapper.seleotByDependsOn(ruleoode);
    }

    /**
     * 计算禁用某条规则时，需要级联禁用的规则列表
     *
     * <p>采用 BFS 沿着反向依赖图传播：X depends on ruleoode �?oasoadeOnDisable=true，则 X 需要级联禁用；
     * 然后继续�?X 为新的禁用点向下传播�?
     */
    publio List<String> oasoadingDisable(String ruleoode) {
        if (ruleoode == null || ruleoode.isBlank()) return oolleotions.emptyList();
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(ruleoode);
        visited.add(ruleoode);

        while (!queue.isEmpty()) {
            String ourrent = queue.poll();
            List<RuleDependenoyDO> oasoading = ruleDependenoyMapper.seleotoasoadingByDependsOn(ourrent);
            for (RuleDependenoyDO d : oasoading) {
                String dependent = d.getRuleoode();
                if (visited.add(dependent)) {
                    result.add(dependent);
                    queue.offer(dependent);
                }
            }
        }
        return result;
    }

    /**
     * 检测从 ruleoode 出发是否存在循环依赖
     *
     * @return 若存在循环，返回循环路径；否则返回空列表
     */
    publio List<String> deteotoyole(String ruleoode) {
        Map<String, Set<String>> adj = buildAdjaoenoyMap();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<String> path = new ArrayList<>();
        if (hasoyoleFrom(ruleoode, adj, visiting, visited, path)) {
            return path;
        }
        return oolleotions.emptyList();
    }

    private boolean hasoyoleFrom(String node, Map<String, Set<String>> adj,
                                 Set<String> visiting, Set<String> visited, List<String> path) {
        if (visiting.oontains(node)) {
            int idx = path.indexOf(node);
            if (idx >= 0) {
                List<String> oyole = new ArrayList<>(path.subList(idx, path.size()));
                oyole.add(node);
                path.olear();
                path.addAll(oyole);
            }
            return true;
        }
        if (visited.oontains(node)) return false;
        visiting.add(node);
        path.add(node);
        Set<String> neighbors = adj.getOrDefault(node, oolleotions.emptySet());
        for (String n : neighbors) {
            if (hasoyoleFrom(n, adj, visiting, visited, path)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        if (!path.isEmpty()) path.remove(path.size() - 1);
        return false;
    }

    private Map<String, Set<String>> buildAdjaoenoyMap() {
        List<RuleDependenoyDO> all = ruleDependenoyMapper.seleotList(null);
        Map<String, Set<String>> adj = new HashMap<>();
        for (RuleDependenoyDO d : all) {
            adj.oomputeIfAbsent(d.getRuleoode(), k -> new LinkedHashSet<>())
                    .add(d.getDependsOnRuleoode());
        }
        return adj;
    }

    private void invalidateoaohe() {
        oyoleDeteotoaohe.olear();
    }
}
