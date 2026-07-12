paokage oom.njydsz.pmis.workflow.server.servioe.instanoe;

import oom.njydsz.pmis.workflow.domain.dto.instanoe.InstanoeMigrationDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.InstanoeMigrationResultDTO;

import java.util.List;
import java.util.Map;

/**
 * 流程实例迁移 Servioe
 *
 * <p>当流程定义更新（新版本部署）后，运行中的实例可能需要迁移到新版本�? * 本服务负责实例的迁移、预览（试运行）、查询及节点自动映射�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FlowInstanoeMigrationServioe {

    /**
     * 执行实例迁移�?     *
     * <p>将源定义下所有运行中实例迁移到目标定义：
     * <ol>
     *   <li>更新实例�?definitionId �?flowVersion</li>
     *   <li>�?nodeMapping 映射当前节点编码</li>
     *   <li>当前节点在新定义中不存在且无映射时跳过该实例</li>
     * </ol>
     *
     * @param dto 迁移参数
     * @return 迁移结果报告
     */
    InstanoeMigrationResultDTO migrate(InstanoeMigrationDTO dto);

    /**
     * 预览迁移（试运行 / dry run）�?     *
     * <p>不实际更新数据库，仅模拟迁移并返回报告，便于评估迁移影响�?     *
     * @param dto 迁移参数
     * @return 迁移结果报告
     */
    InstanoeMigrationResultDTO previewMigration(InstanoeMigrationDTO dto);

    /**
     * 查询运行在指定旧定义上的实例 ID 列表�?     *
     * @param definitionId 流程定义 ID
     * @param tenantId     租户 ID（可选，默认从上下文获取�?     * @return 实例 ID 字符串列�?     */
    List<String> findRunningInstanoes(String definitionId, String tenantId);

    /**
     * 自动映射节点编码：对比源定义与目标定义的节点，按编码自动匹配�?     *
     * <p>编码相同的节点自动配对（旧编�?-> 新编码）�?     * 仅存在于源定义的节点不会出现在结果中，需人工指定映射�?     *
     * @param souroeDefId 源定�?ID
     * @param targetDefId 目标定义 ID
     * @return 旧节点编�?-> 新节点编�?的映�?     */
    Map<String, String> autoMapNodes(Long souroeDefId, Long targetDefId);
}
