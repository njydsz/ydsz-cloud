package com.njydsz.cronjob.server.core.handler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;

import lombok.extern.slf4j.Slf4j;

/**
 * GLUE 编辑器 undo/redo 会话管理（P3-3）。
 *
 * <p>在编辑 GLUE 脚本时为每个 (userId + jobId) 维护 undo/redo 栈：
 *
 * <ul>
 *   <li>Undo：回退到上次保存前的编辑器内容（内存快照）
 *   <li>Redo：重做上次撤销的操作（栈内缓存）
 * </ul>
 *
 * <p>注意：此 undo/redo 作用于编辑会话内的临时快照，不影响已持久化的 GLUE 版本（ {@link
 * com.njydsz.cronjob.server.service.schedule.GlueCodeService}）。 一旦调用 {@code save}，会话栈自动清空。
 *
 * <p>栈上限 50 条/会话，FIFO 淘汰旧条目；最近空闲超 30 分钟的会话会自动过期清理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class GlueEditorUndoRedoService {

  /** 单会话栈最大深度 */
  private static final int MAX_STACK_SIZE = 50;

  /** 会话过期阈值（毫秒），默认 30 分钟 */
  private static final long SESSION_EXPIRE_MS = 30 * 60 * 1000L;

  /** 编辑器状态快照（不可变值对象） */
  public record EditorSnapshot(String sourceCode, String language, long timestamp) {}

  /** 会话 undo/redo 双栈容器 */
  private static class EditSession {
    final Deque<EditorSnapshot> undoStack = new ArrayDeque<>();
    final Deque<EditorSnapshot> redoStack = new ArrayDeque<>();
    long lastAccessTime = System.currentTimeMillis();

    /**
     * 压入新快照（调用时意味着用户编辑产生了新内容）。
     *
     * <p>新快照入 undo 栈，redo 栈清空（新操作会覆盖 redo 路径）。
     */
    void pushSnapshot(EditorSnapshot snapshot) {
      undoStack.push(snapshot);
      if (undoStack.size() > MAX_STACK_SIZE) {
        undoStack.removeLast();
      }
      redoStack.clear();
      lastAccessTime = System.currentTimeMillis();
    }

    /** 撤销：undo 栈顶 → redo 栈；返回撤销后当前快照。 */
    EditorSnapshot undo() {
      if (undoStack.size() <= 1) {
        // 栈底保留初始快照，不可撤销
        return undoStack.isEmpty() ? null : undoStack.peek();
      }
      EditorSnapshot current = undoStack.pop();
      redoStack.push(current);
      lastAccessTime = System.currentTimeMillis();
      return undoStack.peek();
    }

    /** 重做：redo 栈顶 → undo 栈。 */
    EditorSnapshot redo() {
      if (redoStack.isEmpty()) {
        return null;
      }
      EditorSnapshot snapshot = redoStack.pop();
      undoStack.push(snapshot);
      lastAccessTime = System.currentTimeMillis();
      return snapshot;
    }

    boolean canUndo() {
      return undoStack.size() > 1;
    }

    boolean canRedo() {
      return !redoStack.isEmpty();
    }

    EditorSnapshot current() {
      return undoStack.isEmpty() ? null : undoStack.peek();
    }

    void clear() {
      undoStack.clear();
      redoStack.clear();
    }
  }

  /** 会话容器（key = userId + ":" + jobId） */
  private final Map<String, EditSession> sessions = new ConcurrentHashMap<>();

  /**
   * 初始化编辑器会话（打开编辑器时调用）。
   *
   * <p>以当前 GLUE 代码快照为初始状态压入 undo 栈，清空 redo 栈。
   *
   * @param userId 操作用户 ID
   * @param jobId 任务 ID
   * @param initialSnapshot 初始编辑器内容（当前持久化的最新版本）
   */
  public void initSession(String userId, String jobId, EditorSnapshot initialSnapshot) {
    if (initialSnapshot == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_glue_snapshot_required")
          .build();
    }
    EditSession session = new EditSession();
    session.pushSnapshot(initialSnapshot);
    sessions.put(key(userId, jobId), session);
    log.debug("[GlueEditor] 初始化编辑会话: userId={} jobId={}", userId, jobId);
  }

  /**
   * 压入新编辑快照（用户编辑时调用，每次内容变更调用一次）。
   *
   * @param userId 操作用户 ID
   * @param jobId 任务 ID
   * @param snapshot 新的编辑器内容快照
   */
  public void pushSnapshot(String userId, String jobId, EditorSnapshot snapshot) {
    EditSession session = getSession(userId, jobId);
    session.pushSnapshot(snapshot);
  }

  /**
   * 撤销到上个快照。
   *
   * @param userId 操作用户 ID
   * @param jobId 任务 ID
   * @return 撤销后的快照；若已到栈顶则返回原快照
   */
  public EditorSnapshot undo(String userId, String jobId) {
    EditSession session = getSession(userId, jobId);
    EditorSnapshot snapshot = session.undo();
    log.debug("[GlueEditor] undo: userId={} jobId={} canRedo={}", userId, jobId, session.canRedo());
    return snapshot;
  }

  /**
   * 重做上次撤销的操作。
   *
   * @param userId 操作用户 ID
   * @param jobId 任务 ID
   * @return 重做后的快照；无可Redo操作时返回 null
   */
  public EditorSnapshot redo(String userId, String jobId) {
    EditSession session = getSession(userId, jobId);
    EditorSnapshot snapshot = session.redo();
    log.debug("[GlueEditor] redo: userId={} jobId={} canUndo={}", userId, jobId, session.canUndo());
    return snapshot;
  }

  /** 查询当前快照（最新编辑状态）。 */
  public EditorSnapshot current(String userId, String jobId) {
    EditSession session = sessions.get(key(userId, jobId));
    return session == null ? null : session.current();
  }

  /** 查询是否可撤销。 */
  public boolean canUndo(String userId, String jobId) {
    EditSession session = sessions.get(key(userId, jobId));
    return session != null && session.canUndo();
  }

  /** 查询是否可重做。 */
  public boolean canRedo(String userId, String jobId) {
    EditSession session = sessions.get(key(userId, jobId));
    return session != null && session.canRedo();
  }

  /**
   * 清空会话（保存成功或关闭编辑器时调用，释放内存）。
   *
   * @param userId 操作用户 ID
   * @param jobId 任务 ID
   */
  public void clearSession(String userId, String jobId) {
    EditSession removed = sessions.remove(key(userId, jobId));
    if (removed != null) {
      log.debug("[GlueEditor] 关闭编辑会话: userId={} jobId={}", userId, jobId);
    }
  }

  /**
   * 清理过期会话（由 MaintenanceScheduler 定期调用）。
   *
   * @return 清理的会话数
   */
  public int evictExpiredSessions() {
    long now = System.currentTimeMillis();
    int before = sessions.size();
    sessions.entrySet().stream()
        .filter(e -> now - e.getValue().lastAccessTime > SESSION_EXPIRE_MS)
        .map(Map.Entry::getKey)
        .toList()
        .forEach(sessions::remove);
    int evicted = before - sessions.size();
    if (evicted > 0) {
      log.info("[GlueEditor] 清理 {} 个过期编辑会话", evicted);
    }
    return evicted;
  }

  private EditSession getSession(String userId, String jobId) {
    EditSession session = sessions.get(key(userId, jobId));
    if (session == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("error.cronjob.msg_glue_session_not_found")
          .build();
    }
    return session;
  }

  private String key(String userId, String jobId) {
    return userId + ":" + jobId;
  }
}
