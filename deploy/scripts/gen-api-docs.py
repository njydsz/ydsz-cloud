#!/usr/bin/env python3
"""
YDSZ API 文档自动生成脚本

P2-1: 从后端 OpenAPI 规范生成静态 API 文档站点

用法:
  python deploy/scripts/gen-api-docs.py --module userinfo --port 9001
  python deploy/scripts/gen-api-docs.py --all

@author ydsz-team
@since 1.0.0
"""
import argparse
import json
import subprocess
import sys
import pathlib
import urllib.request

SERVICES = {
    'gateway':  9000,
    'userinfo': 9001,
    'system':   9002,
    'project':  9003,
    'message':  9004,
    'cronjob':  9005,
    'workflow': 9006,
    'agent':    9007,
    'nextwiki': 8800,
}

OUTPUT_DIR = pathlib.Path('deploy/docs/api')


def fetch_openapi_spec(base_url: str, service: str) -> dict | None:
    """从运行中的服务获取 OpenAPI JSON"""
    url = f'{base_url}/v3/api-docs'
    print(f'  获取 OpenAPI 规范: {url}')
    try:
        req = urllib.request.Request(url, headers={'Accept': 'application/json'})
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f'  ⚠️  无法获取 {service} 的 OpenAPI 规范: {e}')
        return None


def generate_docs(service: str, port: int, base_url: str):
    """为单个服务生成 API 文档"""
    print(f'\n>>> 生成 {service} API 文档 (port {port})')

    spec = fetch_openapi_spec(base_url, service)
    if not spec:
        return False

    # 保存 OpenAPI JSON
    output_path = OUTPUT_DIR / f'{service}-openapi.json'
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(spec, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f'  ✅ OpenAPI JSON: {output_path}')

    # 生成 Markdown 格式的 API 文档
    md_path = OUTPUT_DIR / f'{service}-api.md'
    md_content = generate_markdown_doc(spec, service)
    md_path.write_text(md_content, encoding='utf-8')
    print(f'  ✅ Markdown 文档: {md_path}')

    return True


def generate_markdown_doc(spec: dict, service: str) -> str:
    """从 OpenAPI 规范生成 Markdown 文档"""
    lines = [
        f'# {service.upper()} API 文档',
        '',
        f'**版本**: {spec.get("info", {}).get("version", "1.0.0")}',
        f'**标题**: {spec.get("info", {}).get("title", service)}',
        '',
        '---',
        '',
    ]

    paths = spec.get('paths', {})
    for path, methods in sorted(paths.items()):
        lines.append(f'## `{path}`')
        lines.append('')
        for method, details in methods.items():
            method_upper = method.upper()
            summary = details.get('summary', '')
            description = details.get('description', '')
            tags = details.get('tags', [])

            lines.append(f'### {method_upper} - {summary}')
            lines.append('')
            if description:
                lines.append(f'{description}')
                lines.append('')
            if tags:
                lines.append(f'**标签**: {", ".join(tags)}')
                lines.append('')

            # 请求参数
            parameters = details.get('parameters', [])
            if parameters:
                lines.append('**请求参数**:')
                lines.append('')
                lines.append('| 参数名 | 位置 | 类型 | 必填 | 描述 |')
                lines.append('|--------|------|------|------|------|')
                for param in parameters:
                    name = param.get('name', '')
                    location = param.get('in', '')
                    required = '✅' if param.get('required', False) else ''
                    schema = param.get('schema', {}).get('type', '')
                    desc = param.get('description', '')
                    lines.append(f'| {name} | {location} | {schema} | {required} | {desc} |')
                lines.append('')

            # 请求体
            request_body = details.get('requestBody')
            if request_body:
                lines.append('**请求体**:')
                lines.append('```json')
                content = request_body.get('content', {}).get('application/json', {})
                schema_ref = content.get('schema', {}).get('$ref', '')
                if schema_ref:
                    lines.append(f'// Schema: {schema_ref.split("/")[-1]}')
                lines.append('```')
                lines.append('')

            # 响应
            responses = details.get('responses', {})
            if responses:
                lines.append('**响应**:')
                lines.append('')
                for code, resp in responses.items():
                    desc = resp.get('description', '')
                    lines.append(f'- `{code}`: {desc}')
                lines.append('')

            lines.append('---')
            lines.append('')

    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description='YDSZ API 文档生成器')
    parser.add_argument('--module', help='指定模块名（如 userinfo）')
    parser.add_argument('--all', action='store_true', help='生成所有模块的文档')
    parser.add_argument('--base-url', default='http://localhost',
                        help='服务基础 URL（默认: http://localhost）')
    args = parser.parse_args()

    if args.all:
        success = 0
        for service, port in SERVICES.items():
            base_url = f'{args.base_url}:{port}'
            if generate_docs(service, port, base_url):
                success += 1
        print(f'\n=== 完成: {success}/{len(SERVICES)} 个服务文档生成成功 ===')
    elif args.module:
        port = SERVICES.get(args.module)
        if not port:
            print(f'❌ 未知模块: {args.module}')
            print(f'   可用模块: {", ".join(SERVICES.keys())}')
            sys.exit(1)
        base_url = f'{args.base_url}:{port}'
        generate_docs(args.module, port, base_url)
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
