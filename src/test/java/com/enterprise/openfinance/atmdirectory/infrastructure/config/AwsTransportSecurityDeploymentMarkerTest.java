package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round 8: the chart renders FBX_DEPLOYED=true on the migrate init container and the service
 * container (never from values). With it the assertion runs whatever the profiles, and startup
 * fails unless aws is active and no local or test profile is. Without it behaviour is unchanged.
 */
class AwsTransportSecurityDeploymentMarkerTest {

    private static final String GOOD = "jdbc:postgresql://aurora:5432/db_of_atm_directory_dev"
            + "?sslmode=verify-full&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem";
    private static final Function<String, String> DEPLOYED = name -> "FBX_DEPLOYED".equals(name) ? "true" : null;
    private static final Function<String, String> NOT_DEPLOYED = name -> null;

    private static MockEnvironment environment(String profiles, String... properties) {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", GOOD);
        for (String property : properties) {
            int equals = property.indexOf('=');
            environment.setProperty(property.substring(0, equals), property.substring(equals + 1));
        }
        environment.setActiveProfiles(profiles.isEmpty() ? new String[0] : profiles.split(","));
        return environment;
    }

    @Test
    void withTheMarkerTheChartProfileWithVerifiedTlsStarts() {
        assertThatCode(() -> AwsTransportSecurityConfiguration.enforce(environment("aws"), DEPLOYED))
                .doesNotThrowAnyException();
        assertThatCode(() -> AwsTransportSecurityConfiguration.enforce(
                environment("aws", "spring.flyway.url=" + GOOD.replace("aurora:", "aurora-writer:")), DEPLOYED))
                .doesNotThrowAnyException();
    }

    @Test
    void withTheMarkerStartupFailsWithoutTheAwsProfile() {
        for (String profiles : List.of("", "default", "kafka-msk")) {
            assertThatThrownBy(() -> AwsTransportSecurityConfiguration.enforce(environment(profiles), DEPLOYED))
                    .as(profiles).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FBX_DEPLOYED is set")
                    .hasMessageContaining("the aws profile is missing");
        }
    }

    @Test
    void withTheMarkerALocalOrTestProfileIsRefused() {
        for (String profiles : List.of("aws,local", "test,aws", "aws,LOCAL")) {
            assertThatThrownBy(() -> AwsTransportSecurityConfiguration.enforce(environment(profiles), DEPLOYED))
                    .as(profiles).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("local profile");
        }
    }

    @Test
    void withTheMarkerTheTlsChecksStillRun() {
        assertThatThrownBy(() -> AwsTransportSecurityConfiguration.enforce(
                environment("aws", "spring.flyway.url=" + GOOD + "&sslmode=disable"), DEPLOYED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.url must be")
                .hasMessageContaining("sslmode=disable is not verify-full");
    }

    @Test
    void anyMarkerValueEnforces() {
        assertThatThrownBy(() -> AwsTransportSecurityConfiguration.enforce(environment(""),
                name -> "FBX_DEPLOYED".equals(name) ? "" : null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the aws profile is missing");
    }

    @Test
    void withoutTheMarkerBehaviourIsUnchanged() {
        assertThatCode(() -> AwsTransportSecurityConfiguration.enforce(
                environment("", "spring.datasource.url=jdbc:postgresql://localhost:5432/x"), NOT_DEPLOYED))
                .doesNotThrowAnyException();
        assertThatCode(() -> AwsTransportSecurityConfiguration.enforce(environment("test"), NOT_DEPLOYED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AwsTransportSecurityConfiguration.enforce(
                environment("aws", "spring.datasource.url=jdbc:postgresql://localhost:5432/x"), NOT_DEPLOYED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url must be");
    }
}
