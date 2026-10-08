package com.enterprise.openfinance.atmdirectory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private static final String ROOT = "com.enterprise.openfinance.atmdirectory";

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
    }

    @Test
    void domainDependsOnNothingOutsideTheDomain() {
        noClasses()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                ROOT + ".application..",
                ROOT + ".infrastructure..",
                "org.springframework..",
                "org.springframework.data..",
                "jakarta.persistence..",
                "org.hibernate..",
                "com.fasterxml..",
                "org.apache.kafka..")
            .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        noClasses()
            .that().resideInAPackage(ROOT + ".application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(ROOT + ".infrastructure..", "jakarta.persistence..", "org.springframework.data..")
            .check(classes);
    }
}
