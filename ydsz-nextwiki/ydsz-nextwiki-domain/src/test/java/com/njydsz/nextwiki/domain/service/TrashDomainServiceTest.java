package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.TrashStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link TrashDomainService} 单元测试。
 *
 * <p>覆盖：移入回收站、恢复（状态校验 + 事件构建）、永久删除、过期批量清理。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("回收站领域服务")
class TrashDomainServiceTest {

  private TrashDomainService service;

  @BeforeEach
  void setUp() {
    service = new TrashDomainService(new SnowflakeIdGenerator());
  }

  private FileNodeVO node() {
    return FileNodeVO.builder()
        .id("node-1")
        .name("a.txt")
        .path("/a.txt/")
        .parentId("p-1")
        .nodeType("file")
        .size(100L)
        .build();
  }

  private TrashItemDTO inTrashItem() {
    return TrashItemDTO.builder()
        .id("trash-1")
        .fileNodeId("node-1")
        .originalName("a.txt")
        .status(TrashStatus.IN_TRASH.getCode())
        .build();
  }

  @Nested
  @DisplayName("moveToTrash 移入回收站")
  class MoveToTrash {

    @Test
    @DisplayName("构建条目并设置 30 天保留期")
    void buildsTrashItem() {
      TrashItemDTO item = service.moveToTrash(node(), "user-1");

      assertThat(item.getId()).isNotBlank();
      assertThat(item.getFileNodeId()).isEqualTo("node-1");
      assertThat(item.getOriginalName()).isEqualTo("a.txt");
      assertThat(item.getOriginalPath()).isEqualTo("/a.txt/");
      assertThat(item.getOriginalParentId()).isEqualTo("p-1");
      assertThat(item.getNodeType()).isEqualTo("file");
      assertThat(item.getSize()).isEqualTo(100L);
      assertThat(item.getStatus()).isEqualTo(TrashStatus.IN_TRASH.getCode());
      assertThat(item.getDeletedTime()).isNotNull();
      assertThat(item.getPurgeTime()).isEqualTo(item.getDeletedTime().plusDays(30));
      assertThat(item.getCreatedBy()).isEqualTo("user-1");
      assertThat(item.getUpdatedBy()).isEqualTo("user-1");
    }
  }

  @Nested
  @DisplayName("restore 恢复")
  class Restore {

    @Test
    @DisplayName("IN_TRASH 可恢复并构建事件")
    void restoresAndBuildsEvent() {
      TrashItemDTO item = inTrashItem();
      FileOperatedEvent event = service.restore(item, node(), "user-1");

      assertThat(item.getStatus()).isEqualTo(TrashStatus.RESTORED.getCode());
      assertThat(item.getUpdatedBy()).isEqualTo("user-1");
      assertThat(item.getUpdatedAt()).isNotNull();
      assertThat(event.getOperation()).isEqualTo(FileOperatedEvent.OP_RESTORE);
      assertThat(event.getFileNodeId()).isEqualTo("node-1");
      assertThat(event.getFileName()).isEqualTo("a.txt");
      assertThat(event.getOperatorId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("已 RESTORED 再次恢复（同状态迁移）不抛异常")
    void restoredAgainIsAllowed() {
      TrashItemDTO item = inTrashItem();
      item.setStatus(TrashStatus.RESTORED.getCode());
      FileOperatedEvent event = service.restore(item, node(), "user-1");
      assertThat(event.getOperation()).isEqualTo(FileOperatedEvent.OP_RESTORE);
      assertThat(item.getStatus()).isEqualTo(TrashStatus.RESTORED.getCode());
    }

    @Test
    @DisplayName("已 PURGED 不可恢复")
    void purgedThrows() {
      TrashItemDTO item = inTrashItem();
      item.setStatus(TrashStatus.PURGED.getCode());
      BusinessException ex =
          catchThrowableOfType(() -> service.restore(item, node(), "user-1"), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.TRASH_INVALID_STATUS.getCode());
    }

    @Test
    @DisplayName("状态非法（未知编码）抛 TRASH_INVALID_STATUS")
    void unknownStatusThrows() {
      TrashItemDTO item = inTrashItem();
      item.setStatus("bogus");
      BusinessException ex =
          catchThrowableOfType(() -> service.restore(item, node(), "user-1"), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.TRASH_INVALID_STATUS.getCode());
    }

    @Test
    @DisplayName("node 为 null 也可恢复")
    void nullNodeStillRestores() {
      TrashItemDTO item = inTrashItem();
      FileOperatedEvent event = service.restore(item, null, "user-1");
      assertThat(event.getOperation()).isEqualTo(FileOperatedEvent.OP_RESTORE);
    }
  }

  @Nested
  @DisplayName("purge 永久删除")
  class Purge {

    @Test
    @DisplayName("状态置 PURGED 并记录操作人")
    void purges() {
      TrashItemDTO item = inTrashItem();
      service.purge(item, "user-1");
      assertThat(item.getStatus()).isEqualTo(TrashStatus.PURGED.getCode());
      assertThat(item.getUpdatedBy()).isEqualTo("user-1");
      assertThat(item.getUpdatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("cleanupExpiredItems 批量清理")
  class CleanupExpiredItems {

    @Test
    @DisplayName("清理全部过期条目并返回数量")
    void cleansAll() {
      TrashItemDTO a = inTrashItem();
      a.setId("a");
      TrashItemDTO b = inTrashItem();
      b.setId("b");
      int cleaned = service.cleanupExpiredItems(List.of(a, b), "system");
      assertThat(cleaned).isEqualTo(2);
      assertThat(a.getStatus()).isEqualTo(TrashStatus.PURGED.getCode());
      assertThat(b.getStatus()).isEqualTo(TrashStatus.PURGED.getCode());
    }

    @Test
    @DisplayName("空列表返回 0")
    void emptyReturnsZero() {
      assertThat(service.cleanupExpiredItems(List.of(), "system")).isZero();
    }
  }
}
