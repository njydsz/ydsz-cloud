import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: { icon: 'lucide:folder', order: 1, title: '文件管理' },
    name: 'FileMgmt',
    path: '/file',
    children: [
      {
        name: 'FileManagement',
        path: 'list',
        component: () => import('#/views/file/index.vue'),
        meta: { icon: 'lucide:hard-drive', title: '文件列表' },
      },
      {
        name: 'CommentManagement',
        path: 'comment',
        component: () => import('#/views/comment/index.vue'),
        meta: { icon: 'lucide:message-circle', title: '文件评论' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:share-2', order: 2, title: '分享管理' },
    name: 'ShareMgmt',
    path: '/share',
    children: [
      {
        name: 'ShareManagement',
        path: 'list',
        component: () => import('#/views/share/index.vue'),
        meta: { icon: 'lucide:share-2', title: '分享列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:tag', order: 3, title: '标签管理' },
    name: 'TagMgmt',
    path: '/tag',
    children: [
      {
        name: 'TagManagement',
        path: 'list',
        component: () => import('#/views/tag/index.vue'),
        meta: { icon: 'lucide:tag', title: '标签列表' },
      },
    ],
  },
  {
    meta: { icon: 'lucide:database', order: 4, title: '存储管理' },
    name: 'StorageMgmt',
    path: '/storage',
    children: [
      {
        name: 'QuotaManagement',
        path: 'quota',
        component: () => import('#/views/quota/index.vue'),
        meta: { icon: 'lucide:hard-drive', title: '存储配额' },
      },
    ],
  },
];

export default routes;
