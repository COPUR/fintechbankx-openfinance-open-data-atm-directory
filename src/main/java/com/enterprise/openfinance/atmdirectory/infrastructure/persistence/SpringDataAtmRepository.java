package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Read-only repository; the schema comes from the connection (hikari schema). */
@Transactional(readOnly = true)
interface SpringDataAtmRepository extends Repository<AtmJpaEntity, String> {

    List<AtmJpaEntity> findAllByOrderByAtmIdAsc();

    /** Served by the GiST index ix_atm_location on point(longitude, latitude). */
    @Query(value = """
        select * from atm
        where point(longitude, latitude) <@ box(point(:minLon, :minLat), point(:maxLon, :maxLat))
        order by atm_id
        """, nativeQuery = true)
    List<AtmJpaEntity> findInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                 @Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
