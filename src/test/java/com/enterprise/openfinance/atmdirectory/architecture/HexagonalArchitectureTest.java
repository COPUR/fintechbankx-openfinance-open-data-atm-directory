package com.enterprise.openfinance.atmdirectory.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The four hexagonal rules of ADR-028 / FINTECHBANKX_SERVICE_GUARDRAILS, on production classes. */
class HexagonalArchitectureTest {

    private static final String ROOT = "com.enterprise.openfinance.atmdirectory";

    private static final DescribedPredicate<JavaClass> INBOUND_ADAPTERS =
        DescribedPredicate.describe("are REST controllers or Kafka listeners", type ->
            type.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                || type.getMethods().stream()
                    .anyMatch(method -> method.isAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")));

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
    }

    @Test
    void rule1DomainDependsOnNoOuterLayerOrFramework() {
        noClasses()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                ROOT + ".application..",
                ROOT + ".infrastructure..",
                "org.springframework..",
                "org.springframework.data..",
                "org.springframework.kafka..",
                "jakarta.persistence..",
                "org.hibernate..",
                "org.apache.kafka..",
                "com.mongodb..",
                "com.fasterxml..")
            .check(classes);
    }

    @Test
    void rule2ApplicationDependsOnNoInfrastructure() {
        noClasses()
            .that().resideInAPackage(ROOT + ".application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(ROOT + ".infrastructure..", "jakarta.persistence..", "org.springframework.data..")
            .check(classes);
    }

    @Test
    void rule3InboundAdaptersUseInboundPortsNotApplicationClasses() {
        noClasses()
            .that(INBOUND_ADAPTERS)
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".application..")
            .check(classes);
        classes()
            .that(INBOUND_ADAPTERS)
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".domain.port.in..")
            .check(classes);
    }

    @Test
    void rule4OutboundPortImplementationsLiveInInfrastructure() {
        classes()
            .that().implement(resideInAPackage(ROOT + ".domain.port.out.."))
            .should().resideInAPackage(ROOT + ".infrastructure..")
            .check(classes);
    }

    @Test
    void infrastructureUsesOnlyTheAgreedTechnologyPackages() {
        classes()
            .that().resideInAPackage(ROOT + ".infrastructure..")
            .should().resideInAnyPackage(
                ROOT + ".infrastructure.web..",
                ROOT + ".infrastructure.persistence..",
                ROOT + ".infrastructure.config..")
            .check(classes);
    }
}
