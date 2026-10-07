package com.devcool.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Executable form of the layer rules in {@code .claude/rules/hexagonal.md} and the leaks catalogued
 * in {@code docs/learning/03-hexagonal-architecture.md}. ArchUnit reads bytecode, so static utility
 * calls count as dependencies even though they never show up in a constructor.
 */
@AnalyzeClasses(packages = "com.devcool", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  private static final String DOMAIN = "com.devcool.domain..";
  private static final String APPLICATION = "com.devcool.application..";
  private static final String ADAPTERS = "com.devcool.adapters..";
  private static final String ADAPTERS_IN = "com.devcool.adapters.in..";
  private static final String ADAPTERS_OUT = "com.devcool.adapters.out..";

  @ArchTest
  static final ArchRule domainDependsOnNoFramework =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "software.amazon..",
              "com.fasterxml.jackson..")
          .because("a port's types are its contract; a framework type ties the use case to it");

  @ArchTest
  static final ArchRule domainDependsOnNoOuterLayer =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(APPLICATION, ADAPTERS)
          .because("dependencies point inward: adapters -> application -> domain");

  @ArchTest
  static final ArchRule applicationDependsOnNoAdapter =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat(
              resideInAnyPackage(ADAPTERS, "software.amazon..")
                  .or(simpleNameEndingWith("Repository"))
                  .or(simpleNameEndingWith("Entity")))
          .because("services reach the outside world only through outbound ports");

  @ArchTest
  static final ArchRule applicationUsesOnlySpringStereotypesAndTransactions =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat(
              resideInAPackage("org.springframework..")
                  .and(
                      not(
                          resideInAnyPackage(
                              "org.springframework.stereotype..",
                              "org.springframework.transaction.."))))
          .because(
              "services may be Spring beans with @Transactional, but must not handle web,"
                  + " security or Spring AI types");

  @ArchTest
  static final ArchRule channelServicesDoNotUseAuthPorts =
      noClasses()
          .that()
          .resideInAPackage("com.devcool.application.service.channel..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.devcool.domain.auth..")
          .because("one concept, one port: channels load users through UserPort");

  @ArchTest
  static final ArchRule inboundAdaptersDoNotDependOnOutboundAdapters =
      noClasses()
          .that()
          .resideInAPackage(ADAPTERS_IN)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTERS_OUT)
          .because("adapters are leaves; shared state between two adapters means a missing port");

  @ArchTest
  static final ArchRule outboundAdaptersDoNotDependOnInboundAdapters =
      noClasses()
          .that()
          .resideInAPackage(ADAPTERS_OUT)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTERS_IN)
          .because("adapters are leaves; shared state between two adapters means a missing port");
}
