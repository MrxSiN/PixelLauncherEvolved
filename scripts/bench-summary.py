#!/usr/bin/env python3
"""Summarises bench-device.sh results: per workload and metric, raw samples,
median, IQR, MAD and the candidate's change against the baseline median.

usage: bench-summary.py results.csv [more.csv ...]
"""
import csv, statistics, sys

METRICS = ['alloc_mb', 'gc_count', 'gc_ms', 'main_cpu_ns', 'janky', 'p50_ms', 'p90_ms', 'p95_ms', 'p99_ms']

rows = [r for path in sys.argv[1:] for r in csv.DictReader(open(path))]


def quartiles(xs):
    if len(xs) < 2:
        return xs[0], xs[0]
    q = statistics.quantiles(xs, n=4, method='inclusive')
    return q[0], q[2]


def mad(xs):
    m = statistics.median(xs)
    return statistics.median(abs(x - m) for x in xs)


for workload in sorted({r['workload'] for r in rows}):
    print(f'## {workload}')
    print('| metric | variant | n | samples | median | IQR | MAD | change |')
    print('|---|---|---|---|---|---|---|---|')
    for metric in METRICS:
        if workload == 'drawer' and metric.endswith('_ms') and metric.startswith('p'):
            continue  # the drawer is rebuilt off screen; no launcher frames to rank
        medians = {}
        for variant in ('baseline', 'candidate'):
            xs = [float(r[metric]) for r in rows if r['workload'] == workload and r['variant'] == variant]
            if not xs:
                continue
            m = statistics.median(xs)
            medians[variant] = m
            lo, hi = quartiles(xs)
            change = ''
            if variant == 'candidate' and medians.get('baseline'):
                change = f'{(m - medians["baseline"]) / medians["baseline"] * 100:+.1f}%'
            samples = ' '.join(f'{x:g}' for x in xs)
            print(f'| {metric} | {variant} | {len(xs)} | {samples} | {m:g} | {lo:g}..{hi:g} | {mad(xs):g} | {change} |')
    print()
