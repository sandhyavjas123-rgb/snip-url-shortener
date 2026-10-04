package dev.snip.link;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    Optional<ShortLink> findByCode(String code);
    Optional<ShortLink> findFirstByDestinationUrlAndActiveTrueOrderByCreatedAtDesc(String destinationUrl);
    boolean existsByCode(String code);
    List<ShortLink> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ShortLink> findByActiveTrueAndExpiresAtBefore(java.time.Instant cutoff);
}

