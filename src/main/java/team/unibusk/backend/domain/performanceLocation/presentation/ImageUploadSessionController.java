package team.unibusk.backend.domain.performanceLocation.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import team.unibusk.backend.domain.performanceLocation.application.ImageUploadSessionService;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadCompleteRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadFailRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadProcessRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadSessionCreateRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.request.ImageUploadUrlIssueRequest;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadSessionCreateResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadSessionStatusResponse;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.ImageUploadUrlIssueResponse;

@RestController
@RequiredArgsConstructor
public class ImageUploadSessionController {

    private final ImageUploadSessionService imageUploadSessionService;

    @PostMapping("/performance-locations/image-upload-sessions")
    public ResponseEntity<ImageUploadSessionCreateResponse> createSession(
            @RequestBody ImageUploadSessionCreateRequest request
    ) {
        ImageUploadSessionCreateResponse response = imageUploadSessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/performance-locations/image-upload-sessions/{sessionId}/upload-urls")
    public ResponseEntity<ImageUploadUrlIssueResponse> issueUploadUrls(
            @PathVariable Long sessionId,
            @RequestBody ImageUploadUrlIssueRequest request
    ) {
        ImageUploadUrlIssueResponse response = imageUploadSessionService.issueUploadUrls(sessionId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/performance-locations/image-upload-sessions/{sessionId}/items/{itemId}/local-upload")
    public ResponseEntity<Void> uploadLocalImage(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody byte[] bytes
    ) {
        imageUploadSessionService.saveLocalUpload(sessionId, itemId, bytes);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/performance-locations/image-upload-sessions/{sessionId}/uploaded")
    public ResponseEntity<Void> markUploaded(
            @PathVariable Long sessionId,
            @RequestBody ImageUploadCompleteRequest request
    ) {
        imageUploadSessionService.markUploaded(sessionId, request.itemIds());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/performance-locations/image-upload-sessions/{sessionId}/upload-failed")
    public ResponseEntity<Void> markUploadFailed(
            @PathVariable Long sessionId,
            @RequestBody ImageUploadFailRequest request
    ) {
        imageUploadSessionService.markUploadFailed(sessionId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/performance-locations/image-upload-sessions/{sessionId}/process")
    public ResponseEntity<ImageUploadSessionStatusResponse> process(
            @PathVariable Long sessionId,
            @RequestBody(required = false) ImageUploadProcessRequest request
    ) {
        ImageUploadSessionStatusResponse response = imageUploadSessionService.process(sessionId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/performance-locations/image-upload-sessions/{sessionId}")
    public ResponseEntity<ImageUploadSessionStatusResponse> getStatus(@PathVariable Long sessionId) {
        ImageUploadSessionStatusResponse response = imageUploadSessionService.getStatus(sessionId);
        return ResponseEntity.ok(response);
    }
}
