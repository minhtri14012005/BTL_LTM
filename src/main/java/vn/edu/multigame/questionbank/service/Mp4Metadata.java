package vn.edu.multigame.questionbank.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded metadata validation for self-contained, non-fragmented MP4 (avc1 + AAC-LC).
 * This is not a decoder/transcoder: damaged compressed samples can still fail in a browser. */
final class Mp4Metadata {
    record Info(long durationMs, int width, int height, String videoCodec, String audioCodec) {}
    private record Box(String type,int start,int end) {}
    private final byte[] data;
    private int boxCount;
    private Mp4Metadata(byte[] data) { this.data=data; }
    static Info inspect(byte[] data) {
        try { return new Mp4Metadata(data).inspect(); }
        catch (IllegalArgumentException | IndexOutOfBoundsException failure) { throw invalid(); }
    }
    private Info inspect() {
        var top=boxes(0,data.length);
        Box ftyp=one(top,"ftyp"),moov=one(top,"moov");
        require(ftyp.start==8 && ftyp.end-ftyp.start>=8 && (ftyp.end-ftyp.start)%4==0);
        boolean mp4=false;
        for(int p=ftyp.start;p<ftyp.end;p+=4) if(p!=ftyp.start+4 && Set.of("isom","iso2","iso4","iso5","iso6","mp41","mp42","avc1").contains(type(p)))mp4=true;
        require(mp4 && top.stream().noneMatch(b -> b.type.equals("moof")));
        var media=top.stream().filter(b -> b.type.equals("mdat")).toList();require(!media.isEmpty());
        var movie=children(moov);require(movie.stream().noneMatch(b -> b.type.equals("mvex")));
        long duration=duration(one(movie,"mvhd"));
        var tracks=movie.stream().filter(b -> b.type.equals("trak")).toList();require(tracks.size()==2);
        int width=0,height=0,videos=0,audios=0;
        for(Box trak:tracks) {
            var mdia=children(one(children(trak),"mdia"));
            Box hdlr=one(mdia,"hdlr");need(hdlr,12);String kind=type(hdlr.start+8);
            long trackDuration=duration(one(mdia,"mdhd"));require(Math.abs(trackDuration-duration)<=2000);
            var minf=children(one(mdia,"minf"));
            Box dref=one(children(one(minf,"dinf")),"dref");need(dref,8);
            require(u32(dref.start+4)==1);var refs=boxes(dref.start+8,dref.end);
            require(refs.size()==1 && refs.getFirst().type.equals("url "));Box url=refs.getFirst();
            require(url.end-url.start==4 && u32(url.start)==1); // self-contained flag, no external URL
            var table=children(one(minf,"stbl"));Box stsd=one(table,"stsd");need(stsd,8);
            require(u32(stsd.start+4)==1);var entries=boxes(stsd.start+8,stsd.end);require(entries.size()==1);
            Box entry=entries.getFirst();need(entry,8);require(u16(entry.start+6)==1);
            if(kind.equals("vide")) {
                require(++videos==1 && entry.type.equals("avc1"));need(entry,78);
                width=u16(entry.start+24);height=u16(entry.start+26);
                require(width>0 && height>0 && width<=1920 && height<=1920 && (long)width*height<=2073600);
                Box avc=one(boxes(entry.start+78,entry.end),"avcC");need(avc,7);
                require(u8(avc.start)==1 && Set.of(66,77,100).contains(u8(avc.start+1)) && (u8(avc.start+4)&3)==3);
                int p=avc.start+6,sps=u8(avc.start+5)&31;require(sps>0);
                for(int i=0;i<sps;i++){require(p+2<=avc.end);int n=u16(p);p+=2;require(n>0 && p+n<=avc.end);p+=n;}
                require(p<avc.end);int pps=u8(p++);require(pps>0);
                for(int i=0;i<pps;i++){require(p+2<=avc.end);int n=u16(p);p+=2;require(n>0 && p+n<=avc.end);p+=n;}
            } else {
                require(kind.equals("soun") && ++audios==1 && entry.type.equals("mp4a"));need(entry,28);
                require(u16(entry.start+8)==0 && u16(entry.start+16)>=1 && u16(entry.start+16)<=2);
                Box esds=one(boxes(entry.start+28,entry.end),"esds");need(esds,6);
                int[] cursor={esds.start+4};int end=descriptor(cursor,esds.end,3);require(cursor[0]+3<=end);
                cursor[0]+=2;int flags=u8(cursor[0]++);require(flags==0);
                int decoderEnd=descriptor(cursor,end,4);require(cursor[0]+13<=decoderEnd);
                require(u8(cursor[0])==0x40 && (u8(cursor[0]+1)>>>2)==5);cursor[0]+=13;
                int ascEnd=descriptor(cursor,decoderEnd,5);require(cursor[0]+2<=ascEnd);
                int config=u16(cursor[0]);require((config>>>11)==2 && ((config>>>7)&15)<=12 && ((config>>>3)&15)>=1 && ((config>>>3)&15)<=2);
            }
            validateSamples(table,media);
        }
        require(videos==1 && audios==1);
        return new Info(duration,width,height,"H.264/avc1","AAC-LC/mp4a");
    }
    private void validateSamples(List<Box> table,List<Box> media) {
        Box sizes=one(table,"stsz");need(sizes,12);long fixed=u32(sizes.start+4),count=u32(sizes.start+8);
        require(count>0 && count<=100000 && (fixed>0 || sizes.end-sizes.start==12+4*count));
        Box timing=one(table,"stts");need(timing,8);long timeCount=u32(timing.start+4),timed=0;
        require(timeCount>0 && timeCount<=count && timing.end-timing.start==8+8*timeCount);
        for(int i=0;i<timeCount;i++){timed+=u32(timing.start+8+i*8);require(u32(timing.start+12+i*8)>0);}require(timed==count);
        var offsets=table.stream().filter(b -> b.type.equals("stco") || b.type.equals("co64")).toList();require(offsets.size()==1);
        Box chunks=offsets.getFirst();need(chunks,8);long chunkCount=u32(chunks.start+4);int stride=chunks.type.equals("co64")?8:4;
        require(chunkCount>0 && chunkCount<=count && chunks.end-chunks.start==8+stride*chunkCount);
        Box mapping=one(table,"stsc");need(mapping,8);long rows=u32(mapping.start+4);
        require(rows>0 && rows<=chunkCount && mapping.end-mapping.start==8+12*rows);
        long previous=0;
        for(int i=0;i<rows;i++){long first=u32(mapping.start+8+i*12);require(first>previous && first<=chunkCount && u32(mapping.start+12+i*12)>0 && u32(mapping.start+16+i*12)==1);previous=first;}
        require(u32(mapping.start+8)==1);
        int row=0,sample=0;
        for(int chunk=1;chunk<=chunkCount;chunk++) {
            if(row+1<rows && chunk>=u32(mapping.start+8+(row+1)*12))row++;
            long samples=u32(mapping.start+12+row*12),length=0;require(samples<=count-sample);
            for(int j=0;j<samples;j++){long n=fixed>0?fixed:u32(sizes.start+12+4*sample);require(n>0);length+=n;sample++;}
            long offset=stride==4?u32(chunks.start+8+(chunk-1)*4):u64(chunks.start+8+(chunk-1)*8);
            final long stop=offset+length;require(stop>offset && media.stream().anyMatch(b -> offset>=b.start && stop<=b.end));
        }
        require(sample==count);
    }
    private long duration(Box b) {
        need(b,20);int version=u8(b.start);require(version<=1);int scaleAt=b.start+(version==0?12:20);need(b,version==0?20:32);
        long scale=u32(scaleAt),ticks=version==0?u32(scaleAt+4):u64(scaleAt+4);
        require(scale>0 && ticks>0 && ticks<=120*scale);return Math.max(1,ticks*1000/scale);
    }
    private int descriptor(int[] p,int end,int tag) {
        require(p[0]<end && u8(p[0]++)==tag);int size=0,n=0,b;
        do{require(p[0]<end && ++n<=4);b=u8(p[0]++);size=(size<<7)|(b&127);}while((b&128)!=0);
        require(size>=0 && p[0]+size<=end);return p[0]+size;
    }
    private List<Box> children(Box b){return boxes(b.start,b.end);}
    private List<Box> boxes(int begin,int end) {
        var result=new ArrayList<Box>();int p=begin;
        while(p<end){require(++boxCount<=10000 && end-p>=8);long size=u32(p);String type=type(p+4);int header=8;
            if(size==1){require(end-p>=16);size=u64(p+8);header=16;}
            else if(size==0)size=end-p;
            require(size>=header && size<=end-p);result.add(new Box(type,p+header,p+(int)size));p+=(int)size;
        }return result;
    }
    private Box one(List<Box> boxes,String type){var found=boxes.stream().filter(b -> b.type.equals(type)).toList();require(found.size()==1);return found.getFirst();}
    private void need(Box b,int n){require(b.end-b.start>=n);}
    private String type(int p){return new String(data,p,4,StandardCharsets.ISO_8859_1);}
    private int u8(int p){return Byte.toUnsignedInt(data[p]);}
    private int u16(int p){return (u8(p)<<8)|u8(p+1);}
    private long u32(int p){return Integer.toUnsignedLong(ByteBuffer.wrap(data,p,4).getInt());}
    private long u64(int p){long n=ByteBuffer.wrap(data,p,8).getLong();require(n>=0);return n;}
    private static void require(boolean condition){if(!condition)throw new IllegalArgumentException();}
    private static QuestionBankFailure invalid(){return new QuestionBankFailure(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_VIDEO","Cần MP4 không phân mảnh, một track H.264 (avc1) và một track AAC-LC, tối đa120 giây/1080p, có sample data nội bộ hợp lệ.");}
}
