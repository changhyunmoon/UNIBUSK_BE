package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItem;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ImageUploadStorageService {

    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket:}")
    private String bucket;

    @Value("${cloud.aws.s3.public-url:https://%s.s3.amazonaws.com}")
    private String publicUrlFormat;

    @Value("${image-upload.storage-mode:local}")
    private String storageMode;

    @Value("${image-upload.local-root:${user.dir}/build/local-image-uploads}")
    private String localRoot;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public boolean isLocalMode() {
        return "local".equalsIgnoreCase(storageMode);
    }

    public String createUploadUrl(Long sessionId, ImageUploadItem item) {
        if (isLocalMode()) {
            return UriComponentsBuilder.fromUriString(publicBaseUrl)
                    .path("/performance-locations/image-upload-sessions/{sessionId}/items/{itemId}/local-upload")
                    .build(sessionId, item.getId())
                    .toString();
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(item.getRawObjectKey())
                .contentType(item.getContentType())
                .contentLength(item.getFileSize())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    public void saveLocalRaw(ImageUploadItem item, byte[] bytes) {
        Path target = resolveLocalPath(item.getRawObjectKey());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Local image save failed.", e);
        }
    }

    public void verifyRawUploaded(ImageUploadItem item) {
        if (isLocalMode()) {
            verifyLocalRawUploaded(item);
            return;
        }

        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(item.getRawObjectKey())
                .build();
        HeadObjectResponse response = s3Client.headObject(request);

        if (response.contentLength() != item.getFileSize()) {
            throw new IllegalStateException("Uploaded S3 object size does not match registered file size.");
        }

        String uploadedContentType = response.contentType();
        if (uploadedContentType == null || !uploadedContentType.equalsIgnoreCase(item.getContentType())) {
            throw new IllegalStateException("Uploaded S3 object content type does not match registered content type.");
        }
    }

    public String process(ImageUploadItem item) {
        String processedObjectKey = createProcessedObjectKey(item);
        if (isLocalMode()) {
            copyLocal(item.getRawObjectKey(), processedObjectKey);
        } else {
            copyS3(item.getRawObjectKey(), processedObjectKey);
            deleteS3(item.getRawObjectKey());
        }
        return processedObjectKey;
    }

    public String createFinalObjectKey(ImageUploadItem item) {
        String source = item.getProcessedObjectKey() != null ? item.getProcessedObjectKey() : item.getRawObjectKey();
        String extension = extractExtension(source);
        return "performanceLocations/" + item.getId() + "." + extension;
    }

    public String createPublicUrl(String objectKey) {
        if (isLocalMode()) {
            return UriComponentsBuilder.fromUriString(publicBaseUrl)
                    .path("/local-image-files/")
                    .path(objectKey)
                    .build()
                    .toString();
        }
        return String.format(publicUrlFormat, bucket) + "/" + encodeObjectKey(objectKey);
    }

    public void finalizeObject(ImageUploadItem item, String finalObjectKey) {
        if (isLocalMode()) {
            String source = item.getProcessedObjectKey() != null ? item.getProcessedObjectKey() : item.getRawObjectKey();
            copyLocal(source, finalObjectKey);
        } else {
            String source = item.getProcessedObjectKey() != null ? item.getProcessedObjectKey() : item.getRawObjectKey();
            copyS3(source, finalObjectKey);
        }
    }

    public void deleteObject(String objectKey) {
        if (isLocalMode()) {
            deleteLocal(objectKey);
            return;
        }
        deleteS3(objectKey);
    }

    private String createProcessedObjectKey(ImageUploadItem item) {
        String extension = extractExtension(item.getRawObjectKey());
        return "temp/performance-location-imports/"
                + item.getSession().getId()
                + "/processed/"
                + item.getId()
                + "."
                + extension;
    }

    private void copyLocal(String sourceObjectKey, String targetObjectKey) {
        Path source = resolveLocalPath(sourceObjectKey);
        Path target = resolveLocalPath(targetObjectKey);
        try {
            if (!Files.exists(source)) {
                throw new IllegalStateException("Uploaded local image does not exist.");
            }
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Local image processing failed.", e);
        }
    }

    private void deleteLocal(String objectKey) {
        Path target = resolveLocalPath(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Local image delete failed.", e);
        }
    }

    private void verifyLocalRawUploaded(ImageUploadItem item) {
        Path target = resolveLocalPath(item.getRawObjectKey());
        try {
            if (!Files.exists(target)) {
                throw new IllegalStateException("Uploaded local image does not exist.");
            }
            if (Files.size(target) != item.getFileSize()) {
                throw new IllegalStateException("Uploaded local image size does not match registered file size.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Local image verification failed.", e);
        }
    }

    private void copyS3(String sourceObjectKey, String targetObjectKey) {
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceObjectKey)
                .destinationBucket(bucket)
                .destinationKey(targetObjectKey)
                .build();
        s3Client.copyObject(request);
    }

    private void deleteS3(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
    }

    private Path resolveLocalPath(String objectKey) {
        Path root = Path.of(localRoot).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid image path.");
        }
        return target;
    }

    private String extractExtension(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == key.length() - 1) {
            return "jpg";
        }
        return key.substring(dotIndex + 1).toLowerCase();
    }

    private String encodeObjectKey(String objectKey) {
        return Arrays.stream(objectKey.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }
}
