# Results derived from CSV

Same-machine loopback. Latency đơn vị ms; p95 dùng nearest-rank. Không phải số đo LAN nhiều thiết bị.

| Mode | Reliability scenario | Success / attempts | Mismatch | Response p50 / p95 (ms) |
|---|---|---:|---:|---:|
| baseline | lost_answer_ack | 0/30 | 0 | 5.081 / 8.931 |
| baseline | lost_spin_ack | 0/30 | 0 | 6.712 / 11.786 |
| baseline | reconnect_after_answer | 0/30 | 30 | 12.467 / 22.905 |
| baseline | reconnect_decision | 0/30 | 30 | 14.031 / 31.410 |
| baseline | retry_finished | 0/30 | 0 | 6.031 / 9.728 |
| proposed | lost_answer_ack | 30/30 | 0 | 5.864 / 12.995 |
| proposed | lost_spin_ack | 30/30 | 0 | 6.272 / 13.903 |
| proposed | reconnect_after_answer | 30/30 | 0 | 4.701 / 11.428 |
| proposed | reconnect_decision | 30/30 | 0 | 4.447 / 12.586 |
| proposed | retry_finished | 30/30 | 0 | 6.050 / 10.822 |

| Mode | Players/Client accounts | Concurrent Rooms | Rounds | Samples | Server p50 / p95 ms | Client response p50 / p95 ms |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 3 | 1 | 3 | 18 | 26.509 / 59.833 | 29.149 / 62.901 |
| baseline | 4 | 1 | 3 | 24 | 41.610 / 77.502 | 44.444 / 81.629 |
| proposed | 3 | 1 | 3 | 18 | 30.141 / 63.381 | 32.739 / 71.558 |
| proposed | 4 | 1 | 3 | 24 | 40.677 / 80.265 | 42.718 / 83.513 |

baseline: duplicate side effects=0; durable invariant violations=0 trên 16 Game checks.

proposed: duplicate side effects=0; durable invariant violations=0 trên 16 Game checks.
