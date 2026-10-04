package dev.snip.link;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    long countByShortLink(ShortLink link);

    @Query("select count(distinct c.visitorHash) from ClickEvent c where c.shortLink = :link")
    long countUniqueVisitors(ShortLink link);

    List<ClickEvent> findByShortLinkOrderByClickedAtDesc(ShortLink link, Pageable pageable);

    @Query("select c.country, count(c) from ClickEvent c where c.shortLink = :link group by c.country order by count(c) desc")
    List<Object[]> countCountries(ShortLink link);
}
