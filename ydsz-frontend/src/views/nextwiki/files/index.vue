<!--
  @file 文件管理页面
  @description 双栏布局：左侧目录树，右侧文件列表。
               支持上传/下载/预览/分享/重命名/移动/删除/收藏/版本管理。
  @module views/nextwiki/files
-->
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  listFiles,
  createFolder,
  moveFile,
  renameFile,
  deleteFile,
  toggleStar,
  listFileVersions,
  rollbackVersion,
} from '@/api/nextwiki/file'
import { downloadFile } from '@/api/nextwiki/download'
import { getPreview } from '@/api/nextwiki/preview'
import type { FileNodeVO, FileVersionVO, FilePreviewVO } from '@/api/nextwiki/types'
import { PC } from '@/constants/permissionCodes'
import FileUploader from '@/components/common/FileUploader.vue'
import FilePreviewer from '@/components/common/FilePreviewer.vue'
import ShareLinkDialog from '@/components/common/ShareLinkDialog.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const { t } = useI18n()
const router = useRouter()

/** 文件列表加载状态 */
const loading = ref(false)
/** 文件列表数据 */
const list = ref<FileNodeVO[]>([])
/** 当前父目录 ID（根目录为 '0'） */
const currentParentId = ref('0')
/** 搜索关键字（回车后跳转搜索页） */
const keyword = ref('')

/** 目录树数据 */
const treeData = ref<FileNodeVO[]>([
  { id: '0', parentId: '', name: t('nextwiki.files.rootFolder'), nodeType: 'folder', size: 0, path: '/', starred: false, shareStatus: 'none', previewReady: false, createdAt: '', updatedAt: '', createdBy: '' },
])

// ===== 上传弹窗 =====
const uploaderVisible = ref(false)

// ===== 新建文件夹弹窗 =====
const folderDialogVisible = ref(false)
const folderForm = reactive({ name: '' })

// ===== 重命名弹窗 =====
const renameDialogVisible = ref(false)
const renameForm = reactive({ id: '', newName: '' })

// ===== 移动弹窗 =====
const moveDialogVisible = ref(false)
const moveForm = reactive({ id: '', targetParentId: '0' })

// ===== 预览弹窗 =====
const previewVisible = ref(false)
const previewData = ref<FilePreviewVO | null>(null)
const previewFileName = ref('')

// ===== 分享弹窗 =====
const shareVisible = ref(false)
const shareFileId = ref('')
const shareFileName = ref('')

// ===== 版本弹窗 =====
const versionDialogVisible = ref(false)
const versionList = ref<FileVersionVO[]>([])
const versionFileName = ref('')

/** 分享状态映射 */
const shareStatusMap = {
  none: { label: t('nextwiki.shares.status'), type: 'info' as const },
  shared: { label: t('nextwiki.shares.active'), type: 'success' as const },
}

/** 查询文件列表 */
async function fetchList() {
  loading.value = true
  try {
    const { data } = await listFiles({
      parentId: currentParentId.value,
      keyword: keyword.value || undefined,
      page: 1,
      size: 100,
    })
    list.value = data.list || []
  } finally {
    loading.value = false
  }
}

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`
}

/** 树节点点击 */
function handleTreeNodeClick(node: FileNodeVO) {
  if (node.nodeType === 'folder') {
    currentParentId.value = node.id
    fetchList()
  }
}

/** 打开上传弹窗 */
function openUploader() {
  uploaderVisible.value = true
}

/** 上传成功 */
function handleUploadSuccess() {
  fetchList()
}

/** 打开新建文件夹弹窗 */
function openFolderDialog() {
  folderForm.name = ''
  folderDialogVisible.value = true
}

/** 提交新建文件夹 */
async function submitFolder() {
  if (!folderForm.name.trim()) return
  await createFolder({ parentId: currentParentId.value, name: folderForm.name })
  ElMessage.success(t('nextwiki.files.createFolder'))
  folderDialogVisible.value = false
  fetchList()
}

/** 打开重命名弹窗 */
function openRenameDialog(row: FileNodeVO) {
  renameForm.id = row.id
  renameForm.newName = row.name
  renameDialogVisible.value = true
}

/** 提交重命名 */
async function submitRename() {
  if (!renameForm.newName.trim()) return
  await renameFile(renameForm.id, { newName: renameForm.newName })
  ElMessage.success(t('nextwiki.files.renameSuccess'))
  renameDialogVisible.value = false
  fetchList()
}

/** 打开移动弹窗 */
function openMoveDialog(row: FileNodeVO) {
  moveForm.id = row.id
  moveForm.targetParentId = '0'
  moveDialogVisible.value = true
}

/** 提交移动 */
async function submitMove() {
  await moveFile(moveForm.id, { targetParentId: moveForm.targetParentId })
  ElMessage.success(t('nextwiki.files.moveSuccess'))
  moveDialogVisible.value = false
  fetchList()
}

/** 删除文件 */
async function handleDelete(row: FileNodeVO) {
  try {
    await ElMessageBox.confirm(t('nextwiki.files.confirmDelete'), t('common.tip'), { type: 'warning' })
    await deleteFile(row.id)
    ElMessage.success(t('nextwiki.files.deleteSuccess'))
    fetchList()
  } catch { /* 取消 */ }
}

/** 收藏/取消收藏 */
async function handleToggleStar(row: FileNodeVO) {
  await toggleStar(row.id, { starred: !row.starred })
  ElMessage.success(row.starred ? t('nextwiki.files.unstarSuccess') : t('nextwiki.files.starSuccess'))
  fetchList()
}

/** 下载文件 */
async function handleDownload(row: FileNodeVO) {
  const { data } = await downloadFile(row.id)
  const url = window.URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = row.name
  a.click()
  window.URL.revokeObjectURL(url)
}

/** 预览文件 */
async function handlePreview(row: FileNodeVO) {
  previewFileName.value = row.name
  previewData.value = null
  previewVisible.value = true
  const { data } = await getPreview(row.id)
  previewData.value = data
}

/** 打开分享弹窗 */
function openShareDialog(row: FileNodeVO) {
  shareFileId.value = row.id
  shareFileName.value = row.name
  shareVisible.value = true
}

/** 打开版本弹窗 */
async function openVersionDialog(row: FileNodeVO) {
  versionFileName.value = row.name
  versionDialogVisible.value = true
  const { data } = await listFileVersions(row.id)
  versionList.value = data
}

/** 回滚版本 */
async function handleRollback(fileNodeId: string, version: number) {
  try {
    await ElMessageBox.confirm(t('nextwiki.preview.version') + ` v${version}`, t('common.tip'), { type: 'warning' })
    await rollbackVersion(fileNodeId, version)
    ElMessage.success(t('nextwiki.files.rollbackSuccess', { version }))
    versionDialogVisible.value = false
    fetchList()
  } catch { /* 取消 */ }
}

/** 跳转搜索 */
function goToSearch() {
  if (keyword.value) {
    router.push({ path: '/nextwiki/search', query: { keyword: keyword.value } })
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="nextwiki-files">
    <el-row :gutter="12">
      <!-- 左侧目录树 -->
      <el-col :span="5">
        <el-card shadow="never" class="tree-card">
          <template #header>{{ $t('nextwiki.files.folder') }}</template>
          <div class="tree-wrapper">
            <el-tree
              :data="treeData"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              :default-expanded-keys="['0']"
              :highlight-current="true"
              @node-click="handleTreeNodeClick"
            >
              <template #default="{ data: node }">
                <span class="tree-node">
                  <el-icon><Folder /></el-icon>
                  <span>{{ node.name }}</span>
                </span>
              </template>
            </el-tree>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧文件列表 -->
      <el-col :span="19">
        <el-card shadow="never">
          <!-- 工具栏 -->
          <div class="files-toolbar">
            <div class="files-toolbar__left">
              <el-input
                v-model="keyword"
                :placeholder="$t('nextwiki.search.placeholder')"
                clearable
                style="width: 240px"
                @keyup.enter="goToSearch"
              />
              <el-button @click="fetchList">{{ $t('common.refresh') }}</el-button>
            </div>
            <div class="files-toolbar__right">
              <el-button v-permission="[PC.NEXTWIKI_FILE_UPLOAD]" type="primary" :icon="'Upload'" @click="openUploader">
                {{ $t('nextwiki.files.upload') }}
              </el-button>
              <el-button v-permission="[PC.NEXTWIKI_FILE_CREATE]" type="success" :icon="'FolderAdd'" @click="openFolderDialog">
                {{ $t('nextwiki.files.createFolder') }}
              </el-button>
            </div>
          </div>

          <!-- 文件列表 -->
          <vxe-table :data="list" :loading="loading" border stripe height="520">
            <vxe-column type="seq" title="#" width="50" />
            <vxe-column field="name" :title="$t('nextwiki.files.name')" min-width="200" show-overflow>
              <template #default="{ row }">
                <el-icon class="file-icon" :class="{ 'file-icon--folder': row.nodeType === 'folder' }">
                  <Folder v-if="row.nodeType === 'folder'" />
                  <Document v-else />
                </el-icon>
                <span>{{ row.name }}</span>
              </template>
            </vxe-column>
            <vxe-column field="size" :title="$t('nextwiki.files.size')" width="100">
              <template #default="{ row }">{{ row.nodeType === 'folder' ? '-' : formatSize(row.size) }}</template>
            </vxe-column>
            <vxe-column field="starred" :title="$t('nextwiki.files.star')" width="60" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.starred" color="#F7BA2A"><Star /></el-icon>
              </template>
            </vxe-column>
            <vxe-column field="shareStatus" :title="$t('nextwiki.shares.status')" width="80">
              <template #default="{ row }">
                <StatusTag v-if="row.shareStatus !== 'none'" :value="row.shareStatus" :map="shareStatusMap" />
                <span v-else>-</span>
              </template>
            </vxe-column>
            <vxe-column field="updatedAt" :title="$t('nextwiki.files.modified')" width="160" />
            <vxe-column :title="$t('common.more')" width="320" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.nodeType === 'file'" v-permission="[PC.NEXTWIKI_PREVIEW_VIEW]" link type="primary" size="small" @click="handlePreview(row)">{{ $t('nextwiki.files.preview') }}</el-button>
                <el-button v-if="row.nodeType === 'file'" v-permission="[PC.NEXTWIKI_DOWNLOAD]" link type="primary" size="small" @click="handleDownload(row)">{{ $t('nextwiki.files.download') }}</el-button>
                <el-button v-permission="[PC.NEXTWIKI_SHARE_CREATE]" link type="success" size="small" @click="openShareDialog(row)">{{ $t('nextwiki.files.share') }}</el-button>
                <el-button v-permission="[PC.NEXTWIKI_FILE_STAR]" link size="small" @click="handleToggleStar(row)">
                  {{ row.starred ? $t('nextwiki.files.unstar') : $t('nextwiki.files.star') }}
                </el-button>
                <el-dropdown trigger="click" @command="(cmd: string) => {
                  if (cmd === 'rename') openRenameDialog(row)
                  else if (cmd === 'move') openMoveDialog(row)
                  else if (cmd === 'versions') openVersionDialog(row)
                  else if (cmd === 'delete') handleDelete(row)
                }">
                  <el-button link size="small">{{ $t('common.more') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">{{ $t('nextwiki.files.rename') }}</el-dropdown-item>
                      <el-dropdown-item command="move">{{ $t('nextwiki.files.move') }}</el-dropdown-item>
                      <el-dropdown-item v-if="row.nodeType === 'file'" command="versions">{{ $t('nextwiki.files.versions') }}</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>{{ $t('nextwiki.files.delete') }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </vxe-column>
          </vxe-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上传弹窗 -->
    <FileUploader v-model="uploaderVisible" :parent-id="currentParentId" @success="handleUploadSuccess" />

    <!-- 新建文件夹弹窗 -->
    <el-dialog v-model="folderDialogVisible" :title="$t('nextwiki.files.createFolder')" width="400px">
      <el-form label-width="80px">
        <el-form-item :label="$t('nextwiki.files.folderName')">
          <el-input v-model="folderForm.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitFolder">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重命名弹窗 -->
    <el-dialog v-model="renameDialogVisible" :title="$t('nextwiki.files.rename')" width="400px">
      <el-form label-width="80px">
        <el-form-item :label="$t('nextwiki.files.newName')">
          <el-input v-model="renameForm.newName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitRename">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 移动弹窗 -->
    <el-dialog v-model="moveDialogVisible" :title="$t('nextwiki.files.move')" width="400px">
      <el-form label-width="100px">
        <el-form-item :label="$t('nextwiki.files.targetFolder')">
          <el-input v-model="moveForm.targetParentId" :placeholder="$t('nextwiki.files.targetFolder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitMove">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <FilePreviewer v-model="previewVisible" :preview="previewData" :file-name="previewFileName" @download="() => {
      const file = list.find(f => f.name === previewFileName)
      if (file) handleDownload(file)
    }" />

    <!-- 分享弹窗 -->
    <ShareLinkDialog v-model="shareVisible" :file-node-id="shareFileId" :file-name="shareFileName" />

    <!-- 版本弹窗 -->
    <el-dialog v-model="versionDialogVisible" :title="`${$t('nextwiki.files.versions')} - ${versionFileName}`" width="600px">
      <vxe-table :data="versionList" border>
        <vxe-column field="versionNumber" :title="$t('nextwiki.preview.version')" width="80">
          <template #default="{ row }">v{{ row.versionNumber }}</template>
        </vxe-column>
        <vxe-column field="size" :title="$t('nextwiki.files.size')" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </vxe-column>
        <vxe-column field="remark" :title="$t('common.more')" min-width="120" />
        <vxe-column field="createdAt" :title="$t('nextwiki.files.modified')" width="160" />
        <vxe-column field="isActive" :title="$t('nextwiki.preview.currentVersion')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isActive" type="success" size="small">{{ $t('common.yes') }}</el-tag>
          </template>
        </vxe-column>
        <vxe-column :title="$t('common.more')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isActive" v-permission="[PC.NEXTWIKI_FILE_ROLLBACK]" link type="warning" size="small" @click="handleRollback(versionList[0]?.fileNodeId || '', row.versionNumber)">
              {{ $t('nextwiki.files.rollback') }}
            </el-button>
          </template>
        </vxe-column>
      </vxe-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.nextwiki-files {
  height: 100%;
}
.tree-card {
  height: 100%;
}
.tree-wrapper {
  max-height: 520px;
  overflow: auto;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
}
.files-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.files-toolbar__left,
.files-toolbar__right {
  display: flex;
  gap: 8px;
}
.file-icon {
  margin-right: 4px;
}
.file-icon--folder {
  color: #E6A23C;
}
</style>
