"""Generate tables solely from flushed CSV, preserving denominators and raw evidence."""
import csv, hashlib, json, math, statistics, sys
from pathlib import Path

def percentile(values,p):
    values=sorted(values)
    return values[max(0,math.ceil(len(values)*p)-1)] if values else None

def span(rows,field):
    values=[float(r[field]) for r in rows if r.get(field)]
    return dict(n=len(values),p50_ms=percentile(values,.50),p95_ms=percentile(values,.95),max_ms=max(values) if values else None)

def main(directory):
    root=Path(directory);summary={'placement':'same-machine loopback; no multi-device LAN or Internet inference','modes':{}}
    lines=['# Results derived from CSV','', 'Same-machine loopback. Latency đơn vị ms; p95 dùng nearest-rank. Không phải số đo LAN nhiều thiết bị.', '',
           '| Mode | Reliability scenario | Success / attempts | Mismatch | Response p50 / p95 (ms) |','|---|---|---:|---:|---:|']
    for mode in ['baseline','proposed']:
        client=root/(mode+'-client.csv');server=root/(mode+'-server.csv')
        if not client.exists():continue
        with client.open(encoding='utf-8-sig',newline='') as f:rows=list(csv.DictReader(f))
        with server.open(encoding='utf-8-sig',newline='') as f:metrics=list(csv.DictReader(f))
        result={'client_rows':len(rows),'server_rows':len(metrics),'reliability':{},'loads':{},
                'duplicate_side_effects':sum(int(r['duplicate_side_effects'] or 0) for r in rows),
                'durable_invariant_violations':sum(int(r['invariant_violation'] or 0) for r in rows),
                'durable_game_checks':sum(r['scenario']=='durable_invariants' for r in rows)}
        for scenario in sorted({r['scenario'] for r in rows}-{'load','durable_invariants'}):
            selected=[r for r in rows if r['scenario']==scenario]
            success=sum(int(r['original_recovered'] or 0) for r in selected) if scenario.startswith(('lost_','retry_')) else sum(r['state_mismatch']=='0' for r in selected)
            mismatches=sum(int(r['state_mismatch'] or 0) for r in selected)
            data=dict(attempts=len(selected),success=success,success_rate=success/len(selected),state_mismatches=mismatches,
                      response=span(selected,'response_ms'),snapshot_response=span(selected,'snapshot_response_ms'),client_apply_resync=span(selected,'client_apply_resync_ms'))
            result['reliability'][scenario]=data
            response=data['response'];lines.append(f"| {mode} | {scenario} | {success}/{len(selected)} | {mismatches} | {response['p50_ms']:.3f} / {response['p95_ms']:.3f} |")
        for n in sorted({int(r['clients']) for r in rows if r['scenario']=='load'}):
            selected=[r for r in rows if r['scenario']=='load' and int(r['clients'])==n]
            ids={(r['user_id'],r['game_id'],r['operation']) for r in selected}
            server_rows=[r for r in metrics if (r['user_id'],r['game_id'],r['operation']) in ids and r['outcome']=='SUCCESS']
            result['loads'][str(n)]=dict(clients=n,concurrent_rooms=1,rounds=len({r['round'] for r in selected}),samples=len(selected),
                                        client_response=span(selected,'response_ms'),server_processing=span(server_rows,'duration_ms'))
        summary['modes'][mode]=result
    lines+=['','| Mode | Players/Client accounts | Concurrent Rooms | Rounds | Samples | Server p50 / p95 ms | Client response p50 / p95 ms |',
            '|---|---:|---:|---:|---:|---:|---:|']
    for mode,result in summary['modes'].items():
        for n,data in result['loads'].items():
            s=data['server_processing'];c=data['client_response']
            lines.append(f"| {mode} | {n} | 1 | {data['rounds']} | {data['samples']} | {s['p50_ms']:.3f} / {s['p95_ms']:.3f} | {c['p50_ms']:.3f} / {c['p95_ms']:.3f} |")
    for mode,result in summary['modes'].items():
        lines+=['',f"{mode}: duplicate side effects={result['duplicate_side_effects']}; durable invariant violations={result['durable_invariant_violations']} trên {result['durable_game_checks']} Game checks."]
    (root/'summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    (root/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in root.iterdir() if p.is_file() and p.name!='sha256.json'}
    (root/'sha256.json').write_text(json.dumps(files,indent=2),encoding='utf-8')
    print(json.dumps(summary,indent=2))

if __name__=='__main__':main(sys.argv[1])
