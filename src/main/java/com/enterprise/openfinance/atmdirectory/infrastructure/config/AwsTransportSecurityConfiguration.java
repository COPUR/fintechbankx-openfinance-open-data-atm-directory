package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Startup TLS assertion. It runs under the aws profile (the chart renders
 * SPRING_PROFILES_ACTIVE=aws for the migrate init container and the service container) and
 * whenever the chart-owned deployment marker FBX_DEPLOYED is in the process environment
 * (the chart renders it directly on both containers, never from values, and refuses
 * config.FBX_DEPLOYED), whatever the profiles. With the marker, startup fails unless the
 * aws profile is active and no local or test profile is. Then it fails unless
 * spring.datasource.url, spring.datasource.hikari.jdbc-url (when set) and spring.flyway.url
 * (FLYWAY_URL, else the datasource URL) each carry exactly one sslmode=verify-full and
 * exactly one sslrootcert equal to openfinance.transport-security.database-ca-bundle
 * (JdbcTlsUrlPolicy), and no Hikari data-source property touches TLS. This service has no
 * Kafka client.
 * <p>
 * It runs as a BeanFactoryPostProcessor, before the DataSource or Flyway exists, so
 * nothing connects with weaker settings first; {@link HikariPoolTlsAssertion} then checks
 * the effective pool after binding, before it opens. Without the marker and the aws
 * profile (tests, local runs) neither does anything.
 */
@Configuration(proxyBeanMethods = false)
public class AwsTransportSecurityConfiguration {

    static final String AWS_PROFILE = "aws";
    static final String CA_BUNDLE_PROPERTY = "openfinance.transport-security.database-ca-bundle";
    static final String DEFAULT_CA_BUNDLE = "/etc/fintechbankx/rds-ca/global-bundle.pem";

    /** Rendered by the chart on every container that runs this assertion; read from the process environment only. */
    static final String DEPLOYED_MARKER = "FBX_DEPLOYED";
    /** Profiles for local runs and tests; never active in a deployment. */
    static final Set<String> LOCAL_PROFILES = Set.of("local", "test");

    @Bean
    static BeanFactoryPostProcessor awsTransportSecurityAssertion(Environment environment) {
        return beanFactory -> enforce(environment, System::getenv);
    }

    @Bean
    static BeanPostProcessor awsHikariPoolTlsAssertion(Environment environment) {
        return new HikariPoolTlsAssertion(environment, System::getenv);
    }

    /**
     * With the deployment marker in {@code processEnvironment}: requires the deployed profiles,
     * then checks. Without it: checks under the aws profile only (unchanged).
     */
    static void enforce(Environment environment, Function<String, String> processEnvironment) {
        if (isDeployed(processEnvironment)) {
            requireDeployedProfiles(environment);
        }
        if (enforced(environment, processEnvironment)) {
            check(environment);
        }
    }

    /** Any value counts: the chart renders "true", and values can neither set nor clear it. */
    static boolean isDeployed(Function<String, String> processEnvironment) {
        return processEnvironment.apply(DEPLOYED_MARKER) != null;
    }

    static boolean enforced(Environment environment, Function<String, String> processEnvironment) {
        return isDeployed(processEnvironment) || environment.acceptsProfiles(Profiles.of(AWS_PROFILE));
    }

    static String caBundle(Environment environment) {
        return Binder.get(environment).bind(CA_BUNDLE_PROPERTY, String.class).orElse(DEFAULT_CA_BUNDLE);
    }

    static void requireDeployedProfiles(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        String hint = DEPLOYED_MARKER + " is set (chart-rendered deployment), so the active profiles must include "
                + AWS_PROFILE + " and no local profile " + LOCAL_PROFILES + "; active: " + active;
        if (!active.contains(AWS_PROFILE)) {
            throw new IllegalStateException(hint + " (the " + AWS_PROFILE + " profile is missing)");
        }
        active.stream().filter(profile -> LOCAL_PROFILES.contains(profile.toLowerCase(Locale.ROOT))).findFirst()
                .ifPresent(profile -> {
                    throw new IllegalStateException(hint + " (local profile " + profile + " is active)");
                });
    }

    static void check(Environment environment) {
        Binder binder = Binder.get(environment);
        String caBundle = caBundle(environment);
        JdbcTlsUrlPolicy.requireVerifiedTls("spring.datasource.url",
                binder.bind("spring.datasource.url", String.class).orElse(null), caBundle);
        // Bound onto the HikariDataSource after spring.datasource.url, so it would replace the checked URL.
        binder.bind("spring.datasource.hikari.jdbc-url", String.class)
                .ifBound(url -> JdbcTlsUrlPolicy.requireVerifiedTls("spring.datasource.hikari.jdbc-url", url, caBundle));
        binder.bind("spring.flyway.url", String.class)
                .ifBound(url -> JdbcTlsUrlPolicy.requireVerifiedTls("spring.flyway.url", url, caBundle));
        JdbcTlsUrlPolicy.requireNoTlsDriverProperties("spring.datasource.hikari.data-source-properties",
                binder.bind("spring.datasource.hikari.data-source-properties",
                        Bindable.mapOf(String.class, String.class)).orElse(Map.of()));
    }
}
