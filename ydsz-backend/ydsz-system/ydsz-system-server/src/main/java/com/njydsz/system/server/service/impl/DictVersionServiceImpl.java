package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
 * <p>对 {@link DictVersionService} 接口的完整实现，是「字典版本管理」的核心业务逻辑层。
 * 维护字典变更历史快照，支持版本回滚与审计。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>版本查询</b>：{@code listByTypeCode} — 按 typeCode 查询所有版本历史，按 {@code effective_date} 倒序</li>
 *   <li><b>版本创建</b>：{@code createVersion} — 由 {@link DictItemServiceImpl} 在写操作成功后调用，
 *       记录变更前全量字典项 JSON 快照</li>
 *   <li><b>版本回滚</b>：通过 {@code snapshotJson} 重建字典项（未来扩展）</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}；
 * 读方法不开启事务，依赖 MyBatis 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>版本号默认 {@code "v" + System.currentTimeMillis()}，按时间戳排序</li>
 *   <li>快照数据一般 < 1MB，无需压缩；超过时调用方需自行处理</li>
 *   <li>版本记录<b>不可变</b>（仅新增，不修改 / 不删除）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DictVersionService 字典版本 Service 接口
 * @see DictItemServiceImpl 字典项 Service（写操作触发版本快照）
 * @see com.njydsz.system.domain.entity.DictVersion 字典版本实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictVersionServiceImpl implements DictVersionService {

    private final DictVersionMapper mapper;

    @Override
    public List<DictVersionVO> listByTypeCode(String typeCode) {
        return mapper.listByTypeCode(typeCode).stream()
                .map(SystemConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

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
}
