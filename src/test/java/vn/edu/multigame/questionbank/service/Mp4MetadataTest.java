package vn.edu.multigame.questionbank.service;
import java.io.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
class Mp4MetadataTest {
    @TempDir Path directory;
    byte[] fixture(String name)throws IOException {try(var s=getClass().getResourceAsStream("/quiz/"+name)){return s.readAllBytes();}}
    @Test void validatesActualH264AacFixtureAndImmutableStaging()throws Exception {
        byte[] data=fixture("song-av.mp4");var info=Mp4Metadata.inspect(data);
        assertThat(info.durationMs()).isEqualTo(25000);assertThat(info.width()).isEqualTo(320);assertThat(info.height()).isEqualTo(180);
        var store=new QuestionVideoStore(directory.toString());var file=new MockMultipartFile("file","../../unsafe.mp4","text/plain",data);
        var saved=store.saveDraft(1,file);assertThat(saved.contentType()).isEqualTo("video/mp4");assertThat(store.saveDraft(1,file)).isEqualTo(saved);
        store.attachDraft(2,1,saved.mediaRef());assertThat(store.read(2,saved.mediaRef()).contentLength()).isEqualTo(data.length);
        assertThatThrownBy(()->store.attachDraft(3,4,saved.mediaRef())).isInstanceOf(QuestionBankFailure.class);
        assertThatThrownBy(()->store.read(2,"../../song-av.mp4")).isInstanceOf(QuestionBankFailure.class);
        assertThat(directory.resolve("unsafe.mp4")).doesNotExist();
    }
    @Test void rejectsSilentTruncatedWrongCodecExternalReferenceAndOutOfBoundsSamples()throws Exception {
        byte[] data=fixture("song-av.mp4");
        assertThatThrownBy(()->Mp4Metadata.inspect(fixture("song-silent.mp4"))).isInstanceOf(QuestionBankFailure.class);
        assertThatThrownBy(()->Mp4Metadata.inspect(Arrays.copyOf(data,data.length-1))).isInstanceOf(QuestionBankFailure.class);
        assertThatThrownBy(()->Mp4Metadata.inspect(new byte[32])).isInstanceOf(QuestionBankFailure.class);
        for(String marker:new String[]{"avc1","mp4a","stco","url "}) {
            byte[] broken=data.clone();byte[] needle=marker.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            int at=-1;for(int i=0;i<data.length-4;i++)if(Arrays.equals(Arrays.copyOfRange(data,i,i+4),needle)){at=i;if(!marker.equals("avc1") || i>64)break;}
            assertThat(at).isGreaterThan(0);
            if(marker.equals("stco"))Arrays.fill(broken,at+12,at+16,(byte)0xff);
            else if(marker.equals("url "))broken[at+7]=0;
            else broken[at]='X';
            assertThatThrownBy(()->Mp4Metadata.inspect(broken)).as(marker).isInstanceOf(QuestionBankFailure.class);
        }
    }
    @Test void rejectsExcessDurationDimensionsAndFragmentedContainer()throws Exception {
        byte[] original=fixture("song-av.mp4");
        byte[] duration=original.clone();int mvhd=index(duration,"mvhd",0);long scale=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(duration,mvhd+16,4).getInt());java.nio.ByteBuffer.wrap(duration,mvhd+20,4).putInt((int)(121*scale));
        assertThatThrownBy(()->Mp4Metadata.inspect(duration)).isInstanceOf(QuestionBankFailure.class);
        byte[] dimensions=original.clone();int avc=index(dimensions,"avc1",64);java.nio.ByteBuffer.wrap(dimensions,avc+28,2).putShort((short)4096);
        assertThatThrownBy(()->Mp4Metadata.inspect(dimensions)).isInstanceOf(QuestionBankFailure.class);
        byte[] fragmented=Arrays.copyOf(original,original.length+8);java.nio.ByteBuffer.wrap(fragmented,original.length,8).putInt(8).put("moof".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(()->Mp4Metadata.inspect(fragmented)).isInstanceOf(QuestionBankFailure.class);
    }
    int index(byte[] bytes,String text,int start){byte[] tag=text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);for(int i=start;i<bytes.length-4;i++)if(Arrays.equals(Arrays.copyOfRange(bytes,i,i+4),tag))return i;throw new AssertionError(text);}
    @Test void sizeAndIntegrityFailuresDoNotReplaceBlobs()throws Exception {
        var store=new QuestionVideoStore(directory.toString());
        assertThatThrownBy(()->store.saveDraft(1,new MockMultipartFile("file",new byte[QuestionVideoStore.MAX_BYTES+1]))).isInstanceOf(QuestionBankFailure.class);
        var saved=store.saveDraft(1,new MockMultipartFile("file",fixture("song-av.mp4")));
        java.nio.file.Files.write(directory.resolve("drafts/1/"+saved.mediaRef().substring(7)+".mp4"),new byte[]{0});
        assertThatThrownBy(()->store.readDraft(1,saved.mediaRef())).isInstanceOf(QuestionBankFailure.class);
        assertThatThrownBy(()->store.saveDraft(1,new MockMultipartFile("file",fixture("song-av.mp4")))).isInstanceOf(QuestionBankFailure.class);
    }
}
