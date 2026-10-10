package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round 5: under the aws profile the service refuses to start unless the reader (DB_URL)
 * and writer (FLYWAY_URL) JDBC URLs verify Aurora's certificate against the mounted RDS
 * CA bundle. Tests and local runs have no aws profile and still start.
 */
class AwsTransportSecurityConfigurationTest {

    private static final String GOOD = "jdbc:postgresql://aurora:5432/db_of_atm_directory_dev"
            + "?sslmode=verify-full&sslrootcert=/etc/fintechbankx/rds-ca/global-bundle.pem";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AwsTransportSecurityConfiguration.class);

    private ApplicationContextRunner aws(String... properties) {
        return runner.withPropertyValues("spring.profiles.active=aws", "spring.datasource.url=" + GOOD)
                .withPropertyValues(properties);
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> startupFailure(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        return assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsUnderTheAwsProfileWithVerifiedJdbcTls() {
        aws().run(context -> assertThat(context).hasNotFailed().hasBean("awsTransportSecurityAssertion"));
        aws("spring.flyway.url=" + GOOD.replace("aurora:", "aurora-writer:"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void doesNothingWithoutTheAwsProfile() {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/db_of_atm_directory_local")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("awsTransportSecurityAssertion"));
    }

    @Test
    void refusesAReaderUrlWithAWeakerTrailingSslmode() {
        aws("spring.datasource.url=" + GOOD + "&sslmode=disable")
                .run(context -> startupFailure(context)
                        .hasMessageContaining("spring.datasource.url must be")
                        .hasMessageContaining("sslmode=disable is not verify-full"));
    }

    @Test
    void refusesASecondSslrootcertAndANonValidatingFactory() {
        aws("spring.datasource.url=" + GOOD + "&sslrootcert=/tmp/global-bundle.pem")
                .run(context -> startupFailure(context)
                        .hasMessageContaining("sslrootcert is not the mounted RDS CA bundle"));
        aws("spring.datasource.url=" + GOOD + "&sslfactory=org.postgresql.ssl.NonValidatingFactory")
                .run(context -> startupFailure(context).hasMessageContaining("sslfactory is refused"));
    }

    @Test
    void refusesAWeakWriterUrl() {
        aws("spring.flyway.url=" + GOOD + "&sslmode=require")
                .run(context -> startupFailure(context)
                        .hasMessageContaining("spring.flyway.url must be")
                        .hasMessageContaining("sslmode=require is not verify-full"));
    }

    @Test
    void refusesAnRdsCaBundleOtherThanTheConfiguredOneAndTlsDriverProperties() {
        aws("openfinance.transport-security.database-ca-bundle=/etc/other/ca.pem")
                .run(context -> startupFailure(context)
                        .hasMessageContaining("sslrootcert=/etc/other/ca.pem")
                        .hasMessageContaining("sslrootcert is not the mounted RDS CA bundle"));
        aws("spring.datasource.hikari.data-source-properties.sslfactory=org.postgresql.ssl.NonValidatingFactory")
                .run(context -> startupFailure(context).hasMessageContaining("sslfactory is refused"));
    }

    @Test
    void theChartProfileWithTheShippedConfigurationStartsOnlyWithVerifiedUrls() {
        // application.yml as the chart runs it: spring.flyway.url = FLYWAY_URL, else DB_URL.
        ApplicationContextRunner shipped = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AwsTransportSecurityConfiguration.class)
                .withPropertyValues("spring.profiles.active=aws");
        shipped.withPropertyValues("DB_URL=" + GOOD).run(context -> assertThat(context).hasNotFailed());
        shipped.withPropertyValues("DB_URL=" + GOOD, "FLYWAY_URL=" + GOOD.replace("aurora:", "aurora-writer:"))
                .run(context -> assertThat(context).hasNotFailed());
        shipped.withPropertyValues("DB_URL=" + GOOD, "FLYWAY_URL=" + GOOD + "&sslmode=disable")
                .run(context -> startupFailure(context).hasMessageContaining("spring.flyway.url must be"));
        shipped.run(context -> startupFailure(context).hasMessageContaining("spring.datasource.url must be"));
    }
}
