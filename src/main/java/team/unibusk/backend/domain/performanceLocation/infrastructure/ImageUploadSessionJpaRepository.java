package team.unibusk.backend.domain.performanceLocation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadSession;

public interface ImageUploadSessionJpaRepository extends JpaRepository<ImageUploadSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<ImageUploadSession> findWithLockById(Long id);
}
