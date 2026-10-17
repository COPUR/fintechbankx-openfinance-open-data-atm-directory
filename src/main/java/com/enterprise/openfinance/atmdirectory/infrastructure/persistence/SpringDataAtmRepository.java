package com.enterprise.openfinance.atmdirectory.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only repository; the schema comes from the connection (hikari schema). */
@Transactional(readOnly = true)
interface SpringDataAtmRepository extends Repository<AtmJpaEntity, String> {

    /** Listed ATMs: every status except Withdrawn (AtmLocation.STATUS_WITHDRAWN). */
    @Query(value = "select * from atm where status <> 'Withdrawn' order by atm_id", nativeQuery = true)
    List<AtmJpaEntity> findListedOrderByAtmId();

}
