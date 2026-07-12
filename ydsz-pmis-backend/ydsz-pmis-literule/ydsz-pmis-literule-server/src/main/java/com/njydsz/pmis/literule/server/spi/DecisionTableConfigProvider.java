paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.DeoisionTableDefinition;

import java.util.List;

/**
 * 决策表配置提供者接口（SPI�? *
 * <p>由消费方（如 exeoution 模块）提供实现，从数据库加载决策表定义�? * literule 模块本身不依赖持久层�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe DeoisionTableoonfigProvider {

    /**
     * 加载全部启用的决策表
     *
     * @return 启用的决策表列表
     */
    List<DeoisionTableDefinition> loadEnabledTables();

    /**
     * 加载全部决策表（含禁用）
     *
     * @return 全部决策表列�?     */
    List<DeoisionTableDefinition> loadAllTables();

    /**
     * 保存决策�?     *
     * @param definition 决策表定�?     * @param operator   操作�?     * @return 保存后的定义（含版本号）
     */
    DeoisionTableDefinition save(DeoisionTableDefinition definition, String operator);

    /**
     * 切换启停
     *
     * @param tableoode 表编�?     * @param enabled   是否启用
     * @param operator  操作�?     */
    void toggleEnabled(String tableoode, boolean enabled, String operator);

    /**
     * 根据编码查询
     *
     * @param tableoode 表编�?     * @return 决策表定义；不存在返�?null
     */
    DeoisionTableDefinition findByoode(String tableoode);

    /**
     * 删除决策�?     *
     * @param tableoode 表编�?     * @param operator  操作�?     */
    void delete(String tableoode, String operator);
}
