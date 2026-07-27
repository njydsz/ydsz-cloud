#!/usr/bin/env python3
"""Rename 9 sub-apps to match backend module names."""
import os
import shutil

DST = r'd:\Code\ydsz\ydsz-pmis\ydsz-frontend'

# Old dir -> New dir
RENAME_MAP = {
    'user-center-web': 'userinfo-web',
    'system-admin-web': 'system-web',
    'project-mgmt-web': 'project-web',
    'message-center-web': 'message-web',
    'cronjob-admin-web': 'cronjob-web',
    'workflow-designer-web': 'workflow-web',
    'wiki-drive-web': 'nextwiki-web',
    'rule-engine-web': 'literule-web',
    'ai-assistant-web': 'agent-web',
}

# App config: new_dir -> (package_name, qiankun_name, port, active_rule, title, namespace)
APP_CONFIG = {
    'userinfo-web': ('@ydsz/userinfo-web', 'userinfo-web', 5601, '/ydsz-user', '用户中心', 'ydsz-userinfo'),
    'system-web': ('@ydsz/system-web', 'system-web', 5602, '/ydsz-sys', '系统管理', 'ydsz-system'),
    'project-web': ('@ydsz/project-web', 'project-web', 5603, '/ydsz-proj', '项目管理', 'ydsz-project'),
    'message-web': ('@ydsz/message-web', 'message-web', 5604, '/ydsz-msg', '消息中心', 'ydsz-message'),
    'cronjob-web': ('@ydsz/cronjob-web', 'cronjob-web', 5605, '/ydsz-cron', '定时任务', 'ydsz-cronjob'),
    'workflow-web': ('@ydsz/workflow-web', 'workflow-web', 5606, '/ydsz-flow', '工作流引擎', 'ydsz-workflow'),
    'nextwiki-web': ('@ydsz/nextwiki-web', 'nextwiki-web', 5607, '/ydsz-wiki', '网盘知识库', 'ydsz-nextwiki'),
    'literule-web': ('@ydsz/literule-web', 'literule-web', 5608, '/ydsz-rule', '规则引擎', 'ydsz-literule'),
    'agent-web': ('@ydsz/agent-web', 'agent-web', 5610, '/ydsz-ai', 'AI 助手', 'ydsz-agent'),
}

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)

# Step 1: Rename directories
print('=== Step 1: Rename directories ===')
apps_dir = os.path.join(DST, 'apps')
for old_dir, new_dir in RENAME_MAP.items():
    old_path = os.path.join(apps_dir, old_dir)
    new_path = os.path.join(apps_dir, new_dir)
    if os.path.isdir(old_path):
        if os.path.exists(new_path):
            shutil.rmtree(new_path)
        os.rename(old_path, new_path)
        print(f'  {old_dir} -> {new_dir}')

# Step 2: Update each app's package.json, vite.config.mts, main.ts, preferences.ts, .env
print('\n=== Step 2: Update app files ===')
for new_dir, (pkg_name, qk_name, port, active_rule, title, namespace) in APP_CONFIG.items():
    app_dir = os.path.join(apps_dir, new_dir)

    # package.json
    write_file(os.path.join(app_dir, 'package.json'), f"""{{
  "name": "{pkg_name}",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {{
    "build": "pnpm vite build --mode production",
    "build:analyze": "pnpm vite build --mode analyze",
    "dev": "pnpm vite --mode development",
    "preview": "vite preview",
    "typecheck": "vue-tsc --noEmit --skipLibCheck"
  }},
  "imports": {{
    "#/*": "./src/*"
  }},
  "dependencies": {{
    "@ydsz/access": "workspace:*",
    "@ydsz/common-ui": "workspace:*",
    "@ydsz/constants": "workspace:*",
    "@ydsz/hooks": "workspace:*",
    "@ydsz/icons": "workspace:*",
    "@ydsz/layouts": "workspace:*",
    "@ydsz/locales": "workspace:*",
    "@ydsz/plugins": "workspace:*",
    "@ydsz/preferences": "workspace:*",
    "@ydsz/request": "workspace:*",
    "@ydsz/stores": "workspace:*",
    "@ydsz/styles": "workspace:*",
    "@ydsz/types": "workspace:*",
    "@ydsz/utils": "workspace:*",
    "@vueuse/core": "catalog:",
    "dayjs": "catalog:",
    "element-plus": "catalog:",
    "pinia": "catalog:",
    "vue": "catalog:",
    "vue-router": "catalog:"
  }},
  "devDependencies": {{
    "unplugin-element-plus": "catalog:",
    "vite-plugin-qiankun": "^1.0.15"
  }}
}}
""")

    # vite.config.mts
    write_file(os.path.join(app_dir, 'vite.config.mts'), f"""import {{ defineConfig }} from '@ydsz/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';
import qiankun from 'vite-plugin-qiankun';

export default defineConfig(async () => {{
  return {{
    application: {{}},
    vite: {{
      base: '/',
      plugins: [
        ElementPlus({{
          format: 'esm',
        }}),
        qiankun('{qk_name}', {{
          useDevMode: true,
        }}),
      ],
      server: {{
        port: {port},
        cors: true,
        host: '0.0.0.0',
        headers: {{
          'Access-Control-Allow-Origin': '*',
        }},
        proxy: {{
          '/api': {{
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\\/api/, ''),
            target: 'http://localhost:9000',
            ws: true,
          }},
        }},
      }},
    }},
  }};
}});
""")

    # main.ts
    write_file(os.path.join(app_dir, 'src', 'main.ts'), f"""import type {{ App as VueApp }} from 'vue';

import {{ createApp }} from 'vue';
import {{ createRouter, createWebHistory }} from 'vue-router';

import {{ registerAccessDirective }} from '@ydsz/access';
import {{ registerLoadingDirective }} from '@ydsz/common-ui';
import {{ initPreferences }} from '@ydsz/preferences';
import {{ initStores }} from '@ydsz/stores';
import '@ydsz/styles';
import '@ydsz/styles/ele';

import {{ ElLoading }} from 'element-plus';
import {{
  qiankunWindow,
  renderWithQiankun,
}} from 'vite-plugin-qiankun/dist/helper';

import {{ initComponentAdapter }} from './adapter/component';
import {{ initSetupYDSZForm }} from './adapter/form';
import RootApp from './app.vue';
import {{ setupI18n }} from './locales';
import {{ overridesPreferences }} from './preferences';
import {{ createRouterGuard, initRoutes }} from './router/guard';
import {{ routes }} from './router/routes';

const env = import.meta.env.PROD ? 'prod' : 'dev';
const appVersion = import.meta.env.VITE_APP_VERSION;
const namespace = `${{import.meta.env.VITE_APP_NAMESPACE}}-${{appVersion}}-${{env}}`;

let app: null | VueApp = null;

async function setupApp(vueApp: VueApp) {{
  await initComponentAdapter();
  await initSetupYDSZForm();

  vueApp.directive('loading', ElLoading.directive);

  registerLoadingDirective(vueApp, {{
    loading: false,
    spinning: 'spinning',
  }});

  await setupI18n(vueApp);
  await initStores(vueApp, {{ namespace }});
  registerAccessDirective(vueApp);

  const {{ initTippy }} = await import('@ydsz/common-ui/es/tippy');
  initTippy(vueApp);

  const {{ MotionPlugin }} = await import('@ydsz/plugins/motion');
  vueApp.use(MotionPlugin);
}}

function createAppRouter(basename?: string) {{
  return createRouter({{
    history: createWebHistory(basename || '{active_rule}'),
    routes,
    scrollBehavior: (to, _from, savedPosition) => {{
      if (savedPosition) return savedPosition;
      return to.hash
        ? {{ behavior: 'smooth', el: to.hash }}
        : {{ left: 0, top: 0 }};
    }},
  }});
}}

async function bootstrap() {{
  console.warn('[{qk_name}] bootstrap');
}}

async function mount(props: Record<string, unknown>) {{
  console.warn('[{qk_name}] mount', props);

  const {{ container }} = props;

  await initPreferences({{
    namespace,
    overrides: overridesPreferences,
  }});

  await import('./api/request');

  app = createApp(RootApp);

  const router = createAppRouter('{active_rule}');
  initRoutes(router);
  app.use(router);

  await setupApp(app);

  createRouterGuard(router);

  const mountNode =
    (container as HTMLElement)?.querySelector('#app') ||
    document.querySelector('#app');
  app.mount(mountNode);
}}

async function unmount() {{
  console.warn('[{qk_name}] unmount');
  app?.unmount();
  app = null;
}}

async function update(props: Record<string, unknown>) {{
  console.warn('[{qk_name}] update', props);
}}

renderWithQiankun({{
  bootstrap,
  mount,
  unmount,
  update,
}});

if (!qiankunWindow.__POWERED_BY_QIANKUN__) {{
  (async () => {{
    await initPreferences({{
      namespace,
      overrides: overridesPreferences,
    }});

    app = createApp(RootApp);

    await import('./api/request');

    const router = createAppRouter(import.meta.env.VITE_BASE);
    initRoutes(router);
    app.use(router);

    await setupApp(app);

    createRouterGuard(router);

    app.mount('#app');

    const {{ unmountGlobalLoading }} = await import('@ydsz/utils');
    unmountGlobalLoading();
  }})();
}}
""")

    # preferences.ts
    write_file(os.path.join(app_dir, 'src', 'preferences.ts'), f"""import {{ defineOverridesPreferences }} from '@ydsz/preferences';

export const overridesPreferences = defineOverridesPreferences({{
  app: {{
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '{active_rule}',
  }},
  sidebar: {{
    hidden: true,
  }},
  theme: {{
    builtinType: 'deep-blue',
    colorPrimary: 'hsl(211 98% 52%)',
    mode: 'light',
    radius: '0.5',
    semiDarkHeader: false,
    semiDarkSidebar: false,
  }},
}});
""")

    # .env
    write_file(os.path.join(app_dir, '.env'), f"""VITE_APP_TITLE={title}
VITE_APP_NAMESPACE={namespace}
VITE_APP_VERSION=1.0.0
VITE_APP_STORE_SECURE_KEY=ydsz-pmis-2026-secure-key
""")

    # .env.development
    write_file(os.path.join(app_dir, '.env.development'), f"""VITE_PORT={port}
VITE_BASE=/
VITE_GLOB_API_URL=/api
VITE_NITRO_MOCK=false
VITE_DEVTOOLS=false
VITE_INJECT_APP_LOADING=true
""")

    # .env.production
    write_file(os.path.join(app_dir, '.env.production'), f"""VITE_BASE=/{new_dir}/
VITE_GLOB_API_URL=/api
VITE_COMPRESS=gzip
VITE_PWA=false
VITE_ROUTER_HISTORY=history
VITE_INJECT_APP_LOADING=true
VITE_ARCHIVER=true
""")

    print(f'  Updated: {new_dir} ({pkg_name}, port {port})')

print('\n=== Done renaming sub-apps ===')
