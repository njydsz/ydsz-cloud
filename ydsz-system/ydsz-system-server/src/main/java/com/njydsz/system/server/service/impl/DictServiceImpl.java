package com.njydsz.system.server.service.impl;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;




/**
 * 字典类型 Service 实现
 *
 * <p>对 {@link DictService} 接口的完整实现，是「字典中心」的核心业务逻辑层。 维护 {@code ydsz_sys_dict_type} 字典类型表，是「字典项」（{@link
 * DictItemServiceImpl}）的父级元数据。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}，全部走 {@code @Transactional} 事务保证
 *   <li><b>唯一性校验</b>：保存 / 更新前校验 {@code (tenantId, typeCode)} 唯一性，冲突时抛 {@code
 *       IllegalArgumentException}
 *   <li><b>缓存联动</b>：写操作触发 {@code @CacheEvict} 失效 Redis 字典缓存 （{@code ydsz:dict:type:{typeCode}} /
 *       {@code ydsz:dict:full:{typeCode}}）， 由调用方在 Controller 层组合触发
 *   <li><b>全量查询</b>：{@link #listAll} 走本地 Caffeine 缓存（5min TTL）， 避免下拉框渲染触发 DB（具体由 {@code @Cacheable}
 *       在调用方实现）
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交
 *   <li>字典类型与字典项的强一致性由外层 Service（如 {@code DictSyncService}）保证事务边界
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离， 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>强校验</b>：删除字典类型前<b>必须</b>先删除其下所有字典项（由外层调用方控制）， 避免孤儿字典项
 *   <li><b>唯一性</b>：{@code typeCode} 是字典类型的「业务主键」（对前端可见）， 全租户内唯一，不能修改，只能新增新类型
 *   <li><b>扩展性</b>：通过 {@code DictItemServiceImpl} 挂载实际字典项， 单个 {@code typeCode} 下可有任意数量的 {@code
 *       itemCode}
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 管理后台新增字典类型
 * String typeId = dictService.save(DictTypeVO.builder()
 *     .typeCode("user_status")
 *     .typeName("用户状态")
 *     .description("在职 / 离职 / 休假等状态枚举")
 *     .build());
 *
 * // 然后挂载字典项
 * dictItemService.save(DictItemVO.builder()
 *     .typeCode("user_status").itemCode("ACTIVE").itemValue("在职").build());
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DictService 字典类型 Service 接口
 * @see com.njydsz.system.infra.entity.DictType 字典类型实体
 * @see DictItemServiceImpl 字典项 Service 实现（依赖本类创建类型后再挂载字典项）
 * @see DictVersionServiceImpl 字典版本 Service（写操作触发版本快照）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

  /** 字典仓储（聚合 DictTypeMapper / DictItemMapper） */
  private final DictRepository dictRepository;

  /** 统一领域事件发布门面（ObjectProvider 可选注入，common-event 未引入时安全降级，见《云顶编码规范》27.4） */
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  // ============================== CRUD ==============================

  /**
   * 分页查询字典类型（管理后台列表页）
   *
   * <p>支持按 {@code typeCode} 精确匹配、{@code typeName} 模糊匹配、{@code status} 精确匹配进行过滤， 按 {@code
   * created_at} 倒序返回。
   *
   * @param query 分页查询条件（含 {@code pageNum / pageSize / typeCode / typeName / status}）
   * @return 分页结果（含 {@code records / total}）
   */
  @Override
  public PageResponse<List<DictTypeVO>> page(DictPageQuery query) {
    return dictRepository.findTypePage(query);
  }

  /**
   * 根据主键查询字典类型
   *
   * @param id 字典类型主键
   * @return 字典类型 VO，不存在返回 null
   */
  @Override
  public DictTypeVO getById(String id) {
    return dictRepository.findTypeById(id).orElse(null);
  }

  /**
   * 新增字典类型
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO，默认 {@code status=ENABLED}
   *   <li>唯一性校验：{@code typeCode} 全租户内不能重复
   *   <li>插入 {@code ydsz_sys_dict_type} 表
   * </ol>
   *
   * <p><b>注意：</b>本方法仅创建类型，<b>不挂载字典项</b>，字典项需通过 {@link DictItemServiceImpl#save} 单独添加。
   *
   * @return 新创建的字典类型 ID
   * @throws IllegalArgumentException {@code typeCode} 已存在时抛出
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "'all:' + T(com.njydsz.common.tenant.TenantContextHolder).getTenantId()")
  @Transactional(rollbackFor = Exception.class)
  public String save(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    dictRepository.insertType(dto);
    publishDictTypeChangedEvent(dto.getTypeCode(), "创建字典类型");
    return dto.getId();
  }

  /**
   * 更新字典类型
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO
   *   <li>唯一性校验：{@code typeCode} 变更时不能与现有类型冲突
   *   <li>更新 {@code ydsz_sys_dict_type} 表
   * </ol>
   *
   * <p><b>注意：</b>更新 {@code typeCode} 会导致所有依赖该编码的下游缓存失效， 调用方需主动清理 {@code ydsz:dict:*} 相关 Redis key。
   *
   * @return true=更新成功，false=记录不存在
   * @throws IllegalArgumentException {@code typeCode} 已被其他类型占用时抛出
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "'all:' + T(com.njydsz.common.tenant.TenantContextHolder).getTenantId()")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(DictTypeDTO dto) {
    checkDuplicateTypeCode(dto);
    boolean updated = dictRepository.updateTypeById(dto);
    if (updated) {
      publishDictTypeChangedEvent(dto.getTypeCode(), "更新字典类型");
    }
    return updated;
  }

  /**
   * 逻辑删除字典类型
   *
   * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}）， 不真正从 DB 删除，便于审计回溯。
   *
   * <p><b>子项校验：</b>删除前校验该类型下是否存在字典项，若存在则抛出 {@link
   * SystemExceptionCode#DICT_TYPE_HAS_ITEMS} 阻止删除，防止孤儿字典项。
   *
   * @param id 字典类型主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "'all:' + T(com.njydsz.common.tenant.TenantContextHolder).getTenantId()")
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    DictTypeVO vo = dictRepository.findTypeById(id).orElse(null);
    if (vo == null) {
      return false;
    }
    // 子项校验：若该类型下存在字典项，阻止删除
    long itemCount = dictRepository.countItemsByTypeCode(vo.getTypeCode());
    if (itemCount > 0) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_HAS_ITEMS)
          .data("typeCode", vo.getTypeCode())
          .data("itemCount", itemCount);
    }
    boolean removed = dictRepository.deleteTypeById(id);
    if (removed) {
      publishDictTypeChangedEvent(vo.getTypeCode(), "删除字典类型");
    }
    return removed;
  }

  /**
   * 广播字典类型变更事件（用于跨实例本地缓存失效感知）。
   *
   * @param typeCode 字典类型编码
   * @param action 变更动作描述
   */
  private void publishDictTypeChangedEvent(String typeCode, String action) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("DictType")
            .aggregateId(typeCode)
            .eventType(DomainEventTypes.DICT_TYPE_CHANGED)
            .metadata("typeCode", typeCode)
            .metadata("action", action)
            .build());
  }

  // ============================== 业务查询 ==============================

  /**
   * 查询全部字典类型（不区分状态）
   *
   * <p>典型调用方：
   *
   * <ul>
   *   <li>管理后台「字典类型管理」列表页（带分页时用 {@link #page}）
   *   <li>「类型选择器」下拉框（高频读，走本地缓存）
   * </ul>
   *
   * <p><b>缓存策略：</b>走本地 Caffeine 缓存（5min TTL），避免全表扫描。 缓存键：{@code dict:type:all:{tenantId}}。
   *
   * @return 全部字典类型列表（按 createdAt 倒序）
   */
  @Override
  @Cacheable(
      value = CacheConstants.SYSTEM_DICT_TYPE_CACHE,
      key = "'all:' + T(com.njydsz.common.tenant.TenantContextHolder).getTenantId()")
  public List<DictTypeVO> listAll() {
    return dictRepository.findAllTypes();
  }

  // ============================== 私有方法 ==============================

  /**
   * 唯一性校验（私有）
   *
   * <p>校验 {@code typeCode} 是否已被其他字典类型占用。 更新场景下排除自身 ID（{@code ne("id", dto.getId())}）。
   *
   * @throws IllegalArgumentException {@code typeCode} 已存在时抛出
   */
  private void checkDuplicateTypeCode(DictTypeDTO dto) {
    if (dictRepository.existsTypeCode(dto.getTypeCode(), dto.getId())) {
      throw BusinessException.of(SystemExceptionCode.DICT_TYPE_CODE_DUPLICATE)
          .data("typeCode", dto.getTypeCode());
    }
  }
}
