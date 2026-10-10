"""Optional PyAV/NumPy/Pillow fixture generator; not a Server dependency.

Renders five familiar traditional/classical melodies as instrumental demos.
No commercial recording, lyrics, title or answer is included in the video.
Do not rerender uploaded clips in place; re-importing never overwrites a bank.
"""
import json
from fractions import Fraction
from pathlib import Path
import av
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'data/demo-banks'
RATE = 48000
QUARTER = .38
SONGS = [
    ('Happy Birthday to You',['Happy Birthday to You','Happy Birthday','Chúc mừng sinh nhật'],
     'G4:.75 G4:.25 A4:1 G4:1 C5:1 B4:2 G4:.75 G4:.25 A4:1 G4:1 D5:1 C5:2 G4:.75 G4:.25 G5:1 E5:1 C5:1 B4:1 A4:2 F5:.75 F5:.25 E5:1 C5:1 D5:1 C5:2'),
    ('Twinkle Twinkle Little Star',['Twinkle Twinkle Little Star','Twinkle, Twinkle, Little Star'],
     'C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2'),
    ('Frère Jacques',['Frère Jacques','Frere Jacques','Kìa con bướm vàng','Kia con buom vang'],
     'C4:1 D4:1 E4:1 C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 F4:1 G4:2 E4:1 F4:1 G4:2 G4:.5 A4:.5 G4:.5 F4:.5 E4:1 C4:1 G4:.5 A4:.5 G4:.5 F4:.5 E4:1 C4:1 C4:1 G3:1 C4:2 C4:1 G3:1 C4:2'),
    ('Jingle Bells',['Jingle Bells','Jingle Bell','Tiếng chuông ngân','Chuông ngân vang'],
     'E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1 D4:1 E4:4 F4:1 F4:1 F4:1 F4:1 F4:1 E4:1 E4:1 E4:.5 E4:.5 E4:1 D4:1 D4:1 E4:1 D4:2 G4:2'),
    ('Ode to Joy',['Ode to Joy','Hymn to Joy','Khúc ca niềm vui'],
     'E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:.5 D4:2 E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:.5 C4:2')]

def audio(score):
    parts=[np.zeros(int(.35*RATE),dtype=np.float32)]
    semitone=dict(C=0,D=2,E=4,F=5,G=7,A=9,B=11)
    for item in score.split():
        note,length=item.split(':')
        duration=float(length)*QUARTER
        midi=12*(int(note[-1])+1)+semitone[note[0]]
        frequency=440*2**((midi-69)/12)
        t=np.arange(round(duration*RATE))/RATE
        wave=np.sin(2*np.pi*frequency*t)+.30*np.sin(4*np.pi*frequency*t)+.12*np.sin(6*np.pi*frequency*t)
        attack=np.minimum(t/.012,1)
        release=np.minimum((duration-t)/.065,1).clip(0,1)
        envelope=attack*release*(.22+.78*np.exp(-4*t/max(duration,.1)))
        parts.append((.28*wave*envelope).astype(np.float32))
    parts.append(np.zeros(int(.5*RATE),dtype=np.float32))
    samples=np.concatenate(parts)
    samples=np.pad(samples,(0,(-len(samples))%4800))
    return samples

def render(path,score,index):
    samples=audio(score)
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',28)
    small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',19)
    with av.open(str(path),'w',format='mp4',options={'movflags':'+faststart'}) as out:
        video=out.add_stream('libx264',rate=10)
        video.width=704;video.height=396;video.pix_fmt='yuv420p'
        video.options={'preset':'ultrafast','crf':'24','profile':'baseline','g':'10'}
        sound=out.add_stream('aac',rate=RATE)
        sound.layout='mono';sound.bit_rate=128000
        framecount=len(samples)//4800
        for i in range(framecount):
            im=Image.new('RGB',(704,396),'#123b36');d=ImageDraw.Draw(im)
            d.rounded_rectangle((25,25,679,371),radius=25,fill='#1b5046',outline='#427665',width=2)
            d.text((65,60),'ĐOÁN TÊN BÀI HÁT',font=font,fill='#ecf2d9')
            d.text((65,103),f'Giai điệu nhạc cụ · Câu {index}',font=small,fill='#b7cfc1')
            for bar in range(32):
                height=int(17+67*abs(np.sin(i*.22+bar*.38)))
                x=69+bar*17
                d.rounded_rectangle((x,240-height,x+9,240+height),radius=4,fill='#b7cd8c' if bar%3 else '#e5cb88')
            d.rounded_rectangle((65,332,639,338),radius=3,fill='#356a5b')
            d.rounded_rectangle((65,332,65+max(3,int(574*(i+1)/framecount)),338),radius=3,fill='#e5cb88')
            vf=av.VideoFrame.from_ndarray(np.asarray(im),format='rgb24')
            vf.pts=i;vf.time_base=Fraction(1,10)
            for packet in video.encode(vf):out.mux(packet)
            af=av.AudioFrame.from_ndarray(samples[i*4800:(i+1)*4800].reshape(1,-1),format='flt',layout='mono')
            af.sample_rate=RATE;af.pts=i*4800;af.time_base=Fraction(1,RATE)
            for packet in sound.encode(af):out.mux(packet)
        for packet in video.encode():out.mux(packet)
        for packet in sound.encode():out.mux(packet)
    return dict(file=path.name,durationSeconds=len(samples)/RATE,bytes=path.stat().st_size)

def main():
    dataset=json.loads((OUT/'questions.json').read_text(encoding='utf-8'))
    clips=[];questions=[]
    for index,(title,aliases,score) in enumerate(SONGS,1):
        filename=f'song-{index:02}.mp4'
        clips.append(dict(title=title,**render(OUT/filename,score,index)))
        questions.append(dict(content='Nghe giai điệu trong video và nhập tên bài hát.',acceptedAnswers=aliases,videoFile=filename))
    dataset['modes']=[b for b in dataset['modes'] if b['mode']!='SONG']
    dataset['modes'].append(dict(mode='SONG',title='Đoán tên bài hát — 5 giai điệu mẫu',questions=questions))
    (OUT/'questions.json').write_text(json.dumps(dataset,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'song-provenance.json').write_text(json.dumps(dict(kind='Locally synthesized instrumental demos, not commercial recordings',notesInGenerator=True,clips=clips),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(clips=clips),ensure_ascii=False,indent=2))

if __name__=='__main__':main()
