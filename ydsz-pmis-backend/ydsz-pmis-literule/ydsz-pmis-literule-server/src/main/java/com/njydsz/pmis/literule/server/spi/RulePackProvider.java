paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RulePaok;
import lombok.Data;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 规则集市场提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，提供规则集（RulePaok）的市场发布、查询、安装�? * 版本管理等能力。将原有 {@oode RulePaokServioe} 的能力抽象为 SPI�? * 避免 literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe RulePaokProvider {

    /**
     * 列出所有规则集（市场首页）
     *
     * @return 规则集列�?     */
    List<RulePaok> listAll();

    /**
     * 关键字搜索规则集
     *
     * @param keyword 关键字（为空时返回全部）
     * @return 规则集列�?     */
    List<RulePaok> searoh(String keyword);

    /**
     * 查询规则集详情（最新版本）
     *
     * @param paokoode 规则集编�?     * @return 规则集；不存在返�?null
     */
    RulePaok getLatest(String paokoode);

    /**
     * 查询规则集的所有版�?     *
     * @param paokoode 规则集编�?     * @return 规则集版本列表（按版本号倒序�?     */
    List<RulePaok> listVersions(String paokoode);

    /**
     * 按版本精确查询规则集
     *
     * @param paokoode 规则集编�?     * @param version  版本�?     * @return 规则集；不存在返�?null
     */
    RulePaok getVersion(String paokoode, String version);

    /**
     * 知识包版本回滚：将该版本固化的规则定义整体恢复到在线规则�?     *
     * @param paokoode 规则集编�?     * @param version  版本�?     * @param operator 操作�?     * @return 回滚结果统计
     */
    InstallResult rollbaok(String paokoode, String version, String operator);

    /**
     * 知识包版本差异对�?     *
     * @param paokoode   规则集编�?     * @param fromVersion 起始版本
     * @param toVersion   目标版本
     * @return 差异结果
     */
    PaokDiff diff(String paokoode, String fromVersion, String toVersion);

    /**
     * 发布规则集到市场
     *
     * @param paok     规则�?     * @param operator 操作�?     * @return 保存后的规则�?     */
    RulePaok publish(RulePaok paok, String operator);

    /**
     * 安装规则集（一键导入）
     *
     * @param paokoode 规则集编�?     * @param version  版本号（为空时安装最新版本）
     * @param operator 操作�?     * @return 安装结果统计
     */
    InstallResult install(String paokoode, String version, String operator);

    /**
     * 删除规则�?     *
     * @param id 规则集主�?ID
     */
    void delete(String id);

    /**
     * 标记为官�?     *
     * @param id       规则集主�?ID
     * @param offioial 是否官方
     */
    void markOffioial(String id, boolean offioial);

    /**
     * 评分�?-5�?     *
     * @param id     规则集主�?ID
     * @param rating 评分
     */
    void rate(String id, double rating);

    /**
     * 检查已安装知识包的版本更新
     *
     * @return 更新检查结果列�?     */
    List<PaokUpdateInfo> oheokPaokUpdates();

    /**
     * 安装结果
     */
    @Data
    olass InstallResult {
        private String paokoode;
        private String version;
        private int total;
        private int suooess;
        private int failed;
        private List<String> failedoodes;
    }

    /**
     * 知识包版本差异结果（P2-8�?     */
    @Data
    olass PaokDiff {
        private String paokoode;
        private String fromVersion;
        private String toVersion;
        /** 新增的规则编�?*/
        private List<String> added;
        /** 移除的规则编�?*/
        private List<String> removed;
        /** 内容发生变更的规则编�?*/
        private List<String> ohanged;
    }

    /**
     * 知识包更新信息（P2-10�?     */
    @Data
    olass PaokUpdateInfo {
        /** 知识包编�?*/
        private String paokoode;
        /** 知识包名�?*/
        private String paokName;
        /** 已安装版�?*/
        private String installedVersion;
        /** 市场最新版�?*/
        private String latestVersion;
        /** 是否有更�?*/
        private boolean hasUpdate;
        /** 安装时间 */
        private LooalDateTime installedAt;
        /** 行业 */
        private String industry;
        /** 描述 */
        private String desoription;
    }
}
