package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.vo.DictVersionVO;

/**
 * 字典版本 Service 接口
 *
 * <p>提供字典变更版本记录和查询能力，支持回滚审计。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>版本记录</b>：{@link #createVersion} — 字典项增删改时自动调用，记录变更前的全量快照</li>
 *   <li><b>历史查询</b>：{@link #listByTypeCode} — 管理后台「字典版本管理」数据源</li>
 *   <li><b>回滚支持</b>：快照数据可一键回滚到任意历史版本</li>
 * </ul>
 *
 * <p><b>版本生成策略：</b>版本号默认 {@code "v" + System.currentTimeMillis()}，
 * 满足「按时间排序」需求；如需语义化版本号（如 {@code v1.2.0}），调用方可在
 * {@link #createVersion} 传入自定义版本字符串。
 *
 * <p><b>快照机制：</b>{@code snapshotJson} 保存变更前全量字典项 JSON 字符串，
 * 大小一般 < 1MB；超过时需在调用方自行压缩。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictItemService 字典项 Service（写操作时调用 {@link #createVersion}）
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 */
public interface DictVersionService {

    /**
     * 按类型编码查询版本历史
     *
     * <p>返回该类型下所有版本记录，按 {@code effectiveDate} 倒序。
     *
     * @param typeCode 字典类型编码
     * @return 版本列表（按生效时间倒序）
     */
    List<DictVersionVO> listByTypeCode(String typeCode);

    /**
     * 创建版本快照
     *
     * <p>由 {@link DictItemService} 在写操作（{@code save / updateById / removeById}）成功后异步调用。
     * {@code snapshotJson} 一般为变更前的字典项列表 JSON 字符串（{@code null} 表示空快照）。
     *
     * @param typeCode      字典类型编码
     * @param version       版本号（如 {@code v1734567890123} 或 {@code v1.2.0}）
     * @param changeLog     变更说明（遵循 Conventional Commits 规范）
     * @param snapshotJson  字典项列表 JSON 快照（可为 {@code null}）
     * @return 新建版本记录主键 ID
     */
    String createVersion(String typeCode, String version, String changeLog, String snapshotJson);

    /**
     * 回滚字典到指定版本
     *
     * <p>执行链路：
     * <ol>
     *   <li>校验目标版本是否存在且未删除</li>
     *   <li>查询当前字典项作为回滚前快照（用于审计）</li>
     *   <li>物理删除当前字典项（按 typeCode）</li>
     *   <li>从 {@code snapshotJson} 反序列化并批量插入历史字典项</li>
     *   <li>创建新版本记录（标记回滚来源，保持完整审计链）</li>
     *   <li>失效该 typeCode 下所有缓存</li>
     * </ol>
     *
     * <p><b>审计设计：</b>回滚操作创建一个<b>新版本</b>而非覆盖历史，
     * 新版本 {@code changeLog} 标注「回滚自 {sourceVersion}」，
     * 旧版本永远不变（不可变记录原则）。
     *
     * <p><b>快照兼容性：</b>若 {@code snapshotJson} 为空或解析失败，
     * 将清空当前字典项但不再重建（行为等同「重置为空字典」）。
     *
     * @param typeCode      字典类型编码
     * @param targetVersion 目标版本号（如 {@code v1734567890123}）
     * @param operatorId    操作人 ID（审计用途）
     * @return 新创建的回滚版本 ID
     * @throws com.njydsz.common.exception.custom.BusinessException 版本不存在时抛出 DICT_VERSION_NOT_FOUND
     */
    String rollbackTo(String typeCode, String targetVersion, String operatorId);
}
