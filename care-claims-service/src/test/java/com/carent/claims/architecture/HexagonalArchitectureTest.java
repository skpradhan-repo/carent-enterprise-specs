package com.carent.claims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;

/**
 * Architecture rules from specs/06-testing-and-quality.md plus PROJECT_RULES.md.
 *
 * <p>Domain rules are strict: they fail if the domain packages are empty, so a mistyped package or a
 * moved domain cannot make them pass vacuously. Rules for layers that do not exist yet (application,
 * adapters) still allow empty matches; remove the flag as each slice adds code.
 */
@AnalyzeClasses(packages = "com.carent.claims", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // 1. domain must not depend on Spring
    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    // 2. domain must not depend on JPA / Hibernate
    @ArchTest
    static final ArchRule domain_does_not_depend_on_persistence = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..");

    // PROJECT_RULES.md: domain must not use Kafka or web libraries either
    @ArchTest
    static final ArchRule domain_does_not_depend_on_kafka_or_web = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.apache.kafka..", "jakarta.servlet..", "jakarta.ws.rs..");

    // Strongest form of purity: domain depends only on the JDK and itself
    @ArchTest
    static final ArchRule domain_depends_only_on_jdk_and_itself = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");

    // Money rule: no double/float in the domain. dependOnClassesThat() cannot see primitives, so the
    // primitive and boxed types are checked separately on fields, return types and parameters.
    // Limitation: local variables are not visible to ArchUnit; code review covers those.
    @ArchTest
    static final ArchRule domain_has_no_floating_point_fields = noFields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().haveRawType(double.class)
            .orShould().haveRawType(float.class)
            .orShould().haveRawType(Double.class)
            .orShould().haveRawType(Float.class);

    @ArchTest
    static final ArchRule domain_has_no_floating_point_return_types = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().haveRawReturnType(double.class)
            .orShould().haveRawReturnType(float.class)
            .orShould().haveRawReturnType(Double.class)
            .orShould().haveRawReturnType(Float.class);

    @ArchTest
    static final ArchRule domain_has_no_floating_point_parameters = noCodeUnits()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().haveRawParameterTypes(new DescribedPredicate<List<JavaClass>>("contain double or float") {
                @Override
                public boolean test(List<JavaClass> parameters) {
                    return parameters.stream().anyMatch(p -> Set.of("double", "float", "java.lang.Double",
                            "java.lang.Float").contains(p.getName()));
                }
            });

    // 3. domain must not depend on adapters
    @ArchTest
    static final ArchRule domain_does_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    // 4. application must not depend on adapters
    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    // Application is framework-free: JDK + domain + itself only (wiring lives in the config package)
    @ArchTest
    static final ArchRule application_depends_only_on_jdk_domain_and_itself = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..domain..", "..application..");

    // JPA is an adapter concern: only the persistence adapter may touch it
    @ArchTest
    static final ArchRule jpa_is_confined_to_the_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..",
                    "org.springframework.data..");

    // Kafka is an adapter concern: only the messaging adapter (and the topic declaration in config) may use it
    @ArchTest
    static final ArchRule kafka_is_confined_to_the_messaging_adapter = noClasses()
            .that().resideOutsideOfPackages("..adapter.out.messaging..", "..config..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.kafka..", "org.apache.kafka..");

    // Outbound ports are interfaces (inbound ports also include the command record)
    @ArchTest
    static final ArchRule outbound_ports_are_interfaces = classes()
            .that().resideInAnyPackage("..application.port.out..")
            .should().beInterfaces();

    // Persistence internals stay package-private; only the adapter itself is exposed
    @ArchTest
    static final ArchRule persistence_internals_are_not_public = classes()
            .that().resideInAPackage("..adapter.out.persistence..")
            .and().haveSimpleNameNotEndingWith("Adapter")
            .should().notBePublic();

    // 5. controllers must not reach persistence directly; they go through inbound ports
    @ArchTest
    static final ArchRule web_adapter_does_not_depend_on_persistence_adapter = noClasses()
            .that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.out..", "..application.port.out..");

    // Web adapter reaches the application only via inbound ports (never application services directly)
    @ArchTest
    static final ArchRule web_adapter_uses_only_inbound_ports = noClasses()
            .that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..");

    // Only the web adapter may use Spring MVC / servlet / bean validation
    @ArchTest
    static final ArchRule web_framework_is_confined_to_the_web_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..",
                    "jakarta.validation..", "org.springframework.http..");

    // RFC 7807: HTTP status codes are produced only in the web adapter
    @ArchTest
    static final ArchRule http_status_is_confined_to_the_web_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.in.web..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.http.HttpStatus");

    // Domain exceptions are mapped to HTTP in exactly one class: the @RestControllerAdvice
    @ArchTest
    static final ArchRule exception_handlers_live_in_the_web_adapter = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestControllerAdvice.class)
            .or().areAnnotatedWith(org.springframework.web.bind.annotation.ControllerAdvice.class)
            .should().resideInAPackage("..adapter.in.web..");

    // Controllers are thin: they live in the web adapter and are package-visible only through the advice
    @ArchTest
    static final ArchRule rest_controllers_live_in_the_web_adapter = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("..adapter.in.web..")
            .andShould().haveSimpleNameEndingWith("Controller");

    // DTOs are a web concern: nothing outside the web adapter may use them
    @ArchTest
    static final ArchRule dtos_are_not_used_outside_the_web_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in.web.dto..");

    // Layer direction: Adapter -> Application -> Domain. The config package is the composition root: it may
    // use every layer but nothing may depend on it.
    @ArchTest
    static final ArchRule layer_direction_is_respected = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapter").definedBy("..adapter..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config");
}
