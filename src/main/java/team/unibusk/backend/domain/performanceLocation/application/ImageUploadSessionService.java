package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadFailRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadFileRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadProcessRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadSessionCreateRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadUrlIssueRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadItemResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadSessionCreateResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadSessionStatusResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadUrlIssueResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadUrlResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.RejectedImageFileResponse;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItem;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItemStatus;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadSession;
import team.unibusk.backend.domain.performanceLocation.infrastructure.ImageUploadItemJpaRepository;
import team.unibusk.backend.domain.performanceLocation.infrastructure.ImageUploadSessionJpaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Transactional
public class ImageUploadSessionService {

    private static final int MAX_FILES_PER_REQUEST = 1000;
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L;
    private static final long MAX_SESSION_SIZE = 100L * 1024 * 1024 * 1024;
    private static final long SESSION_EXPIRE_HOURS = 24;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageUploadSessionJpaRepository sessionRepository;
    private final ImageUploadItemJpaRepository itemRepository;
    private final ImageUploadStorageService storageService;

    /*
        ImageUploadSessionCreateResponse의 흐름
        // 1. request.files() null/empty 검증
        // 2. 1000개 초과 검증
        // 3. 파일별 검증해서 accepted/rejected 분리
        // 4. accepted 기준 total count/size 계산
        // 5. session 저장
        // 6. session id 기반 rawObjectKey 생성
        // 7. item saveAll
        // 8. response 반환
     */
    public ImageUploadSessionCreateResponse createSession(ImageUploadSessionCreateRequest request) {
        validateRequest(request);

        List<ImageUploadFileRequest> acceptedFiles = new ArrayList<>();
        List<RejectedImageFileResponse> rejectedFiles = new ArrayList<>();
        Set<String> seenFileNames = new HashSet<>();

        for (ImageUploadFileRequest file : request.files()) {
            String rejectedReason = validateFile(file, seenFileNames);

            if (rejectedReason != null) {
                rejectedFiles.add(new RejectedImageFileResponse(
                        file == null ? null : file.originalFileName(),
                        rejectedReason
                ));
                continue;
            }

            acceptedFiles.add(file);
        }

        if (acceptedFiles.isEmpty()) {
            throw new IllegalArgumentException("등록 가능한 이미지가 없습니다.");
        }

        long totalSize = acceptedFiles.stream()
                .mapToLong(ImageUploadFileRequest::fileSize)
                .sum();

        if (totalSize > MAX_SESSION_SIZE) {
            throw new IllegalArgumentException("세션당 이미지 총 용량은 최대 100GB까지 허용합니다.");
        }

        ImageUploadSession session = ImageUploadSession.builder()
                .totalItemCount(acceptedFiles.size())
                .totalSize(totalSize)
                .expiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRE_HOURS))
                .build();

        ImageUploadSession savedSession = sessionRepository.save(session);

        List<ImageUploadItem> items = acceptedFiles.stream()
                .map(file -> ImageUploadItem.builder()
                        .session(savedSession)
                        .originalFileName(file.originalFileName().trim())
                        .contentType(file.contentType())
                        .fileSize(file.fileSize())
                        .rawObjectKey(createRawObjectKey(savedSession.getId(), file.originalFileName()))
                        .build())
                .toList();

        List<ImageUploadItem> savedItems = itemRepository.saveAll(items);

        List<ImageUploadItemResponse> itemResponses = savedItems.stream()
                .map(item -> new ImageUploadItemResponse(
                        item.getId(),
                        item.getOriginalFileName(),
                        item.getRawObjectKey(),
                        item.getStatus()
                ))
                .toList();

        return new ImageUploadSessionCreateResponse(
                savedSession.getId(),
                savedSession.getStatus(),
                savedItems.size(),
                rejectedFiles.size(),
                itemResponses,
                rejectedFiles
        );
    }

    public ImageUploadUrlIssueResponse issueUploadUrls(Long sessionId, ImageUploadUrlIssueRequest request) {
        ImageUploadSession session = getSession(sessionId);
        List<Long> itemIds = validateItemIds(request == null ? null : request.itemIds());
        List<ImageUploadItem> items = itemRepository.findBySessionIdAndIdIn(sessionId, itemIds);

        if (items.size() != itemIds.size()) {
            throw new IllegalArgumentException("세션에 속하지 않는 이미지가 포함되어 있습니다.");
        }

        session.startUploading();

        List<ImageUploadUrlResponse> responses = new ArrayList<>();
        for (ImageUploadItem item : items) {
            if (!item.canIssuePresignedUrl()) {
                markUploadFailedByIssueLimit(item);
                continue;
            }

            item.markPresignedUrlIssued();
            responses.add(new ImageUploadUrlResponse(
                    item.getId(),
                    item.getOriginalFileName(),
                    item.getRawObjectKey(),
                    storageService.createUploadUrl(sessionId, item),
                    LocalDateTime.now().plusMinutes(15),
                    item.getStatus()
            ));
        }
        refreshUploadedSession(session);

        return new ImageUploadUrlIssueResponse(sessionId, responses);
    }

    public void saveLocalUpload(Long sessionId, Long itemId, byte[] bytes) {
        ImageUploadItem item = getSessionItem(sessionId, itemId);
        if (!storageService.isLocalMode()) {
            throw new IllegalStateException("로컬 업로드 모드에서만 사용할 수 있습니다.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("이미지 바이트가 비어 있습니다.");
        }
        if (bytes.length > item.getFileSize()) {
            throw new IllegalArgumentException("등록한 파일 크기를 초과했습니다.");
        }
        item.validateUploadCompletable();

        storageService.saveLocalRaw(item, bytes);
        storageService.verifyRawUploaded(item);
        item.markUploaded();
        refreshUploadedSession(item.getSession());
    }

    public void markUploaded(Long sessionId, List<Long> itemIds) {
        List<Long> ids = validateItemIds(itemIds);
        List<ImageUploadItem> items = itemRepository.findBySessionIdAndIdIn(sessionId, ids);
        if (items.size() != ids.size()) {
            throw new IllegalArgumentException("세션에 속하지 않는 이미지가 포함되어 있습니다.");
        }

        items.forEach(ImageUploadItem::validateUploadCompletable);

        for (ImageUploadItem item : items) {
            storageService.verifyRawUploaded(item);
            item.markUploaded();
        }
        refreshUploadedSession(getSession(sessionId));
    }

    public void markUploadFailed(Long sessionId, ImageUploadFailRequest request) {
        List<Long> ids = validateItemIds(request == null ? null : request.itemIds());
        List<ImageUploadItem> items = itemRepository.findBySessionIdAndIdIn(sessionId, ids);
        if (items.size() != ids.size()) {
            throw new IllegalArgumentException("세션에 속하지 않는 이미지가 포함되어 있습니다.");
        }

        String reason = request.reason();
        String failureReason = truncateFailureReason(
                reason == null || reason.isBlank()
                        ? "이미지 업로드에 실패했습니다."
                        : reason
        );
        for (ImageUploadItem item : items) {
            item.markUploadFailed(failureReason);
        }
        refreshUploadedSession(getSession(sessionId));
    }

    public ImageUploadSessionStatusResponse process(Long sessionId, ImageUploadProcessRequest request) {
        ImageUploadSession session = getSession(sessionId);
        List<ImageUploadItem> items = getProcessTargets(sessionId, request);
        session.startProcessing();
        sessionRepository.flush();

        for (ImageUploadItem item : items) {
            if (itemRepository.markProcessingIfUploaded(sessionId, item.getId()) != 1) {
                continue;
            }

            ImageUploadItem processingItem = getSessionItem(sessionId, item.getId());
            try {
                String processedObjectKey = storageService.process(processingItem);
                itemRepository.markReadyIfProcessing(sessionId, processingItem.getId(), processedObjectKey);
            } catch (RuntimeException e) {
                itemRepository.markOptimizeFailedIfProcessing(
                        sessionId,
                        processingItem.getId(),
                        truncateFailureReason(e.getMessage())
                );
            }
        }

        markSessionReadyIfAllItemsProcessingFinished(getSession(sessionId));
        return getStatus(sessionId);
    }

    @Transactional(readOnly = true)
    public ImageUploadSessionStatusResponse getStatus(Long sessionId) {
        ImageUploadSession session = getSession(sessionId);
        return new ImageUploadSessionStatusResponse(
                session.getId(),
                session.getStatus(),
                session.getTotalItemCount(),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.PENDING),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.UPLOADING),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.UPLOADED),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.PROCESSING),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.READY),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.VALIDATION_FAILED),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.OPTIMIZE_FAILED),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.UPLOAD_FAILED),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.CONFIRMED),
                itemRepository.countBySessionIdAndStatus(sessionId, ImageUploadItemStatus.FINALIZE_FAILED)
        );
    }

    private void validateRequest(ImageUploadSessionCreateRequest request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new IllegalArgumentException("이미지 파일 목록은 필수입니다.");
        }

        if (request.files().size() > MAX_FILES_PER_REQUEST) {
            throw new IllegalArgumentException("요청당 이미지 파일은 최대 1,000개까지 등록할 수 있습니다.");
        }
    }

    private String validateFile(ImageUploadFileRequest file, Set<String> seenFileNames) {
        if (file == null) {
            return "파일 정보가 없습니다.";
        }

        String originalFileName = file.originalFileName();

        if (originalFileName == null || originalFileName.isBlank()) {
            return "파일명은 필수입니다.";
        }

        String trimmedFileName = originalFileName.trim();

        if (trimmedFileName.length() > 255) {
            return "파일명은 최대 255자까지 허용됩니다.";
        }

        String normalizedFileName = trimmedFileName.toLowerCase();

        if (!seenFileNames.add(normalizedFileName)) {
            return "같은 요청 안에서 중복된 파일명입니다.";
        }

        String extension = extractExtension(trimmedFileName);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return "허용되지 않는 확장자입니다.";
        }

        if (file.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.contentType())) {
            return "허용되지 않는 Content-Type입니다.";
        }

        if (file.fileSize() <= 0) {
            return "파일 크기는 0보다 커야 합니다.";
        }

        if (file.fileSize() > MAX_FILE_SIZE) {
            return "파일 크기는 최대 20MB까지 허용됩니다.";
        }

        return null;
    }

    private String createRawObjectKey(Long sessionId, String originalFileName) {
        String extension = extractExtension(originalFileName);

        return "temp/performance-location-imports/"
                + sessionId
                + "/raw/"
                + UUID.randomUUID()
                + "."
                + extension;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private ImageUploadSession getSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("업로드 세션을 찾을 수 없습니다."));
    }

    private ImageUploadItem getSessionItem(Long sessionId, Long itemId) {
        return itemRepository.findBySessionIdAndIdIn(sessionId, List.of(itemId)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("업로드 이미지를 찾을 수 없습니다."));
    }

    private List<Long> validateItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("itemIds는 필수입니다.");
        }
        if (itemIds.size() > MAX_FILES_PER_REQUEST) {
            throw new IllegalArgumentException("요청당 최대 1,000개까지 처리할 수 있습니다.");
        }
        return itemIds;
    }

    private List<ImageUploadItem> getProcessTargets(Long sessionId, ImageUploadProcessRequest request) {
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            return itemRepository.findBySessionId(sessionId).stream()
                    .filter(item -> item.getStatus() == ImageUploadItemStatus.UPLOADED)
                    .toList();
        }
        return itemRepository.findBySessionIdAndIdIn(sessionId, validateItemIds(request.itemIds()));
    }

    private String truncateFailureReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private void markUploadFailedByIssueLimit(ImageUploadItem item) {
        if (!item.canMarkUploadFailed() || !item.isPresignedUrlIssueLimitReached()) {
            throw new IllegalStateException("Presigned URL 발급이 불가능한 상태입니다.");
        }
        item.markUploadFailed("Presigned URL 발급 가능 횟수를 초과했습니다.");
    }

    private void refreshUploadedSession(ImageUploadSession session) {
        boolean hasUnfinishedUpload = itemRepository.findBySessionId(session.getId()).stream()
                .anyMatch(item -> item.getStatus() != ImageUploadItemStatus.UPLOADED
                        && item.getStatus() != ImageUploadItemStatus.UPLOAD_FAILED);
        if (!hasUnfinishedUpload) {
            session.markUploaded();
        }
    }

    private void markSessionReadyIfAllItemsProcessingFinished(ImageUploadSession session) {
        boolean allTerminal = itemRepository.findBySessionId(session.getId()).stream()
                .allMatch(item -> item.getStatus().isTerminal());
        if (allTerminal) {
            session.markReady();
        }
    }
}
