package com.carent.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.carent.claims.adapter.in.web.dto.ClaimResponse;
import com.carent.claims.adapter.in.web.dto.ClaimSubmissionRequest;
import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.ClaimStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * specs/openapi.yaml is the API contract. These tests read it and compare it with the code, so a drifting
 * DTO, enum, pattern or an invented endpoint fails the build.
 */
class OpenApiContractTest {

    private static final Map<String, Object> SPEC = loadSpec();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadSpec() {
        Path base = Path.of(System.getProperty("basedir", "."));
        Path spec = base.resolve("../specs/openapi.yaml").normalize();
        try (InputStream in = Files.newInputStream(spec)) {
            return new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + spec.toAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return (Map<String, Object>) node;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object node) {
        return (List<String>) node;
    }

    private static Map<String, Object> schema(String name) {
        return map(map(map(SPEC.get("components")).get("schemas")).get(name));
    }

    private static Set<String> propertyNames(String schemaName) {
        return new TreeSet<>(map(schema(schemaName).get("properties")).keySet());
    }

    private static Set<String> recordComponents(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toCollection(TreeSet::new));
    }

    // ---- endpoints -------------------------------------------------------------------------

    @Test
    void implemented_endpoints_are_exactly_the_ones_in_openapi() throws Exception {
        String serverUrl = map(list0(SPEC.get("servers"))).get("url").toString();
        String basePath = URI.create(serverUrl).getPath();

        Set<String> expected = new TreeSet<>();
        for (Map.Entry<String, Object> path : map(SPEC.get("paths")).entrySet()) {
            for (String verb : map(path.getValue()).keySet()) {
                expected.add(verb.toUpperCase() + " " + basePath + path.getKey());
            }
        }

        assertThat(scanImplementedEndpoints()).isEqualTo(expected);
    }

    private static Object list0(Object node) {
        return ((List<?>) node).get(0);
    }

    private static Set<String> scanImplementedEndpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> endpoints = new TreeSet<>();
        for (BeanDefinition controller : scanner.findCandidateComponents("com.carent.claims")) {
            Class<?> type = Class.forName(controller.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping != null) {
                    String path = mapping.path().length == 0 ? "" : mapping.path()[0];
                    endpoints.add(mapping.method()[0].name() + " " + prefix + path);
                }
            }
        }
        return endpoints;
    }

    // ---- request schema --------------------------------------------------------------------

    @Test
    void request_dto_has_exactly_the_openapi_properties() {
        assertThat(recordComponents(ClaimSubmissionRequest.class))
                .isEqualTo(propertyNames("ClaimSubmissionRequest"));
    }

    @Test
    void every_required_request_property_is_enforced_as_not_null_or_not_blank() throws Exception {
        for (String name : list(schema("ClaimSubmissionRequest").get("required"))) {
            Field field = ClaimSubmissionRequest.class.getDeclaredField(name);
            assertThat(field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class))
                    .as("%s must be validated as required", name).isTrue();
        }
    }

    @Test
    void money_pattern_and_id_min_length_match_openapi() throws Exception {
        String moneyPattern = map(schema("Money")).get("pattern").toString();
        Pattern pattern = ClaimSubmissionRequest.class.getDeclaredField("billedAmount").getAnnotation(Pattern.class);
        assertThat(pattern.regexp()).isEqualTo(moneyPattern);

        for (String id : List.of("memberId", "providerId")) {
            int minLength = (Integer) map(map(schema("ClaimSubmissionRequest").get("properties")).get(id))
                    .get("minLength");
            Size size = ClaimSubmissionRequest.class.getDeclaredField(id).getAnnotation(Size.class);
            assertThat(size.min()).as("%s minLength", id).isEqualTo(minLength);
        }
    }

    // ---- response schema -------------------------------------------------------------------

    @Test
    void response_dto_has_exactly_the_openapi_properties() {
        assertThat(recordComponents(ClaimResponse.class)).isEqualTo(propertyNames("ClaimResponse"));
    }

    // ---- enums -----------------------------------------------------------------------------

    @Test
    void benefit_category_enum_matches_openapi() {
        Set<String> expected = new TreeSet<>(list(schema("BenefitCategory").get("enum")));
        Set<String> actual = Arrays.stream(BenefitCategory.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void claim_status_enum_matches_openapi() {
        Set<String> expected = new TreeSet<>(list(schema("ClaimStatus").get("enum")));
        Set<String> actual = Arrays.stream(ClaimStatus.values()).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(actual).isEqualTo(expected);
    }

    // ---- problem details -------------------------------------------------------------------

    @Test
    void problem_details_schema_lists_the_rfc7807_members_we_return() {
        assertThat(propertyNames("ProblemDetailsSchema"))
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance");
    }
}
