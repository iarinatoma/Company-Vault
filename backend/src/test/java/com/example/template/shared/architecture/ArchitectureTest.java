package com.example.template.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;

/**
 * ArchUnit enforces CLAUDE.md. A violated rule is a build failure, not a review comment.
 * When the document gains a mechanically checkable rule, the ArchUnit rule lands in the same
 * change.
 */
@AnalyzeClasses(
        packages = "com.example.template",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("unused") // the @ArchTest fields are discovered reflectively by ArchUnit's JUnit engine
class ArchitectureTest {

    private static final String[] SHARED_AND_INFRA = {"com.example.template.shared..", "com.example.template.infra.."};

    @ArchTest
    static final ArchRule feature_slices_follow_the_standard_sub_package_layout = classes()
            .that()
            .resideInAPackage("com.example.template.(*)..")
            .and()
            .resideOutsideOfPackages(SHARED_AND_INFRA)
            .should()
            .resideInAnyPackage(
                    "com.example.template.*.api",
                    "com.example.template.*.dto",
                    "com.example.template.*.model",
                    "com.example.template.*.event",
                    "com.example.template.*.repository",
                    "com.example.template.*.service",
                    "com.example.template.*.mapper")
            .because("every feature is a vertical slice with the same sub-packages");

    @ArchTest
    static final ArchRule entities_never_reference_entities_across_a_feature_boundary = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should(onlyDependOnEntitiesOfTheSameFeature())
            .because("cross-feature references are plain UUID columns; the FK lives in Liquibase");

    @ArchTest
    static final ArchRule controllers_depend_only_on_services_and_dtos = classes()
            .that()
            .resideInAPackage("..api")
            .and()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "com.example.template.*.service",
                    "com.example.template.*.dto",
                    "com.example.template.*.api",
                    "com.example.template.shared..",
                    "java..",
                    "jakarta..",
                    "org.jspecify..",
                    "org.springframework..")
            .because("controllers hold no logic beyond delegation");

    @ArchTest
    static final ArchRule controllers_never_map_and_never_see_entities = noClasses()
            .that()
            .resideInAPackage("..api")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.example.template.*.mapper", "com.example.template.*.model")
            .because("a controller sends DTOs to the service and gets DTOs back — "
                    + "mapping happens inside the service, entities never leave it");

    @ArchTest
    static final ArchRule response_status_exception_only_in_shared_error = noClasses()
            .that()
            .resideOutsideOfPackage("com.example.template.shared.error")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.server.ResponseStatusException")
            .because("business errors are ApiException with an ErrorCode, translated centrally");

    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule no_java_util_date = noClasses()
            .that()
            .doNotHaveFullyQualifiedName("com.example.template.shared.security.JwtService")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.Date")
            .because("time is Instant; JwtService is the single sanctioned exception (JJWT API)");

    @ArchTest
    static final ArchRule entities_extend_base_entity = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .beAssignableTo("com.example.template.shared.model.BaseEntity")
            .because("BaseEntity owns id, audit timestamps, optimistic locking and equality");

    private static ArchCondition<JavaClass> onlyDependOnEntitiesOfTheSameFeature() {
        return new ArchCondition<>("only depend on entities of the same feature") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String ownFeature = featureOf(entity);
                entity.getDirectDependenciesFromSelf().stream()
                        .map(Dependency::getTargetClass)
                        .filter(target -> target.isAnnotatedWith(Entity.class))
                        .filter(target -> !featureOf(target).equals(ownFeature))
                        .forEach(target -> events.add(SimpleConditionEvent.violated(
                                entity,
                                entity.getName() + " references entity " + target.getName()
                                        + " across a feature boundary")));
            }
        };
    }

    private static String featureOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = "com.example.template.";
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot == -1 ? rest : rest.substring(0, dot);
    }
}
