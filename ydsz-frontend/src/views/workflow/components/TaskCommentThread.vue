<!--
  @fileoverview 任务评论线程组件
  @description
    P2-3：任务级评论线程，支持楼中楼回复、评论类型（COMMENT / QUESTION）标记、
    删除自己的评论。
    集成于流程实例详情页与待办操作弹窗。
    配套自研工作流 v2 引擎，PC 端专用。
  @module views/workflow/components/TaskCommentThread
  @author ydsz-pmis-team
  @since 1.0.0
-->
<script setup lang="ts">
/**
 * @file 任务评论线程组件
 * @module components/TaskCommentThread
 * @description P2-3: 任务级评论线程，支持楼中楼回复、评论类型标记、删除自己的评论。
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Position } from '@element-plus/icons-vue'
import {
  listInstanceComments,
  addTaskComment,
  deleteTaskComment,
} from '@/api/workflow'
import type { TaskCommentDTO } from '@/api/workflow/types'
import type { ApiResponse } from '@/types/api'

const props = defineProps<{
  instanceId: string
  taskId?: string
  nodeCode?: string
  currentUserId?: string
}>()

const loading = ref(false)
const comments = ref<TaskCommentDTO[]>([])
const newComment = ref('')
const commentType = ref<'COMMENT' | 'QUESTION'>('COMMENT')
const replyingTo = ref<string | null>(null)
const replyContent = ref('')
const submitting = ref(false)

// 按层级组织评论
const threadedComments = computed(() => {
  const root = comments.value.filter((c) => !c.parentId)
  const replies = comments.value.filter((c) => c.parentId)
  return root.map((r) => ({
    ...r,
    children: replies.filter((c) => c.parentId === r.id),
  }))
})

async function loadComments() {
  if (!props.instanceId) return
  loading.value = true
  try {
    const res = await listInstanceComments(props.instanceId)
    // 拦截器已解包 AxiosResponse，运行时 res 即 ApiResponse<TaskCommentDTO[]>
    comments.value = (res as unknown as ApiResponse<TaskCommentDTO[]>).data ?? []
  } catch {
    ElMessage.error('加载评论失败')
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!newComment.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    await addTaskComment({
      instanceId: props.instanceId,
      taskId: props.taskId,
      nodeCode: props.nodeCode,
      content: newComment.value.trim(),
      type: commentType.value,
    })
    ElMessage.success('评论已发布')
    newComment.value = ''
    await loadComments()
  } catch {
    ElMessage.error('评论发布失败')
  } finally {
    submitting.value = false
  }
}

function startReply(commentId: string) {
  replyingTo.value = commentId
  replyContent.value = ''
}

function cancelReply() {
  replyingTo.value = null
  replyContent.value = ''
}

async function submitReply(parentId: string) {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  submitting.value = true
  try {
    await addTaskComment({
      instanceId: props.instanceId,
      taskId: props.taskId,
      nodeCode: props.nodeCode,
      content: replyContent.value.trim(),
      type: 'REPLY',
      parentId,
    })
    ElMessage.success('回复已发布')
    cancelReply()
    await loadComments()
  } catch {
    ElMessage.error('回复发布失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(commentId: string) {
  try {
    await deleteTaskComment(commentId)
    ElMessage.success('评论已删除')
    await loadComments()
  } catch {
    ElMessage.error('删除失败')
  }
}

function formatTime(time?: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

function typeTag(type: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' | undefined {
  switch (type) {
    case 'QUESTION':
      return 'warning'
    case 'REPLY':
      return 'info'
    default:
      return undefined
  }
}

function typeLabel(type: string): string {
  switch (type) {
    case 'QUESTION':
      return '提问'
    case 'REPLY':
      return '回复'
    default:
      return '评论'
  }
}

onMounted(() => {
  loadComments()
})
</script>

<template>
  <div class="task-comment-thread">
    <!-- 评论输入区 -->
    <div class="comment-input-area">
      <el-radio-group v-model="commentType" size="small" style="margin-bottom: 8px">
        <el-radio-button value="COMMENT">评论</el-radio-button>
        <el-radio-button value="QUESTION">提问</el-radio-button>
      </el-radio-group>
      <div class="input-row">
        <el-input
          v-model="newComment"
          type="textarea"
          :rows="2"
          placeholder="输入评论或提问..."
          maxlength="500"
          show-word-limit
        />
        <el-button
          type="primary"
          :icon="Position"
          :loading="submitting"
          @click="submitComment"
        >
          发布
        </el-button>
      </div>
    </div>

    <!-- 评论列表 -->
    <div v-loading="loading" class="comment-list">
      <div v-if="threadedComments.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无评论，快来发表第一条吧" :image-size="60" />
      </div>

      <div v-for="comment in threadedComments" :key="comment.id" class="comment-item">
        <!-- 主评论 -->
        <div class="comment-main">
          <div class="comment-header">
            <span class="comment-user">{{ comment.userName || `用户${comment.userId}` }}</span>
            <el-tag :type="typeTag(comment.type)" size="small">
              {{ typeLabel(comment.type) }}
            </el-tag>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            <span v-if="comment.nodeCode" class="comment-node">{{ comment.nodeCode }}</span>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-actions">
            <el-button text size="small" @click="startReply(comment.id)">回复</el-button>
            <el-button
              v-if="comment.userId === currentUserId"
              text
              size="small"
              type="danger"
              :icon="Delete"
              @click="handleDelete(comment.id)"
            >
              删除
            </el-button>
          </div>

          <!-- 回复输入框 -->
          <div v-if="replyingTo === comment.id" class="reply-input">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="2"
              :placeholder="`回复 ${comment.userName || ('用户' + comment.userId)}...`"
              maxlength="500"
            />
            <div class="reply-actions">
              <el-button size="small" @click="cancelReply">取消</el-button>
              <el-button type="primary" size="small" :loading="submitting" @click="submitReply(comment.id)">
                回复
              </el-button>
            </div>
          </div>
        </div>

        <!-- 楼中楼回复 -->
        <div v-if="comment.children && comment.children.length > 0" class="comment-replies">
          <div v-for="reply in comment.children" :key="reply.id" class="reply-item">
            <div class="comment-header">
              <span class="comment-user">{{ reply.userName || `用户${reply.userId}` }}</span>
              <el-tag type="info" size="small">回复</el-tag>
              <span class="comment-time">{{ formatTime(reply.createdAt) }}</span>
            </div>
            <div class="comment-content">{{ reply.content }}</div>
            <div class="comment-actions">
              <el-button
                v-if="reply.userId === currentUserId"
                text
                size="small"
                type="danger"
                :icon="Delete"
                @click="handleDelete(reply.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.task-comment-thread {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .comment-input-area {
    .input-row {
      display: flex;
      gap: 8px;
      align-items: flex-start;

      .el-button {
        flex-shrink: 0;
      }
    }
  }

  .comment-list {
    min-height: 100px;
  }

  .empty-state {
    padding: 20px 0;
  }

  .comment-item {
    border-bottom: 1px solid #f0f0f0;
    padding: 12px 0;

    &:last-child {
      border-bottom: none;
    }
  }

  .comment-main {
    .comment-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      .comment-user {
        font-weight: 600;
        font-size: 13px;
        color: #1e293b;
      }

      .comment-time {
        font-size: 12px;
        color: #94a3b8;
        margin-left: auto;
      }

      .comment-node {
        font-size: 11px;
        color: #64748b;
        background: #f1f5f9;
        padding: 1px 6px;
        border-radius: 3px;
      }
    }

    .comment-content {
      font-size: 14px;
      line-height: 1.6;
      color: #334155;
      margin-bottom: 6px;
      word-break: break-word;
    }

    .comment-actions {
      display: flex;
      gap: 4px;
    }
  }

  .reply-input {
    margin-top: 8px;
    padding: 8px;
    background: #f8fafc;
    border-radius: 4px;

    .reply-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 6px;
    }
  }

  .comment-replies {
    margin-left: 24px;
    padding-left: 12px;
    border-left: 2px solid #e2e8f0;
    margin-top: 8px;

    .reply-item {
      padding: 8px 0;

      .comment-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;

        .comment-user {
          font-weight: 600;
          font-size: 12px;
          color: #475569;
        }

        .comment-time {
          font-size: 11px;
          color: #94a3b8;
          margin-left: auto;
        }
      }

      .comment-content {
        font-size: 13px;
        line-height: 1.5;
        color: #475569;
      }

      .comment-actions {
        display: flex;
        gap: 4px;
      }
    }
  }
}
</style>
