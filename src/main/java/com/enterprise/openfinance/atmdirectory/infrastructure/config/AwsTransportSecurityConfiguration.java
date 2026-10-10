package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Startup TLS assertion (guardrail 4a, single form: on by default). It runs in every
 * context that is not explicitly local or test, the service container and the migrate
 * init container alike: fintechbankx.tls.enforce is true in application.yml (and when
 * unset), and only the local profile (application-local.yml) and the test resources
 * (application.properties) set it false. The chart never sets it and refuses the key,
 * every spring.profiles.* name and the JVM options that could carry them (vendored
 * platform guard). No profile or deployment marker is needed. It fails startup unless
 * spring.datasource.url, spring.datasource.hikari.jdbc-url (when set) and spring.flyway.url
 * (FLYWAY_URL, else the datasource URL) each carry exactly one sslmode=verify-full and
 * exactly one sslrootcert equal to openfinance.transport-security.database-ca-bundle
 * (JdbcTlsUrlPolicy), and no Hikari data-source property touches TLS. This service has no
 * Kafka client.
 * <p>
 * It runs as a BeanFactoryPostProcessor, before the DataSource or Flyway exists, so
 * nothing connects with weaker settings first; {@link HikariPoolTlsAssertion} then checks
 * the effective pool after binding, before it opens, under the same switch.
 */
@Configuration(proxyBeanMethods = false)
public class AwsTransportSecurityConfiguration {

    /** The only switch; true unless local or test configuration says otherwise. */
    public static final String ENFORCE_PROPERTY = "fintechbankx.tls.enforce";
    static final String CA_BUNDLE_PROPERTY = "openfinance.transport-security.database-ca-bundle";
    static final String DEFAULT_CA_BUNDLE = "/etc/fintechbankx/rds-ca/global-bundle.pem";
    private static final Logger log = LoggerFactory.getLogger(AwsTransportSecurityConfiguration.class);

    @Bean
    static BeanFactoryPostProcessor awsTransportSecurityAssertion(Environment environment) {
        return beanFactory -> enforce(environment);
    }

    @Bean
    static BeanPostProcessor awsHikariPoolTlsAssertion(Environment environment) {
        return new HikariPoolTlsAssertion(environment);
    }

    /** Checks unless {@value #ENFORCE_PROPERTY} is false. */
    static void enforce(Environment environment) {
        if (!enforced(environment)) {
            log.warn("{} is false: the startup TLS assertions are off (local runs and tests only)", ENFORCE_PROPERTY);
            return;
        }
        check(environment);
    }

    /** True unless the property says false; an unreadable value fails the start. */
    static boolean enforced(Environment environment) {
        return environment.getProperty(ENFORCE_PROPERTY, Boolean.class, true);
    }

    static String caBundle(Environment environment) {
        return Binder.get(environment).bind(CA_BUNDLE_PROPERTY, String.class).orElse(DEFAULT_CA_BUNDLE);
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
