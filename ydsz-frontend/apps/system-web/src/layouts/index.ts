const BasicLayout = () => import('./basic.vue');

const IFrameView = () => import('@ydsz/layouts').then((m) => m.IFrameView);

export { BasicLayout, IFrameView };
