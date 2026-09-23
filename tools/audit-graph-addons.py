"""Inventory installed jars and report references to the crafting takeover boundary.

This is an audit index, not a compatibility test. It never loads mod code and
ignores disabled files. Run against the actual pack and the test server separately.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

TARGETS = (
    'appeng/crafting/', 'appeng/menu/me/crafting/',
    'appeng/api/networking/crafting/', 'appeng/helpers/patternprovider/',
    'appeng/me/service/CraftingService', 'appeng/me/cluster/implementations/CraftingCPUCluster',
)


def utf8_constants(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    size = struct.unpack_from('>H', data, 8)[0]
    at, index, result = 10, 1, []
    while index < size:
        tag = data[at]
        at += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, at)[0]
            at += 2
            result.append(data[at:at + length].decode('utf-8', 'replace'))
            at += length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            at += 4
        elif tag in (5, 6):
            at += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            at += 2
        elif tag == 15:
            at += 3
        else:
            raise ValueError(f'unknown constant tag {tag}')
        index += 1
    return result


def audit(path):
    result = {'jar': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
              'configs': [], 'references': []}
    with zipfile.ZipFile(path) as jar:
        mixins = set()
        for name in jar.namelist():
            if not name.endswith('.json') or 'mixin' not in name.lower() or 'refmap' in name.lower():
                continue
            try:
                config = json.loads(jar.read(name))
            except (ValueError, UnicodeError):
                continue
            if not isinstance(config, dict) or 'package' not in config:
                continue
            result['configs'].append({'name': name, 'plugin': config.get('plugin'), 'required': config.get('required'),
                                      'mixins': config.get('mixins', []), 'client': config.get('client', [])})
            for item in config.get('mixins', []) + config.get('client', []) + config.get('server', []):
                mixins.add(config['package'].replace('.', '/') + '/' + item.replace('.', '/') + '.class')
        # AE itself and the development Core are the reference implementations.
        if path.name.lower().startswith(('appliedenergistics2-', 'gtlcore-')):
            return result
        for name in jar.namelist():
            if not name.endswith('.class'):
                continue
            data = jar.read(name)
            if not any(target.encode() in data or target.replace('/', '.').encode() in data for target in TARGETS):
                continue
            strings = utf8_constants(data)
            refs = sorted({text for text in strings if any(t in text or t.replace('/', '.') in text for t in TARGETS)})
            hooks = [text for text in strings if any(word in text for word in (
                'Inject;', 'Redirect;', 'WrapMethod;', 'WrapOperation;', 'Accessor;', 'Modify', 'Overwrite;',
                'beginCrafting', 'submitJob', 'insertCrafted', 'jobStateChange', 'CraftingPlan', 'CraftingCpu'))]
            result['references'].append({'class': name, 'configured_mixin': name in mixins, 'references': refs,
                                         'hooks_and_names': hooks})
        result['nested_jars'] = [name for name in jar.namelist() if name.endswith('.jar')]
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mods', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    report = [audit(path) for path in sorted(args.mods.glob('*.jar'))]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    for jar in report:
        if jar['references']:
            print(jar['jar'], len(jar['references']), 'boundary references;',
                  sum(row['configured_mixin'] for row in jar['references']), 'configured mixins')
    print(f'{len(report)} active jars indexed; report: {args.output}')
