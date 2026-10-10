"""Optional fixture regeneration: PyAV17.1.0 + NumPy, never an application dependency.
Produces original synthetic coloured video + a440Hz tone; no copyrighted song."""
from pathlib import Path
from fractions import Fraction
import av,numpy as np
ROOT=Path(__file__).resolve().parents[1]
def generate(path,seconds=25,audio=True,tone=440):
 path.parent.mkdir(parents=True,exist_ok=True)
 with av.open(str(path),'w',format='mp4',options={'movflags':'+faststart'}) as out:
  v=out.add_stream('libx264',rate=10);v.width=320;v.height=180;v.pix_fmt='yuv420p';v.options={'preset':'ultrafast','crf':'30','profile':'baseline','g':'10'}
  a=out.add_stream('aac',rate=48000) if audio else None
  if a:a.layout='mono';a.bit_rate=64000
  for i in range(seconds*10):
   rgb=np.zeros((180,320,3),dtype=np.uint8);rgb[:,:,0]=(i*3)%200+30;rgb[:,:,1]=90;rgb[:,:,2]=150
   frame=av.VideoFrame.from_ndarray(rgb,format='rgb24');frame.pts=i;frame.time_base=Fraction(1,10)
   for packet in v.encode(frame):out.mux(packet)
   if a:
    samples=np.arange(i*4800,(i+1)*4800);wave=(0.08*np.sin(2*np.pi*tone*samples/48000)).astype(np.float32).reshape(1,-1)
    af=av.AudioFrame.from_ndarray(wave,format='flt',layout='mono');af.sample_rate=48000;af.pts=i*4800;af.time_base=Fraction(1,48000)
    for packet in a.encode(af):out.mux(packet)
  for packet in v.encode():out.mux(packet)
  if a:
   for packet in a.encode():out.mux(packet)
if __name__=='__main__':
 for name,seconds,audio,tone in [('song-av.mp4',25,True,440),('song-av2.mp4',25,True,660),('song-silent.mp4',1,False,440)]:
  p=ROOT/'src/test/resources/quiz'/name;generate(p,seconds,audio,tone);print(name,p.stat().st_size)
