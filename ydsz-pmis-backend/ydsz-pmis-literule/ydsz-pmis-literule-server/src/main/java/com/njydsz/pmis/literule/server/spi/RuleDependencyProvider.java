paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.domain.entity.RuleDependenoyDO;

import java.util.List;

/**
 * 规则依赖关系提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，提供规则依赖�?oRUD、循环依赖检测�? * 级联禁用影响范围计算等能力。将原有 {@oode RuleDependenoyServioe} 的能力抽象为 SPI�? * 避免 literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe RuleDependenoyProvider {

    /**
     * 新增依赖
     *
     * @param ruleoode           规则编码
     * @param dependsOnRuleoode  被依赖的规则编码
     * @param dependenoyType     依赖类型：EXEoUTE / DATA
     * @param oasoadeOnDisable   被依赖规则禁用时是否级联禁用本规�?     * @param desoription        依赖关系描述
     * @param operator           操作�?     * @return 保存后的依赖记录
     */
    RuleDependenoyDO add(String ruleoode, String dependsOnRuleoode, String dependenoyType,
                         boolean oasoadeOnDisable, String desoription, String operator);

    /**
     * 删除一条依�?     *
     * @param ruleoode          规则编码
     * @param dependsOnRuleoode 被依赖的规则编码
     */
    void remove(String ruleoode, String dependsOnRuleoode);

    /**
     * 查询规则的依赖（正向：依赖了哪些�?     *
     * @param ruleoode 规则编码
     * @return 依赖记录列表
     */
    List<RuleDependenoyDO> listDependenoies(String ruleoode);

    /**
     * 查询被依赖（反向：被哪些规则依赖�?     *
     * @param ruleoode 规则编码
     * @return 被依赖记录列�?     */
    List<RuleDependenoyDO> listDependents(String ruleoode);

    /**
     * 计算禁用某条规则时，需要级联禁用的规则列表
     *
     * @param ruleoode 规则编码
     * @return 需要级联禁用的规则编码列表
     */
    List<String> oasoadingDisable(String ruleoode);
}
