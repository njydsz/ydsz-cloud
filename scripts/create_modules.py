#!/usr/bin/env python3
"""Batch create DDD 5-layer module skeleton for ydsz-userinfo and ydsz-system."""
import os
import sys

BASE = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

MODULES = {
    'ydsz-userinfo': 'userinfo',
    'ydsz-system': 'system',
}

LAYERS = ['api', 'domain', 'infra', 'server', 'web']


def main():
    for mod, short in MODULES.items():
        for layer in LAYERS:
            # Java package path
            pkg_path = os.path.join(
                BASE, mod, f'{mod}-{layer}',
                'src', 'main', 'java', 'com', 'njydsz', short, layer
            )
            os.makedirs(pkg_path, exist_ok=True)
            print(f'Created: {pkg_path}')

            # Resources path
            res_path = os.path.join(
                BASE, mod, f'{mod}-{layer}',
                'src', 'main', 'resources'
            )
            os.makedirs(res_path, exist_ok=True)
            print(f'Created: {res_path}')

    print('Done: DDD skeleton directories created.')


if __name__ == '__main__':
    main()
