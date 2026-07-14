package team.unibusk.backend.domain.performanceLocation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.unibusk.backend.domain.performanceLocation.domain.PerformanceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PerformanceLocationJpaRepository extends JpaRepository<PerformanceLocation, Long> {

    // 키워드로 performanceLocation 테이블의 name, address 칼럼에 keyword가 있는지 조회
    @Query("SELECT p FROM PerformanceLocation p " +
            "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<PerformanceLocation> searchByNameOrAddress(@Param("keyword") String keyword, Pageable pageable);

    // 이름 일치하는 데이터가 있는지 확인
    Optional<PerformanceLocation> findByName(String name);

    List<PerformanceLocation> findByNameIn(java.util.Collection<String> names);


    List<PerformanceLocation> findByIdIn(Set<Long> ids);

}
