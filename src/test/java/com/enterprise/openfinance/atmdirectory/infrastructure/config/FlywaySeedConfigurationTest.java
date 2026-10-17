package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

class FlywaySeedConfigurationTest {

    private final FlywaySeedConfiguration configuration = new FlywaySeedConfiguration();

    @Test
    void seedLocationIsNotAddedByDefault() {
        FluentConfiguration flyway = new FluentConfiguration().locations("classpath:db/migration");

        configuration.atmSeedLocation(false).customize(flyway);

        assertThat(descriptors(flyway)).containsExactly("classpath:db/migration");
    }

    @Test
    void seedLocationIsAppendedWhenEnabled() {
        FluentConfiguration flyway = new FluentConfiguration().locations("classpath:db/migration");

        configuration.atmSeedLocation(true).customize(flyway);
        configuration.atmSeedLocation(true).customize(flyway);

        assertThat(descriptors(flyway)).containsExactly("classpath:db/migration", "classpath:db/seed");
    }

    private static String[] descriptors(FluentConfiguration flyway) {
        return Arrays.stream(flyway.getLocations()).map(Location::getDescriptor).toArray(String[]::new);
    }
}
