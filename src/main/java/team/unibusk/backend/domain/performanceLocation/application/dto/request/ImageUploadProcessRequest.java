package team.unibusk.backend.domain.performanceLocation.application.dto.request;

import java.util.List;

public record ImageUploadProcessRequest(
        List<Long> itemIds
) {
}
