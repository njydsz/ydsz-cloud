package com.njydsz.nextwiki.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link FileNodeVO} 单元测试。
 *
 * <p>覆盖：类型/根节点/状态判定、后代关系、移动合法性、路径计算、状态变更方法。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("文件节点聚合根 VO")
class FileNodeVOTest {

  private FileNodeVO node() {
    return FileNodeVO.builder().id("n-1").name("a.txt").nodeType("file").parentId("p-1").build();
  }

  @Nested
  @DisplayName("类型判定")
  class TypeCheck {

    @Test
    @DisplayName("isFolder / isFile 互斥")
    void folderAndFileExclusive() {
      assertThat(FileNodeVO.builder().nodeType("folder").build().isFolder()).isTrue();
      assertThat(FileNodeVO.builder().nodeType("file").build().isFile()).isTrue();
      assertThat(FileNodeVO.builder().nodeType("folder").build().isFile()).isFalse();
      assertThat(FileNodeVO.builder().nodeType("file").build().isFolder()).isFalse();
    }

    @Test
    @DisplayName("nodeType 为 null 时两者均为 false")
    void nullTypeIsNeither() {
      assertThat(FileNodeVO.builder().build().isFolder()).isFalse();
      assertThat(FileNodeVO.builder().build().isFile()).isFalse();
    }
  }

  @Nested
  @DisplayName("isRoot 根节点判定")
  class RootCheck {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0"})
    @DisplayName("null / 空 / \"0\" 视为根节点")
    void rootWhenParentMissing(String parentId) {
      assertThat(FileNodeVO.builder().parentId(parentId).build().isRoot()).isTrue();
    }

    @Test
    @DisplayName("非根父 ID 不是根节点")
    void notRoot() {
      assertThat(node().isRoot()).isFalse();
    }
  }

  @Nested
  @DisplayName("isActive / isLocked 状态判定")
  class StatusCheck {

    @Test
    @DisplayName("status=null 视为活跃")
    void nullStatusActive() {
      assertThat(FileNodeVO.builder().build().isActive()).isTrue();
    }

    @Test
    @DisplayName("active 活跃，locked 锁定")
    void activeAndLocked() {
      assertThat(FileNodeVO.builder().status("active").build().isActive()).isTrue();
      assertThat(FileNodeVO.builder().status("locked").build().isLocked()).isTrue();
      assertThat(FileNodeVO.builder().status("locked").build().isActive()).isFalse();
    }
  }

  @Nested
  @DisplayName("isDescendantOf 后代关系")
  class Descendant {

    @Test
    @DisplayName("同 ID 视为自身后代")
    void sameIdIsDescendant() {
      FileNodeVO child = node();
      child.setPath("/a/b/c/");
      FileNodeVO ancestor = node();
      ancestor.setPath("/a/b/");
      assertThat(child.isDescendantOf(ancestor)).isTrue();
    }

    @Test
    @DisplayName("路径前缀匹配视为后代")
    void pathPrefixIsDescendant() {
      FileNodeVO child = node();
      child.setId("n-2");
      child.setPath("/a/b/c/");
      FileNodeVO ancestor = FileNodeVO.builder().id("a").path("/a/b/").build();
      assertThat(child.isDescendantOf(ancestor)).isTrue();
    }

    @Test
    @DisplayName("路径不匹配返回 false")
    void differentPathNotDescendant() {
      FileNodeVO child = node();
      child.setId("n-2");
      child.setPath("/x/y/");
      FileNodeVO ancestor = FileNodeVO.builder().id("a").path("/a/b/").build();
      assertThat(child.isDescendantOf(ancestor)).isFalse();
    }

    @Test
    @DisplayName("祖先为 null 或路径为 null 返回 false")
    void nullArgsReturnFalse() {
      FileNodeVO child = node();
      child.setPath("/a/");
      assertThat(child.isDescendantOf(null)).isFalse();
      assertThat(child.isDescendantOf(FileNodeVO.builder().id("a").build())).isFalse();
    }
  }

  @Nested
  @DisplayName("canMoveTo 移动合法性")
  class CanMoveTo {

    @Test
    @DisplayName("目标父目录为 null 抛 FILE_FOLDER_NOT_FOUND")
    void nullTargetThrows() {
      BusinessException ex = catchThrowableOfType(() -> node().canMoveTo(null), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_FOLDER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("目标非目录抛 FILE_PARENT_NOT_FOLDER")
    void nonFolderTargetThrows() {
      FileNodeVO fileParent = FileNodeVO.builder().id("f").nodeType("file").build();
      BusinessException ex = catchThrowableOfType(() -> node().canMoveTo(fileParent), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_PARENT_NOT_FOLDER.getCode());
    }

    @Test
    @DisplayName("目录移动到自身路径前缀下抛 FILE_MOVE_TO_SELF")
    void moveFolderToOwnSubtreeThrows() {
      // this.isDescendantOf(targetParent)：this 的路径以 targetParent 路径为前缀时判为循环
      FileNodeVO folder = FileNodeVO.builder().id("d").nodeType("folder").path("/a/b/").build();
      FileNodeVO targetParent = FileNodeVO.builder().id("p").nodeType("folder").path("/a/").build();
      BusinessException ex = catchThrowableOfType(() -> folder.canMoveTo(targetParent), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_MOVE_TO_SELF.getCode());
    }

    @Test
    @DisplayName("目录移动到无路径前缀关系的目录合法")
    void moveFolderToUnrelatedFolderOk() {
      FileNodeVO folder = FileNodeVO.builder().id("d").nodeType("folder").path("/a/b/").build();
      FileNodeVO targetParent = FileNodeVO.builder().id("p").nodeType("folder").path("/x/y/").build();
      folder.canMoveTo(targetParent);
    }

    @Test
    @DisplayName("文件移动到任意目录合法")
    void moveFileToFolderOk() {
      FileNodeVO folderParent = FileNodeVO.builder().id("p").nodeType("folder").path("/p/").build();
      node().canMoveTo(folderParent);
    }
  }

  @Nested
  @DisplayName("calculatePath 路径计算")
  class CalculatePath {

    @ParameterizedTest
    @CsvSource({
      "/a/,b.txt,/a/b.txt/",
      "a,b.txt,/a/b.txt/",
      "/a,b.txt,/a/b.txt/",
      "'',b.txt,/b.txt/",
      ",b.txt,/b.txt/"
    })
    @DisplayName("各种父路径形态拼接")
    void buildsChildPath(String parentPath, String childName, String expected) {
      assertThat(FileNodeVO.calculatePath(parentPath, childName)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("状态变更方法")
  class StateChange {

    @Test
    @DisplayName("changeParent 更新父节点与路径信息")
    void changeParentUpdatesFields() {
      FileNodeVO n = node();
      n.changeParent("new-p", "/new/", 2, 5, "user-9");
      assertThat(n.getParentId()).isEqualTo("new-p");
      assertThat(n.getPath()).isEqualTo("/new/");
      assertThat(n.getLevel()).isEqualTo(2);
      assertThat(n.getSort()).isEqualTo(5);
      assertThat(n.getUpdatedBy()).isEqualTo("user-9");
    }

    @Test
    @DisplayName("changeName 更新名称与路径")
    void changeNameUpdatesFields() {
      FileNodeVO n = node();
      n.changeName("b.txt", "/a/b.txt/", "user-9");
      assertThat(n.getName()).isEqualTo("b.txt");
      assertThat(n.getPath()).isEqualTo("/a/b.txt/");
      assertThat(n.getUpdatedBy()).isEqualTo("user-9");
    }

    @Test
    @DisplayName("markUpdatedBy 更新操作人")
    void markUpdatedBy() {
      FileNodeVO n = node();
      n.markUpdatedBy("user-9");
      assertThat(n.getUpdatedBy()).isEqualTo("user-9");
    }
  }
}
