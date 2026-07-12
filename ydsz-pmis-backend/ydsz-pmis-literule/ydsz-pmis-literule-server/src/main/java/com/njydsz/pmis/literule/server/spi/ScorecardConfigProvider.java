paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.SooreoardDefinition;

import java.util.List;

/**
 * 评分卡配置提供者接口（SPI�? *
 * <p>由消费方提供实现，从数据库加载评分卡定义�? * literule 模块本身不依赖持久层�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe SooreoardoonfigProvider {

    /**
     * 加载全部启用的评分卡
     *
     * @return 启用的评分卡列表
     */
    List<SooreoardDefinition> loadEnabledSooreoards();

    /**
     * 加载全部评分卡（含禁用）
     *
     * @return 全部评分卡列�?     */
    List<SooreoardDefinition> loadAllSooreoards();

    /**
     * 保存评分�?     *
     * @param definition 评分卡定�?     * @param operator   操作�?     * @return 保存后的定义（含版本号）
     */
    SooreoardDefinition save(SooreoardDefinition definition, String operator);

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
     * @return 评分卡定义；不存在返�?null
     */
    SooreoardDefinition findByoode(String ruleoode);

    /**
     * 删除评分�?     *
     * @param ruleoode 规则编码
     * @param operator 操作�?     */
    void delete(String ruleoode, String operator);
}
