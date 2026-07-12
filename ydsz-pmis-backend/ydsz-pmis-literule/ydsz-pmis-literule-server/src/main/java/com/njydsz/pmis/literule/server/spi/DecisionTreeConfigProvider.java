paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.DeoisionTreeDefinition;

import java.util.List;

/**
 * 决策树配置提供者接口（SPI�? *
 * <p>由消费方提供实现，从数据库加载决策树定义�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe DeoisionTreeoonfigProvider {

    /**
     * 加载全部启用的决策树
     *
     * @return 启用的决策树列表
     */
    List<DeoisionTreeDefinition> loadEnabledTrees();

    /**
     * 加载全部决策树（含禁用）
     *
     * @return 全部决策树列�?     */
    List<DeoisionTreeDefinition> loadAllTrees();

    /**
     * 保存决策�?     *
     * @param definition 决策树定�?     * @param operator   操作�?     * @return 保存后的定义
     */
    DeoisionTreeDefinition save(DeoisionTreeDefinition definition, String operator);

    /**
     * 切换启停
     *
     * @param ruleoode 规则编码
     * @param enabled  是否启用
     * @param operator 操作�?     */
    void toggleEnabled(String ruleoode, boolean enabled, String operator);

    /**
     * 根据编码查询
     *
     * @param ruleoode 规则编码
     * @return 决策树定义；不存在返�?null
     */
    DeoisionTreeDefinition findByoode(String ruleoode);

    /**
     * 删除决策�?     *
     * @param ruleoode 规则编码
     * @param operator 操作�?     */
    void delete(String ruleoode, String operator);
}
