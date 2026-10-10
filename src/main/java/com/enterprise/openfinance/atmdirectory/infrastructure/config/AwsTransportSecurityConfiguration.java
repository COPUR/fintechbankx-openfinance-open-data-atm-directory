package com.enterprise.openfinance.atmdirectory.infrastructure.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Under the aws profile (the chart renders SPRING_PROFILES_ACTIVE=aws for the migrate
 * init container and the service container), startup fails unless
 * spring.datasource.url and spring.flyway.url (FLYWAY_URL, else the datasource URL) each
 * carry exactly one sslmode=verify-full and exactly one sslrootcert equal to
 * openfinance.transport-security.database-ca-bundle (JdbcTlsUrlPolicy), and no Hikari
 * data-source property touches TLS. This service has no Kafka client.
 * <p>
 * It runs as a BeanFactoryPostProcessor, before the DataSource or Flyway exists, so
 * nothing connects with weaker settings first. Tests and local runs do not activate the
 * aws profile.
 */
@Configuration(proxyBeanMethods = false)
@Profile(AwsTransportSecurityConfiguration.AWS_PROFILE)
public class AwsTransportSecurityConfiguration {

    static final String AWS_PROFILE = "aws";
    static final String CA_BUNDLE_PROPERTY = "openfinance.transport-security.database-ca-bundle";
    static final String DEFAULT_CA_BUNDLE = "/etc/fintechbankx/rds-ca/global-bundle.pem";

    @Bean
    static BeanFactoryPostProcessor awsTransportSecurityAssertion(Environment environment) {
        return beanFactory -> check(environment);
    }

    static void check(Environment environment) {
        // red skeleton: no check yet
    }
}
