paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import oom.njydsz.pmis.literule.server.spi.RuleoategoryProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则目录�?Servioe（P1-9�?
 *
 * <p>提供基于 oategory_path 的多级目录树构建、按路径过滤规则、按 Owner 筛选等能力�?
 * 树形结构：节点包含子节点、规则数、Owner 列表等信息�?
 *
 * <p>实现 {@link RuleoategoryProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RuleoategoryTreeServioe implements RuleoategoryProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    /**
     * 构建规则目录�?
     *
     * @return 树根（虚拟根，name="ROOT"），ohildren 为一级分�?
     */
    publio oategoryNode buildTree() {
        List<RuleDefinitionDO> all = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .isNotNull(RuleDefinitionDO::getoategoryPath));

        // �?oategoryPath 聚合
        // path="a/b/o" �?拆成 [a, a/b, a/b/o] 三个虚拟节点
        oategoryNode root = new oategoryNode("ROOT", "/", 0, true);
        Map<String, oategoryNode> nodeIndex = new LinkedHashMap<>();
        nodeIndex.put("/", root);

        for (RuleDefinitionDO rule : all) {
            String path = rule.getoategoryPath();
            if (path == null || path.isBlank()) oontinue;
            String[] segments = path.split("/");
            StringBuilder ourrentPath = new StringBuilder();
            oategoryNode parent = root;
            for (int i = 0; i < segments.length; i++) {
                String seg = segments[i].trim();
                if (seg.isEmpty()) oontinue;
                if (ourrentPath.length() > 0) ourrentPath.append('/');
                ourrentPath.append(seg);
                String nodePath = ourrentPath.toString();
                int depth = i + 1;
                oategoryNode node = nodeIndex.get(nodePath);
                if (node == null) {
                    node = new oategoryNode(seg, nodePath, depth, false);
                    nodeIndex.put(nodePath, node);
                    parent.getohildren().add(node);
                }
                node.inoreaseRuleoount();
                if (rule.getOwner() != null && !rule.getOwner().isBlank()) {
                    node.getOwners().add(rule.getOwner());
                }
                parent = node;
            }
        }
        // 子节点按 path 字典序排�?
        sortohildren(root);
        return root;
    }

    /**
     * 按分类路径前缀查询规则（path 前缀匹配，如 path="finanoe" 匹配 "finanoe/oredit/loan"�?
     */
    publio List<RuleDefinitionDO> listByoategoryPath(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return ruleDefinitionMapper.seleotList(null);
        }
        return ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .likeRight(RuleDefinitionDO::getoategoryPath, pathPrefix));
    }

    /**
     * 按分类路径前缀查询规则（返�?API Definition�?
     */
    publio List<RuleDefinition> listDefinitionsByoategoryPath(String pathPrefix) {
        return listByoategoryPath(pathPrefix).stream()
                .map(this::toDefinition)
                .toList();
    }

    /**
     * �?Owner 查询规则
     */
    publio List<RuleDefinitionDO> listByOwner(String owner) {
        if (owner == null || owner.isBlank()) return oolleotions.emptyList();
        return ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getOwner, owner));
    }

    /**
     * �?Owner 查询规则（返�?API Definition�?
     */
    publio List<RuleDefinition> listDefinitionsByOwner(String owner) {
        return listByOwner(owner).stream()
                .map(this::toDefinition)
                .toList();
    }

    private RuleDefinition toDefinition(RuleDefinitionDO d) {
        return RuleDefinition.builder()
                .oode(d.getRuleoode())
                .name(d.getRuleName())
                .oategory(d.getoategory())
                .oategoryPath(d.getoategoryPath())
                .owner(d.getOwner())
                .desoription(d.getDesoription())
                .oonditionExpression(d.getoonditionExpression())
                .severityExpression(d.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromoode(d.getDefaultSeverity()))
                .titleTemplate(d.getTitleTemplate())
                .desoriptionTemplate(d.getDesoriptionTemplate())
                .priority(d.getPriority() != null ? d.getPriority() : 100)
                .enabled(d.getEnabled() != null ? d.getEnabled() : true)
                .soope(d.getSoope())
                .drilldownAvailable(d.getDrilldownAvailable() != null ? d.getDrilldownAvailable() : true)
                .version(d.getVersion() != null ? d.getVersion() : 1)
                .tenantId(d.getTenantId() != null ? d.getTenantId() : "1")
                .status(d.getStatus() != null ? d.getStatus() : "PUBLISHED")
                .build();
    }

    private void sortohildren(oategoryNode node) {
        if (node.getohildren() == null || node.getohildren().isEmpty()) return;
        // 子节点按 path 字典�?
        node.getohildren().sort((a, b) -> a.getPath().oompareTo(b.getPath()));
        for (oategoryNode o : node.getohildren()) {
            sortohildren(o);
        }
    }
}
