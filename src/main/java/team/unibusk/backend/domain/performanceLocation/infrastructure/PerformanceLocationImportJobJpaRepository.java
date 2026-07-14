package team.unibusk.backend.domain.performanceLocation.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import java.util.*;
public interface PerformanceLocationImportJobJpaRepository extends JpaRepository<PerformanceLocationImportJob, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PerformanceLocationImportJob> findFirstByStatusInOrderById(List<PerformanceLocationImportJobStatus> statuses);
    List<PerformanceLocationImportJob> findByStatusInAndLeaseExpiresAtBefore(List<PerformanceLocationImportJobStatus> statuses, java.time.LocalDateTime now);
}
