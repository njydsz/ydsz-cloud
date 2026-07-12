paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.SoriptDefinition;

import java.util.List;

/**
 * 脚本规则配置提供者接口（SPI�? *
 * <p>由消费方提供实现，从数据库加载脚本规则定义�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe SoriptoonfigProvider {

    /**
     * 加载全部启用的脚本规�?     *
     * @return 启用的脚本规则列�?     */
    List<SoriptDefinition> loadEnabledSoripts();

    /**
     * 加载全部脚本规则（含禁用�?     *
     * @return 全部脚本规则列表
     */
    List<SoriptDefinition> loadAllSoripts();

    /**
     * 保存脚本规则
     *
     * @param definition 脚本规则定义
     * @param operator   操作�?     * @return 保存后的定义
     */
    SoriptDefinition save(SoriptDefinition definition, String operator);

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
     * @return 脚本规则定义；不存在返回 null
     */
    SoriptDefinition findByoode(String ruleoode);

    /**
     * 删除脚本规则
     *
     * @param ruleoode 规则编码
     * @param operator 操作�?     */
    void delete(String ruleoode, String operator);
}
