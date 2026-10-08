package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import com.enterprise.openfinance.atmdirectory.application.AtmDirectoryService;
import com.enterprise.openfinance.atmdirectory.domain.port.out.AtmDirectoryPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Wires the snapshot-serving application service. In a web run the snapshot is
 * refreshed every {@code atm-directory.snapshot.refresh-interval}; a migrate-only run
 * (web-application-type=none) never schedules a refresh.
 */
@Configuration(proxyBeanMethods = false)
public class AtmDirectoryConfiguration {

    @Bean
    AtmDirectoryService atmDirectoryService(AtmDirectoryPort port,
                                            @Value("${atm-directory.snapshot.max-age:PT10M}") Duration maxAge) {
        return new AtmDirectoryService(port, Clock.systemUTC(), maxAge);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnWebApplication
    static class SnapshotRefresh {

        private static final Logger LOG = LoggerFactory.getLogger(SnapshotRefresh.class);

        private final AtmDirectoryService service;

        SnapshotRefresh(AtmDirectoryService service, ObjectProvider<MeterRegistry> registry) {
            this.service = service;
            registry.ifAvailable(meters -> Gauge.builder("atm.directory.snapshot.age", service,
                    s -> s.snapshotLoadedAt()
                        .map(at -> Duration.between(at, Clock.systemUTC().instant()).toMillis() / 1000.0)
                        .orElse(Double.NaN))
                .baseUnit("seconds")
                .description("Age of the in-process ATM directory snapshot")
                .register(meters));
        }

        @Scheduled(fixedDelayString = "${atm-directory.snapshot.refresh-interval:PT30S}")
        void refresh() {
            try {
                service.refresh();
            } catch (RuntimeException ex) {
                // The previous snapshot keeps serving until it is older than max-age.
                LOG.warn("ATM directory snapshot refresh failed: {}", ex.getClass().getSimpleName());
            }
        }
    }
}
