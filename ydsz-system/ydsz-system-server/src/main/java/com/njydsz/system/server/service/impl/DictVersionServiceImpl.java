package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.enums.SystemResultCode;
import com.njydsz.system.infra.mapper.DictItemMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.system.domain.entity.DictVersion;
import com.njydsz.system.domain.vo.DictVersionVO;
import com.njydsz.system.infra.mapper.DictVersionMapper;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 字典版本 Service 实现
 *
 * <p>对 {@link DictVersionService} 接口的完整实现，是「字典中心」版本管理子系统的核心业务逻辑层。
 * 维护字典变更历史快照（schema + 全量字典项 JSON），对标大厂「配置中心 / 字典中心」版本管理能力，
 * 支持版本回滚、变更审计、配置复盘等场景。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>版本查询</b>：{@link #listByTypeCode} — 按 {@code typeCode} 查询某字典类型的所有历史版本，
 *       按 {@code effective_date} 倒序返回（最新版本在前）</li>
 *   <li><b>版本创建</b>：{@link #createVersion} — 由 {@link DictItemServiceImpl} 在写操作成功后调用，
 *       记录变更前的<b>全量字典项 JSON 快照</b>，写入 {@code ydsz_dict_version} 表</li>
 *   <li><b>版本回滚</b>：未来扩展 — 通过 {@code snapshotJson} 重建字典项（{@code restoreVersion}），
 *       当前仅保留快照数据，回滚由管理后台基于快照二次开发</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 *   <li>{@link #createVersion} 通常由 {@link DictItemServiceImpl#save} /
 *       {@link DictItemServiceImpl#updateById} / {@link DictItemServiceImpl#removeById} 触发，
 *       <b>必须在同一事务边界内</b>调用，确保字典项变更与版本快照原子性</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>不可变记录</b>：版本记录<b>仅新增</b>，不修改 / 不删除（保留完整审计链）</li>
 *   <li><b>版本号生成</b>：调用方传入（典型格式 {@code "v" + System.currentTimeMillis()}），
 *       内部不做格式校验</li>
 *   <li><b>快照大小</b>：典型字典快照 < 1MB，无需压缩；
 *       极端字典（如行政区划 70w+ 项）由调用方在写入前自行压缩</li>
 *   <li><b>软删除</b>：{@code ydsz_dict_version} 表采用 <b>逻辑删除</b>（{@code deleted} 字段），
 *       与物理删除不同</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 字典变更时自动创建版本（由 DictItemServiceImpl 内部调用）
 * String versionId = dictVersionService.createVersion(
 *     "user_status",
 *     "v" + System.currentTimeMillis(),
 *     "新增【离职】状态",
 *     YdszJson.toJson(snapshotBeforeChange)
 * );
 *
 * // 查询某字典的所有历史版本
 * List<DictVersionVO> versions = dictVersionService.listByTypeCode("user_status");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictVersionService 字典版本 Service 接口
 * @see DictItemServiceImpl 字典项 Service（写操作触发版本快照）
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 * @see com.njydsz.system.domain.vo.DictVersionVO 字典版本 VO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictVersionServiceImpl implements DictVersionService {

    /** 字典版本 Mapper（继承 {@code ydsz_dict_version} 表 CRUD） */
    private final DictVersionMapper mapper;
    /** 字典项 Mapper（用于回滚时删除/重建字典项） */
    private final DictItemMapper dictItemMapper;
    /** Redis 缓存服务（用于失效缓存） */
    private final RedisService redisService;

    /** 字典项缓存键前缀 */
    private static final String DICT_CACHE_PREFIX = "ydsz:dict:item:";

    /**
     * 按字典类型编码查询所有历史版本（按生效时间倒序）
     *
     * <p>典型调用方：管理后台「字典历史版本」列表页，运营 / 审计人员查看某字典的完整变更链路。
     *
     * <p><b>性能说明：</b>
     * <ul>
     *   <li>索引：{@code (type_code, deleted, effective_date DESC)}</li>
     *   <li>单字典类型历史版本一般 < 100 条，单次查询 < 10ms</li>
     *   <li>若某字典频繁变更（> 1000 版本），建议按时间区间分页查询（{@code listByTypeCode} 未来扩展）</li>
     * </ul>
     *
     * @param typeCode 字典类型编码（{@code ydsz_dict_type.type_code}）
     * @return 历史版本列表（最新生效时间在前），无版本时返回<b>空列表</b>（不是 null）
     */
    @Override
    public List<DictVersionVO> listByTypeCode(String typeCode) {
        return mapper.listByTypeCode(typeCode).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

    /**
     * 创建字典版本快照
     *
     * <p>由 {@link DictItemServiceImpl} 在字典项变更（增 / 删 / 改）后调用，
     * 记录变更前的<b>全量字典项 JSON 快照</b>，用于版本回滚和变更审计。
     *
     * <p><b>关键设计：</b>
     * <ul>
     *   <li><b>事务一致性</b>：调用方需在<b>字典项变更事务</b>内调用本方法，
     *       通过 Spring 事务传播保证原子性（{@code PROPAGATION_REQUIRED}）</li>
     *   <li><b>快照时机</b>：必须在<b>变更前</b>查询并快照原字典项，
     *       而非变更后（否则快照反映的是变更后的状态，无法回滚）</li>
     *   <li><b>版本号语义</b>：{@code version} 字段由调用方决定格式（典型：{@code "v" + 时间戳}），
     *       本方法不做格式校验</li>
     *   <li><b>生效时间</b>：{@code effectiveDate} 自动取当前时间</li>
     * </ul>
     *
     * @param typeCode      字典类型编码
     * @param version       版本号（由调用方决定格式）
     * @param changeLog     变更说明（如「新增【离职】状态」），用于审计展示
     * @param snapshotJson  变更前的<b>全量字典项 JSON 快照</b>，由调用方序列化
     * @return 新创建的版本 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createVersion(String typeCode, String version, String changeLog, String snapshotJson) {
        DictVersion entity = new DictVersion();
        entity.setTypeCode(typeCode);
        entity.setVersion(version);
        entity.setChangeLog(changeLog);
        entity.setSnapshotJson(snapshotJson);
        entity.setEffectiveDate(LocalDateTime.now());
        mapper.insert(entity);
        return entity.getId();
    }

    /**
     * 回滚字典到指定版本
     *
     * <p>事务边界内执行「查询快照 → 物理删除 → 批量插入 → 创建新版本 → 失效缓存」全链路。
     * 若中间步骤失败，整个事务回滚，字典数据保持原状。
     *
     * <p><b>审计设计：</b>回滚创建新版本（而非覆盖历史），
     * 新版本 changeLog = 「回滚自 {sourceVersion} by {operatorId}」，
     * 保持完整审计链（旧版本永不可变）。
     *
     * @param typeCode      字典类型编码
     * @param targetVersion 目标版本号
     * @param operatorId    操作人 ID
     * @return 新创建的回滚版本 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rollbackTo(String typeCode, String targetVersion, String operatorId) {
        // 1. 查询目标版本
        DictVersion targetVersionEntity = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictVersion>()
                        .eq("type_code", typeCode)
                        .eq("version", targetVersion)
                        .eq("deleted", 0)
        );
        if (targetVersionEntity == null) {
            throw BusinessException.of(SystemResultCode.DICT_VERSION_NOT_FOUND)
                    .data("typeCode", typeCode)
                    .data("version", targetVersion);
        }

        // 2. 查询当前字典项作为回滚前快照（用于审计回溯）
        List<DictItem> currentItems = dictItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictItem>()
                        .eq("type_code", typeCode)
                        .eq("deleted", 0)
        );
        String rollbackSnapshot = YdszJson.toJson(SystemConverter.INSTANT.dictItemListToVO(currentItems));

        // 3. 物理删除当前字典项
        int deletedCount = dictItemMapper.physicalDeleteByTypeCode(typeCode);
        log.info("[DictVersion] 回滚准备: typeCode={}, 删除 {} 条现有字典项", typeCode, deletedCount);

        // 4. 反序列化目标快照并重建字典项
        int insertedCount = 0;
        String snapshotJson = targetVersionEntity.getSnapshotJson();
        if (StringUtils.isNotBlank(snapshotJson)) {
            try {
                List<DictItemDTO> snapshotItems = YdszJson.fromJson(snapshotJson, java.util.List.class, DictItemDTO.class);
                if (snapshotItems != null && !snapshotItems.isEmpty()) {
                    for (DictItemDTO dto : snapshotItems) {
                        DictItem entity = new DictItem();
                        entity.setTypeCode(dto.getTypeCode());
                        entity.setItemCode(dto.getItemCode());
                        entity.setItemValue(dto.getItemValue());
                        entity.setSortOrder(dto.getSortOrder());
                        entity.setParentId(dto.getParentId());
                        entity.setDescription(dto.getDescription());
                        entity.setExtJson(dto.getExtJson());
                        entity.setStatus(dto.getStatus());
                        dictItemMapper.insert(entity);
                        insertedCount++;
                    }
                }
            } catch (Exception e) {
                log.error("[DictVersion] 快照解析失败: typeCode={}, version={}, error={}",
                        typeCode, targetVersion, e.getMessage());
                throw BusinessException.of(SystemResultCode.SNAPSHOT_PARSE_ERROR)
                        .data("reason", e.getMessage());
            }
        }

        // 5. 创建新版本（标记回滚来源）
        String newVersion = "v" + System.currentTimeMillis();
        String changeLog = String.format("回滚自 %s by %s (恢复 %d 条, 删除 %d 条)",
                targetVersion, operatorId, insertedCount, deletedCount);
        DictVersion newVersionEntity = new DictVersion();
        newVersionEntity.setTypeCode(typeCode);
        newVersionEntity.setVersion(newVersion);
        newVersionEntity.setChangeLog(changeLog);
        newVersionEntity.setSnapshotJson(rollbackSnapshot);
        newVersionEntity.setEffectiveDate(LocalDateTime.now());
        mapper.insert(newVersionEntity);

        // 6. 失效缓存
        evictCache(typeCode);

        log.info("[DictVersion] 回滚完成: typeCode={}, targetVersion={}, newVersion={}, 恢复 {} 条字典项",
                typeCode, targetVersion, newVersion, insertedCount);
        return newVersionEntity.getId();
    }

    /**
     * 失效指定 typeCode 下所有缓存（私有）
     *
     * @param typeCode 字典类型编码
     */
    private void evictCache(String typeCode) {
        // 删除列表缓存
        redisService.delete(DICT_CACHE_PREFIX + typeCode);
        // 删除 lookup 模式的缓存（ydsz:dict:item:{typeCode}:{itemCode}）
        try {
            redisService.advancedOps().deleteByPattern(DICT_CACHE_PREFIX + typeCode + ":*");
        } catch (Exception e) {
            log.warn("[DictVersion] 缓存模式删除失败（非关键路径）: typeCode={}, error={}", typeCode, e.getMessage());
        }
    }
}
