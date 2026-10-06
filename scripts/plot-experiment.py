"""Optional export figures from real summary JSON; matplotlib is for reporting only."""
import json, os, sys
from pathlib import Path
os.environ.setdefault('MPLCONFIGDIR',str(Path(__file__).resolve().parents[1]/'target/matplotlib-cache'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

root=Path(sys.argv[1]);data=json.loads((root/'summary.json').read_text(encoding='utf-8'))
plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False,'svg.fonttype':'none'})
fig,axes=plt.subplots(1,2,figsize=(10,3.6),constrained_layout=True)
for ax,stat,title in zip(axes,('p50_ms','p95_ms'),('Median server command duration','95th percentile server command duration')):
    for mode,color in [('baseline','#677485'),('proposed','#155e9b')]:
        loads=data['modes'][mode]['loads'];xs=list(map(int,loads));ys=[loads[str(n)]['server_processing'][stat] for n in xs]
        ax.plot(xs,ys,'o-',color=color,label=mode)
    ax.set(title=title,xlabel='Concurrent Player accounts (one Room)',ylabel='Milliseconds, includes queue wait',xticks=[3,5,10,20],ylim=(0,None));ax.grid(axis='y',alpha=.2);ax.legend(frameon=False)
fig.savefig(root/'server-latency.png',dpi=240);fig.savefig(root/'server-latency.svg');plt.close(fig)
names=['lost_spin_ack','lost_answer_ack','retry_finished','reconnect_decision','reconnect_after_answer']
labels=['Lost Spin ACK','Lost Answer ACK','Retry FINISHED','Reconnect DECISION','Reconnect after Answer']
fig,ax=plt.subplots(figsize=(9,3.8),constrained_layout=True)
for offset,mode,color in [(-.18,'baseline','#677485'),(.18,'proposed','#155e9b')]:
    vals=[100*data['modes'][mode]['reliability'][n]['success_rate'] for n in names]
    bars=ax.bar([i+offset for i in range(5)],vals,.36,label=mode,color=color)
    for bar,n in zip(bars,names):
        v=data['modes'][mode]['reliability'][n];ax.text(bar.get_x()+bar.get_width()/2,bar.get_height()+1,f"{v['success']}/{v['attempts']}",ha='center',va='bottom',fontsize=9)
ax.set(xticks=range(5),xticklabels=labels,ylabel='Recovered original response / matching model (%)',ylim=(0,116));ax.set_title('Reliability in the measured loopback trials',pad=40);ax.legend(frameon=False,loc='lower center',bbox_to_anchor=(.5,1.01),ncol=2);ax.grid(axis='y',alpha=.15)
fig.savefig(root/'reliability.png',dpi=240);fig.savefig(root/'reliability.svg');plt.close(fig)
print('Exported two PNG/SVG figures from measured summary.json; no estimated data.')
