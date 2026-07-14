package team.unibusk.backend.domain.performanceLocation.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import java.time.LocalDateTime;
import java.util.*;
public interface PerformanceLocationImageFinalizeTaskJpaRepository extends JpaRepository<PerformanceLocationImageFinalizeTask, Long> {
    List<PerformanceLocationImageFinalizeTask> findByImportJobIdAndStatusIn(Long jobId, List<ImageFinalizeTaskStatus> statuses);
    long countByImportJobIdAndStatus(Long jobId, ImageFinalizeTaskStatus status);
    @org.springframework.data.jpa.repository.Query("select t from PerformanceLocationImageFinalizeTask t where t.status = :pending or (t.status = :retry and t.nextRetryAt <= :now) order by t.id")
    List<PerformanceLocationImageFinalizeTask> findDue(@org.springframework.data.repository.query.Param("pending") ImageFinalizeTaskStatus pending,
            @org.springframework.data.repository.query.Param("retry") ImageFinalizeTaskStatus retry,
            @org.springframework.data.repository.query.Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);
}
