package vn.edu.multigame.questionbank.service;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.multigame.questionbank.dto.response.VideoResponse;

/** Immutable local video blobs. Auth and released-question checks happen in QuestionBankService. */
@Service @Profile("mysql")
public class QuestionVideoStore {
    public static final int MAX_BYTES=20*1024*1024;
    private final Path root;
    public QuestionVideoStore(@Value("${quiz.videos.directory:./data/quiz-videos}") String directory){root=Path.of(directory).toAbsolutePath().normalize();}
    public VideoResponse save(long quizId,MultipartFile file){return save(directory(quizId),file,"/api/quizzes/"+quizId+"/videos/");}
    public VideoResponse saveDraft(long userId,MultipartFile file){return save(draftDirectory(userId),file,"/api/quizzes/videos/drafts/");}
    public Resource read(long quizId,String ref){return read(path(directory(quizId),ref),ref);}
    public Resource readDraft(long userId,String ref){return read(path(draftDirectory(userId),ref),ref);}
    public void attachDraft(long quizId,long ownerId,String ref) {
        Path target=path(directory(quizId),ref);
        if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)){read(target,ref);return;}
        Path source=path(draftDirectory(ownerId),ref);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw failure(HttpStatus.BAD_REQUEST,"VIDEO_NOT_FOUND","Video chưa upload bởi tài khoản này.");
        read(source,ref);
        try{createDirectory(target.getParent());try{Files.createLink(target,source);}catch(FileAlreadyExistsException duplicate){/* immutable */}read(target,ref);}
        catch(IOException | UnsupportedOperationException e){throw unavailable();}
    }
    private VideoResponse save(Path directory,MultipartFile file,String url) {
        if(file.isEmpty())throw failure(HttpStatus.BAD_REQUEST,"INVALID_VIDEO","Video không được rỗng.");
        if(file.getSize()>MAX_BYTES)throw tooLarge();
        try {
            byte[] bytes;try(var input=file.getInputStream()){bytes=input.readNBytes(MAX_BYTES+1);}if(bytes.length>MAX_BYTES)throw tooLarge();
            var metadata=Mp4Metadata.inspect(bytes);String hash=hash(bytes),ref="sha256:"+hash;Path target=path(directory,ref);
            createDirectory(directory);Path temp=Files.createTempFile(directory,".upload-",".tmp");
            try{Files.write(temp,bytes);try{Files.createLink(target,temp);}catch(FileAlreadyExistsException duplicate){/* Never replace existing data. */}read(target,ref);}
            finally{Files.deleteIfExists(temp);}
            return new VideoResponse(ref,url+hash,"video/mp4",bytes.length,metadata.durationMs(),metadata.width(),metadata.height(),metadata.videoCodec(),metadata.audioCodec());
        }catch(IOException | UnsupportedOperationException e){throw unavailable();}
    }
    private Resource read(Path file,String ref) {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw failure(HttpStatus.NOT_FOUND,"VIDEO_NOT_FOUND","Không tìm thấy video.");
        try {
            if(Files.size(file)>MAX_BYTES || !hash(Files.readAllBytes(file)).equals(ref.substring(7)))throw new IOException("Integrity mismatch");
            // MVC Resource converters stream bytes and implement HTTP Range after authorization.
            return new FileSystemResource(file);
        }catch(IOException e){throw unavailable();}
    }
    private Path directory(long id){if(id<=0)throw invalidRef();return root.resolve(Long.toString(id));}
    private Path draftDirectory(long id){if(id<=0)throw invalidRef();return root.resolve("drafts").resolve(Long.toString(id));}
    private Path path(Path directory,String ref) {
        if(ref==null || !ref.matches("sha256:[0-9a-f]{64}"))throw invalidRef();
        for(Path p=directory;p!=null && p.startsWith(root);p=p.getParent())if(Files.isSymbolicLink(p))throw unavailable();
        Path target=directory.resolve(ref.substring(7)+".mp4");if(Files.isSymbolicLink(target))throw unavailable();return target;
    }
    private void createDirectory(Path directory)throws IOException {Files.createDirectories(directory);path(directory,"sha256:"+"0".repeat(64));}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static QuestionBankFailure failure(HttpStatus status,String code,String message){return new QuestionBankFailure(status,code,message);}
    private static QuestionBankFailure tooLarge(){return failure(HttpStatus.PAYLOAD_TOO_LARGE,"VIDEO_TOO_LARGE","Video tối đa20 MiB.");}
    private static QuestionBankFailure unavailable(){return failure(HttpStatus.SERVICE_UNAVAILABLE,"VIDEO_UNAVAILABLE","Không thể đọc/lưu video hoặc file không toàn vẹn.");}
    private static QuestionBankFailure invalidRef(){return failure(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Media reference không hợp lệ.");}
}
