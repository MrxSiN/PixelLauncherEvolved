#!/usr/bin/env python3
"""Lists allocation, boxing, reflection, logging and locking sites in chosen methods of a release DEX.

usage: dex-audit.py <dexdump -d output> <R8 mapping.txt> <original-method-substring>...

A method of the final DEX is reported when any original method R8 inlined into
it contains one of the substrings, e.g. 'TaskCardButtonDecorator.findButton' or
'HiddenAppsFeature.shown$lambda'. Each report names the original methods it holds.
"""
import re, sys

dump, mapping, wanted = sys.argv[1], sys.argv[2], sys.argv[3:]
obf_class, sources = {}, {}
current = None
for line in open(mapping, encoding='utf-8'):
    m = re.match(r'^(\S+) -> (\S+):$', line)
    if m:
        current = 'L' + m.group(2).replace('.', '/') + ';'
        obf_class[current] = m.group(1)
        continue
    m = re.match(r'^\s+(?:[\d:]+:)?\S+ (\S+)\(.*\)(?::[\d:]+)? -> (\S+)$', line)
    if m and current:
        name = m.group(1)
        if '.' not in name:
            name = obf_class[current] + '.' + name
        sources.setdefault((current, m.group(2)), set()).add(name)

PATTERNS = re.compile(r'new-instance|new-array|filled-new-array|\.valueOf:|Arrays;\.copyOf|'
                      r'Iterator|StringBuilder|getIdentifier|getDeclared|getMethod|Log;\.|monitor-enter|'
                      r'reflect/Method;\.invoke|reflect/Field;\.get')
cls = meth = key = None
hits = {}
for line in open(dump, encoding='utf-8', errors='replace'):
    m = re.match(r"\s*Class descriptor\s*:\s*'(\S+)'", line)
    if m:
        cls = m.group(1); continue
    m = re.match(r"\s*name\s*:\s*'(\S+)'", line)
    if m:
        meth = m.group(1)
        origin = sources.get((cls, meth), {obf_class.get(cls, cls) + '.' + meth})
        key = None
        if any(w in o for o in origin for w in wanted):
            key = f'{obf_class.get(cls, cls)}.{meth}  <=  ' + ', '.join(sorted(o.split('pixellauncherevolved.')[-1] for o in origin if any(w.split('.')[0] in o for w in wanted)))
            hits.setdefault(key, [])
        continue
    if key and '|' in line and PATTERNS.search(line):
        op = line.split('|', 1)[1].split(':', 1)[1].strip()
        hits[key].append(re.sub(r'\s*// (method|type|field|string)@\w+', '', op))
for k in sorted(hits):
    print(k)
    for op in hits[k] or ['(none)']:
        print('   ', op)
