package team.unibusk.backend.domain.performanceLocation.application.dto.response;

import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItemStatus;

import java.time.LocalDateTime;

public record ImageUploadUrlResponse(
        Long itemId,
        String originalFileName,
        String objectKey,
        String uploadUrl,
        LocalDateTime expiresAt,
        ImageUploadItemStatus status
) {
}
