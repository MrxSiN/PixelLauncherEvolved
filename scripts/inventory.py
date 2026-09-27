#!/usr/bin/env python3
"""Writes a machine-readable inventory of the module to stdout as JSON.

Static scan of the sources and manifest: per source file, the host classes it
names, the host members it resolves by name, the hooks it installs, the
settings keys, threads/executors and root/Binder calls it makes. Paired with the
runtime "Installed <id>" / "Hooked <Class>.<method>" lines the module logs, this
is the hook graph the optimisation work is checked against.

usage: python scripts/inventory.py > docs/inventory.json
"""
import json, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'app/src/main/kotlin/my/github/MrxSiN/pixellauncherevolved'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'
META = ROOT / 'app/src/main/resources/META-INF/xposed'

HOST_CLASS = re.compile(r'"((?:com\.android|com\.google|android\.app\.search)\.[A-Za-z0-9_.$\\]+)"')
MEMBER = re.compile(r'(?:Reflect\.(?:method|field)|getDeclaredMethod|getDeclaredField|getMethod|hookAfter)\([^,()]+,\s*"([A-Za-z_$][\w$]*)"')
CONST = re.compile(r'const val ([A-Z_]+)\s*=\s*"([^"]+)"')
SETTING = re.compile(r'val ([A-Z_]+)\s*=\s*(Bool|Int)Setting\(\s*"([^"]+)",\s*default\s*=\s*([^,)\s]+)', re.S)
TOGGLE = re.compile(r'ToggleFeature\(\s*Settings\.([A-Z_]+)\s*\)')

inventory = {'module': {}, 'components': [], 'settings': [], 'features': [], 'files': {}}

for name in ('module.prop', 'scope.list', 'java_init.list'):
    inventory['module'][name] = (META / name).read_text(encoding='utf-8').split()

for m in re.finditer(r'<(provider|service|receiver|activity)\b(.*?)/?>', MANIFEST.read_text(encoding='utf-8'), re.S):
    attrs = dict(re.findall(r'android:(\w+)="([^"]*)"', m.group(2)))
    inventory['components'].append({'kind': m.group(1), **attrs})

for m in SETTING.finditer((SRC / 'catalog/Settings.kt').read_text(encoding='utf-8')):
    inventory['settings'].append({'name': m.group(1), 'type': m.group(2), 'key': m.group(3), 'default': m.group(4)})

for path in sorted(list(SRC.rglob('*.kt')) + list(SRC.rglob('*.java'))):
    text = path.read_text(encoding='utf-8')
    rel = str(path.relative_to(SRC)).replace('\\', '/')
    consts = dict(CONST.findall(text))
    feature_id = re.search(r'override val id: String = "([^"]+)"', text)
    toggle = TOGGLE.search(text)
    if not feature_id and toggle:
        key = next((x['key'] for x in inventory['settings'] if x['name'] == toggle.group(1)), toggle.group(1))
        feature_id = re.match(r'(.*)', key)
    if feature_id:
        compat = re.search(r'override val compatibility = CompatibilityFeature\.(\w+)', text)
        inventory['features'].append({'id': feature_id.group(1), 'file': rel,
                                      'compatibility': compat.group(1) if compat else None})
    members = set(MEMBER.findall(text))
    members |= {v for k, v in consts.items() if re.fullmatch(r'(on|set|get|update|apply|dispatch|calculate|show|is|can)[A-Z]\w*', v)}
    entry = {
        'host_classes': sorted(set(HOST_CLASS.findall(text)) | {v for v in consts.values() if HOST_CLASS.fullmatch(f'"{v}"')}),
        'host_members': sorted(members),
        'hooks': len(re.findall(r'xposed\.hook\(|\.hookAfter\(', text)),
        'threads': len(re.findall(r'\bThread\(|Executors\.|ThreadPoolExecutor\(', text)),
        'root': bool(re.search(r'"su"', text)),
        'binder': sorted(set(re.findall(r'\b(contentResolver\.call|resolver\.call|getSystemService|goToSleep)\b', text))),
        'preferences': bool(re.search(r'SharedPreferences|getRemotePreferences', text)),
    }
    if any(entry[k] for k in entry):
        inventory['files'][rel] = entry

print(json.dumps(inventory, indent=1, sort_keys=False))
