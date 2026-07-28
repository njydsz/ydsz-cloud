package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典版本 VO
 *
 * <p>对应 {@code ydsz_dict_version} 表的展示视图，是「字典版本管理」列表 / 详情接口的返回值类型。
 * 字典版本是对<b>字典项全量快照</b>的版本化管理，对标 Git 提交模型。
 *
 * <p><b>字段语义：</b>
 * <ul>
 *   <li>{@code typeCode} — 字典类型编码，关联 {@link DictTypeVO#typeCode}</li>
 *   <li>{@code version} — 版本号（语义化版本，如 {@code 1.0.0} / {@code 1.1.0}）</li>
 *   <li>{@code changeLog} — 变更说明（必填，遵循「Conventional Commits」规范）</li>
 *   <li>{@code effectiveDate} — 生效时间，{@code null} 表示尚未生效</li>
 *   <li>{@code snapshotJson} — 快照数据（JSON 格式），包含该版本下所有字典项的完整数据，
 *       用于「版本回滚」一键恢复</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>字典变更审计：每次字典项增删改产生新版本</li>
 *   <li>版本回滚：误删字典项后通过「回滚到 v1.0.0」一键恢复</li>
 *   <li>多环境同步：dev 验证 → 生成版本 → sit / uat / prod 依次同步</li>
 *   <li>合规审计：金融/医疗行业需保留字典变更历史 N 年</li>
 * </ul>
 *
 * <p><b>版本管理策略：</b>
 * <ul>
 *   <li>每次发布产生一个新版本（不可变，{@code createdAt} 即发布时间）</li>
 *   <li>回滚操作通过「创建一个内容等于历史版本快照的新版本」实现</li>
 *   <li>查询时默认仅返回 {@code effectiveDate} 已过期的历史版本</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 * @see DictTypeVO 字典类型 VO
 * @see DictItemVO 字典项 VO
 */
@Data
@Schema(description = "字典版本视图对象")
public class DictVersionVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "字典类型编码")
    private String typeCode;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "变更说明")
    private String changeLog;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveDate;

    @Schema(description = "快照数据（JSON）")
    private String snapshotJson;

    @Schema(description = "发布时间")
    private LocalDateTime createdAt;
}
