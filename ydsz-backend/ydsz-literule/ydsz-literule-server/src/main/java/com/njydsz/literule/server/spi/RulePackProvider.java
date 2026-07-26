package com.njydsz.literule.server.spi;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.literule.api.RulePack;

import lombok.Data;

/**
 * 规则集市场提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供规则集（RulePack）的市场发布、查询、安装、
 * 版本管理等能力。将原有 {@code RulePackService} 的能力抽象为 SPI，
 * 避免 literule 模块直接依赖 project 模块。
 *
 * @since 1.0.0
 */
public interface RulePackProvider {

    /**
     * 列出所有规则集（市场首页）
     *
     * @return 规则集列表
     */
    List<RulePack> listAll();

    /**
     * 关键字搜索规则集
     *
     * @param keyword 关键字（为空时返回全部）
     * @return 规则集列表
     */
    List<RulePack> search(String keyword);

    /**
     * 查询规则集详情（最新版本）
     *
     * @param packCode 规则集编码
     * @return 规则集；不存在返回 null
     */
    RulePack getLatest(String packCode);

    /**
     * 查询规则集的所有版本
     *
     * @param packCode 规则集编码
     * @return 规则集版本列表（按版本号倒序）
     */
    List<RulePack> listVersions(String packCode);

    /**
     * 按版本精确查询规则集
     *
     * @param packCode 规则集编码
     * @param version  版本号
     * @return 规则集；不存在返回 null
     */
    RulePack getVersion(String packCode, String version);

    /**
     * 知识包版本回滚：将该版本固化的规则定义整体恢复到在线规则表
     *
     * @param packCode 规则集编码
     * @param version  版本号
     * @param operator 操作人
     * @return 回滚结果统计
     */
    InstallResult rollback(String packCode, String version, String operator);

    /**
     * 知识包版本差异对比
     *
     * @param packCode   规则集编码
     * @param fromVersion 起始版本
     * @param toVersion   目标版本
     * @return 差异结果
     */
    PackDiff diff(String packCode, String fromVersion, String toVersion);

    /**
     * 发布规则集到市场
     *
     * @param pack     规则集
     * @param operator 操作人
     * @return 保存后的规则集
     */
    RulePack publish(RulePack pack, String operator);

    /**
     * 安装规则集（一键导入）
     *
     * @param packCode 规则集编码
     * @param version  版本号（为空时安装最新版本）
     * @param operator 操作人
     * @return 安装结果统计
     */
    InstallResult install(String packCode, String version, String operator);

    /**
     * 删除规则集
     *
     * @param id 规则集主键 ID
     */
    void delete(String id);

    /**
     * 标记为官方
     *
     * @param id       规则集主键 ID
     * @param official 是否官方
     */
    void markOfficial(String id, boolean official);

    /**
     * 评分（0-5）
     *
     * @param id     规则集主键 ID
     * @param rating 评分
     */
    void rate(String id, double rating);

    /**
     * 检查已安装知识包的版本更新
     *
     * @return 更新检查结果列表
     */
    List<PackUpdateInfo> checkPackUpdates();

    /**
     * 安装结果
     */
    @Data
    class InstallResult {
        private String packCode;
        private String version;
        private int total;
        private int success;
        private int failed;
        private List<String> failedCodes;
    }

    /**
     * 知识包版本差异结果（P2-8）
     */
    @Data
    class PackDiff {
        private String packCode;
        private String fromVersion;
        private String toVersion;
        /** 新增的规则编码 */
        private List<String> added;
        /** 移除的规则编码 */
        private List<String> removed;
        /** 内容发生变更的规则编码 */
        private List<String> changed;
    }

    /**
     * 知识包更新信息（P2-10）
     */
    @Data
    class PackUpdateInfo {
        /** 知识包编码 */
        private String packCode;
        /** 知识包名称 */
        private String packName;
        /** 已安装版本 */
        private String installedVersion;
        /** 市场最新版本 */
        private String latestVersion;
        /** 是否有更新 */
        private boolean hasUpdate;
        /** 安装时间 */
        private LocalDateTime installedAt;
        /** 行业 */
        private String industry;
        /** 描述 */
        private String description;
    }
}
