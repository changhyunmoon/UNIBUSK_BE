package team.unibusk.backend.domain.performanceLocation.application.dto.response;

import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadSessionStatus;

public record ImageUploadSessionStatusResponse(
        Long uploadSessionId,
        ImageUploadSessionStatus status,
        int totalCount,
        long pendingCount,
        long uploadingCount,
        long uploadedCount,
        long processingCount,
        long readyCount,
        long validationFailedCount,
        long optimizeFailedCount,
        long uploadFailedCount,
        long confirmedCount,
        long finalizeFailedCount
) {
}
