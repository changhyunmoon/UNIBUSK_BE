package team.unibusk.backend.domain.performanceLocation.application.dto.response;

import java.util.List;

public record ImageUploadUrlIssueResponse(
        Long uploadSessionId,
        List<ImageUploadUrlResponse> uploadUrls
) {
}
