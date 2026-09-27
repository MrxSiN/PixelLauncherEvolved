#!/usr/bin/env python3
"""Summarises PLEProbe batch lines: per statistic, median and IQR across batches, change vs the first variant.

usage: bench-probe-summary.py <results.txt> <variant-a> <variant-b>
"""
import re,sys,statistics as st
rows=[]
for l in open(sys.argv[1]):
    r,v=l.split()[:2]; d={k:float(x) for k,x in re.findall(r'(\w+)=([\d.]+)',l)}; rows.append((r,v,d))
vs=sys.argv[2:]
for k in ('p50','p95','p99','mean','allocs_per_call','items_per_call'):
    med={}
    if any(k not in d for _,_,d in rows): continue
    for v in vs:
        xs=[d[k] for _,vv,d in rows if vv==v]
        q=st.quantiles(xs,n=4,method='inclusive') if len(xs)>1 else [xs[0]]*3
        med[v]=st.median(xs)
        print(f"{k} {v} n={len(xs)} median={med[v]:.1f} IQR={q[0]:.1f}..{q[2]:.1f}", f"change={(med[v]-med[vs[0]])/med[vs[0]]*100:+.1f}%" if v!=vs[0] else "")
