# Results derived from CSV

Same-machine loopback. Latency đơn vị ms; p95 dùng nearest-rank. Không phải số đo LAN nhiều thiết bị.

| Mode | Reliability scenario | Success / attempts | Mismatch | Response p50 / p95 (ms) |
|---|---|---:|---:|---:|
| baseline | lost_answer_ack | 0/30 | 0 | 14.081 / 23.539 |
| baseline | lost_spin_ack | 0/30 | 0 | 14.558 / 23.707 |
| baseline | reconnect_after_answer | 0/30 | 30 | 35.685 / 63.819 |
| baseline | reconnect_decision | 0/30 | 30 | 34.612 / 87.968 |
| baseline | retry_finished | 0/30 | 0 | 13.883 / 29.849 |
| proposed | lost_answer_ack | 30/30 | 0 | 13.533 / 20.598 |
| proposed | lost_spin_ack | 30/30 | 0 | 9.135 / 17.091 |
| proposed | reconnect_after_answer | 30/30 | 0 | 12.181 / 21.215 |
| proposed | reconnect_decision | 30/30 | 0 | 8.416 / 19.942 |
| proposed | retry_finished | 30/30 | 0 | 9.650 / 24.712 |

| Mode | Players/Client accounts | Concurrent Rooms | Rounds | Samples | Server p50 / p95 ms | Client response p50 / p95 ms |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 3 | 1 | 3 | 18 | 56.376 / 142.149 | 66.583 / 155.855 |
| baseline | 5 | 1 | 3 | 30 | 47.345 / 111.948 | 51.562 / 119.450 |
| baseline | 10 | 1 | 3 | 60 | 93.103 / 256.693 | 98.075 / 270.455 |
| baseline | 20 | 1 | 3 | 120 | 153.784 / 285.865 | 162.837 / 298.568 |
| proposed | 3 | 1 | 3 | 18 | 35.388 / 81.849 | 39.070 / 88.193 |
| proposed | 5 | 1 | 3 | 30 | 53.423 / 161.266 | 56.107 / 170.637 |
| proposed | 10 | 1 | 3 | 60 | 82.359 / 165.633 | 86.981 / 178.539 |
| proposed | 20 | 1 | 3 | 120 | 176.730 / 451.814 | 187.754 / 466.826 |

baseline: duplicate side effects=0; durable invariant violations=0 trên 22 Game checks.

proposed: duplicate side effects=0; durable invariant violations=0 trên 22 Game checks.
