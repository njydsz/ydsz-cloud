package com.remisoft.system.server.service;

import java.util.List;

import com.remisoft.system.domain.vo.DictVersionVO;

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
 * @author remi-team
 * @since 1.0.0
 *
 * @see DictItemService 字典项 Service（写操作时调用 {@link #createVersion}）
 * @see com.remisoft.system.domain.entity.DictVersion 字典版本实体
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
}
