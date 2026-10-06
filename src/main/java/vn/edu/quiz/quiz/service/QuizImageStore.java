package vn.edu.quiz.quiz.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Server-managed immutable PNG blobs, scoped by Quiz ID; filenames never come from Client. */
@Service
@Profile("mysql")
public class QuizImageStore {
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private final Path root;
    public QuizImageStore(@Value("${quiz.images.directory:./data/quiz-images}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    public String save(long quizId, MultipartFile upload) {
        if (upload.isEmpty()) throw invalidImage();
        if (upload.getSize() > MAX_BYTES) throw new QuizFailure(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 2 MiB.");
        try {
            byte[] input;
            try (InputStream stream = upload.getInputStream()) { input = stream.readNBytes((int) MAX_BYTES + 1); }
            if (input.length > MAX_BYTES) throw new QuizFailure(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 2 MiB.");
            BufferedImage decoded;
            try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw invalidImage();
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 4_000_000) throw invalidImage();
                    decoded = reader.read(0);
                } catch (IOException invalid) { throw invalidImage(); }
                finally { reader.dispose(); }
            }
            // Canonical PNG strips source metadata/extra bytes; JPEG uses the same stable content format.
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(decoded, "png", output)) throw invalidImage();
            byte[] bytes = output.toByteArray();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Path target = path(quizId, "sha256:" + hash);
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                // Publish a complete file without replacement. Duplicate upload must never overwrite a blob.
                try { Files.createLink(target, temporary); }
                catch (FileAlreadyExistsException duplicate) { /* Content-addressed duplicate. */ }
                if (!MessageDigest.isEqual(bytes, Files.readAllBytes(target))) {
                    throw new QuizFailure(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UNAVAILABLE", "Ảnh lưu trữ không toàn vẹn.");
                }
            } finally { Files.deleteIfExists(temporary); }
            return "sha256:" + hash;
        } catch (QuizFailure failure) { throw failure; }
        catch (IOException | UnsupportedOperationException failure) {
            throw new QuizFailure(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UNAVAILABLE", "Không thể lưu ảnh.");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 required", impossible); }
    }
    private Path path(long quizId, String reference) {
        if (quizId <= 0 || reference == null || !reference.matches("sha256:[0-9a-f]{64}")) throw invalidImage();
        return root.resolve(Long.toString(quizId)).resolve(reference.substring(7) + ".png");
    }
    public void requireExists(long quizId, String reference) {
        if (reference != null && !Files.isRegularFile(path(quizId, reference))) {
            throw new QuizFailure(HttpStatus.BAD_REQUEST, "IMAGE_NOT_FOUND", "Ảnh chưa được upload cho Quiz này.");
        }
    }
    public byte[] read(long quizId, String reference) {
        Path path = path(quizId, reference);
        if (!Files.isRegularFile(path)) throw new QuizFailure(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Không tìm thấy ảnh.");
        try {
            byte[] bytes = Files.readAllBytes(path);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(reference.substring(7))) throw new IOException("Integrity mismatch");
            return bytes;
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new QuizFailure(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_UNAVAILABLE", "Không thể đọc ảnh.");
        }
    }
    private QuizFailure invalidImage() {
        return new QuizFailure(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Ảnh phải là PNG/JPEG hợp lệ, mỗi cạnh tối đa4096 và tối đa4 triệu pixel.");
    }
}
