package team.unibusk.backend.domain.performanceLocation.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import java.util.List;
public interface PerformanceLocationImportRowResultJpaRepository extends JpaRepository<PerformanceLocationImportRowResult, Long> {
    List<PerformanceLocationImportRowResult> findByImportJobIdOrderByRowNum(Long importJobId);
    boolean existsByImportJobIdAndRowNumAndStatus(Long jobId, int rowNum, PerformanceLocationImportRowStatus status);
}
