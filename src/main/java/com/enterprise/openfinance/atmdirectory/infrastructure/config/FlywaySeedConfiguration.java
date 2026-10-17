package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import java.util.Arrays;
import java.util.stream.Stream;
import org.flywaydb.core.api.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the sample ATMs in classpath:db/seed to the Flyway run only when
 * ATM_DIRECTORY_SEED_ENABLED=true (dev, CI). Never on by default: the real
 * network is loaded with db/import/import-atms.sh.
 */
@Configuration(proxyBeanMethods = false)
public class FlywaySeedConfiguration {

    static final String SEED_LOCATION = "classpath:db/seed";

    @Bean
    FlywayConfigurationCustomizer atmSeedLocation(@Value("${atm-directory.seed.enabled:false}") boolean seedEnabled) {
        return configuration -> {
            if (!seedEnabled) {
                return;
            }
            String[] locations = Stream.concat(
                    Arrays.stream(configuration.getLocations()).map(Location::getDescriptor),
                    Stream.of(SEED_LOCATION))
                .distinct()
                .toArray(String[]::new);
            configuration.locations(locations);
        };
    }
}
