#!/usr/bin/env python3
"""Writes the host symbol list the module fingerprints with DexKit.

Every launcher/SystemUI class the sources name, and every identifier-like string
literal they use (the member names looked up by reflection). The recorder
(core/HostDex.java) fingerprints each listed class, its launcher superclasses and
their members whose name is listed, so a later build that renames or moves one
can be matched back by DexKit.

usage: python scripts/host-symbols.py   (rewrites app/src/main/resources/ple/host.syms)
"""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'app/src/main/kotlin/my/github/MrxSiN/pixellauncherevolved'
OUT = ROOT / 'app/src/main/resources/ple/host.syms'

LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
HOST = re.compile(r'(?:com\.android|com\.google|androidx)\.[A-Za-z0-9_.$]+')
IDENT = re.compile(r'[A-Za-z_][A-Za-z0-9_]{1,63}')

classes, names = set(), set()
# Wallpaper & style (picker/) is not a host DexKit fingerprints; its names stay out.
for path in sorted(p for p in list(SRC.rglob('*.kt')) + list(SRC.rglob('*.java')) if 'picker' not in p.relative_to(SRC).parts):
    for raw in LITERAL.findall(path.read_text(encoding='utf-8')):
        s = raw.replace('\\$', '$')
        if HOST.fullmatch(s):
            classes.add(s)
        elif IDENT.fullmatch(s):
            names.add(s)

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(''.join(f'S\t{c}\n' for c in sorted(classes)) + ''.join(f'N\t{n}\n' for n in sorted(names)),
               encoding='utf-8', newline='\n')
print(f'{len(classes)} classes, {len(names)} names -> {OUT.relative_to(ROOT)}')
