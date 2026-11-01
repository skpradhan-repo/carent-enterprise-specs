package com.carent.claims.adapter.in.web;

import static com.carent.claims.domain.ClaimFixtures.CLOCK;
import static com.carent.claims.domain.ClaimFixtures.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carent.claims.application.port.in.GetClaimUseCase;
import com.carent.claims.application.port.in.SubmitClaimCommand;
import com.carent.claims.application.port.in.SubmitClaimUseCase;
import com.carent.claims.domain.exception.ClaimNotFoundException;
import com.carent.claims.domain.exception.IllegalClaimStateException;
import com.carent.claims.domain.exception.InvalidClaimException;
import com.carent.claims.domain.model.BenefitCategory;
import com.carent.claims.domain.model.Claim;
import com.carent.claims.domain.service.AdjudicationEngine;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Web slice: mapping, validation, response shape and RFC 7807 bodies. Use cases are mocked. */
@WebMvcTest(ClaimController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ClaimControllerTest {

    private static final String BASE = "/api/v1/claims";
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final MockMvc mvc;

    @MockBean
    private SubmitClaimUseCase submitClaim;

    @MockBean
    private GetClaimUseCase getClaim;

    ClaimControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static Map<String, String> validRequest() {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("memberId", "MEM-99201");
        request.put("providerId", "PRV-44102");
        request.put("serviceDate", "2026-10-25");
        request.put("billedAmount", "150.00");
        request.put("benefitCategory", "IN_NETWORK_PRIMARY_CARE");
        return request;
    }

    private static String json(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String jsonWith(String field, String value) {
        Map<String, String> request = validRequest();
        request.put(field, value);
        return json(request);
    }

    private static String jsonWithout(String field) {
        Map<String, String> request = validRequest();
        request.remove(field);
        return json(request);
    }

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static Claim approvedClaim() {
        Claim claim = pending(BenefitCategory.IN_NETWORK_PRIMARY_CARE, "150.00");
        new AdjudicationEngine(CLOCK).adjudicate(claim, false);
        return claim;
    }

    private static void assertProblem(ResultActions result, int status, String title, String instance)
            throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.instance").value(instance));
    }

    // ---- POST /claims: success -------------------------------------------------------------

    @Test
    void post_valid_claim_returns_201_with_contract_fields_and_money_as_strings() throws Exception {
        when(submitClaim.submit(any())).thenReturn(approvedClaim());

        postJson(json(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimReference").value("CLM-20261031-891023"))
                .andExpect(jsonPath("$.memberId").value("MEM-99201"))
                .andExpect(jsonPath("$.providerId").value("PRV-44102"))
                .andExpect(jsonPath("$.serviceDate").value("2026-10-25"))
                .andExpect(jsonPath("$.billedAmount").value("150.00"))
                .andExpect(jsonPath("$.benefitCategory").value("IN_NETWORK_PRIMARY_CARE"))
                .andExpect(jsonPath("$.adjudicatedAmount").value("120.00"))
                .andExpect(jsonPath("$.patientCoPay").value("30.00"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.denialReason").value(nullValue()));
    }

    @Test
    void post_maps_request_to_command_without_loss_of_precision() throws Exception {
        when(submitClaim.submit(any())).thenReturn(approvedClaim());

        postJson(json(validRequest())).andExpect(status().isCreated());

        ArgumentCaptor<SubmitClaimCommand> captor = ArgumentCaptor.forClass(SubmitClaimCommand.class);
        verify(submitClaim).submit(captor.capture());
        SubmitClaimCommand command = captor.getValue();
        assertThat(command.memberId()).isEqualTo("MEM-99201");
        assertThat(command.providerId()).isEqualTo("PRV-44102");
        assertThat(command.serviceDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(command.billedAmount()).hasToString("150.00");
        assertThat(command.benefitCategory()).isEqualTo(BenefitCategory.IN_NETWORK_PRIMARY_CARE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"150", "150.5", "0.99", "1234567890.12"})
    void post_accepts_every_amount_the_openapi_pattern_allows(String amount) throws Exception {
        when(submitClaim.submit(any())).thenReturn(approvedClaim());

        postJson(jsonWith("billedAmount", amount)).andExpect(status().isCreated());
    }

    @Test
    void under_review_claim_serialises_missing_amounts_as_null() throws Exception {
        Claim claim = pending(BenefitCategory.GENERAL_MEDICAL, "50.00");
        new AdjudicationEngine(CLOCK).adjudicate(claim, true);
        when(submitClaim.submit(any())).thenReturn(claim);

        postJson(json(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.adjudicatedAmount").value(nullValue()))
                .andExpect(jsonPath("$.patientCoPay").value(nullValue()));
    }

    // ---- POST /claims: validation (RFC 7807, status 400) -----------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"memberId", "providerId", "serviceDate", "billedAmount", "benefitCategory"})
    void missing_required_field_returns_400_problem_naming_the_field(String field) throws Exception {
        ResultActions result = postJson(jsonWithout(field));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString(field)));
        verifyNoInteractions(submitClaim);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEM-", "M", ""})
    void member_id_shorter_than_five_characters_is_rejected(String memberId) throws Exception {
        ResultActions result = postJson(jsonWith("memberId", memberId));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString("memberId")));
        verifyNoInteractions(submitClaim);
    }

    @Test
    void provider_id_shorter_than_five_characters_is_rejected() throws Exception {
        ResultActions result = postJson(jsonWith("providerId", "PRV-"));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString("providerId")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-5.00", "10.999", "1e3", "", " 5.00", "5.", ".50", "5,00",
            "12345678901234.00"})
    void malformed_or_oversized_billed_amount_is_rejected(String amount) throws Exception {
        ResultActions result = postJson(jsonWith("billedAmount", amount));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString("billedAmount")));
        verifyNoInteractions(submitClaim);
    }

    @Test
    void unknown_benefit_category_is_rejected_not_mapped_to_a_default() throws Exception {
        ResultActions result = postJson(jsonWith("benefitCategory", "DENTAL"));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString("benefitCategory")))
                .andExpect(jsonPath("$.detail").value(not(containsString("DENTAL"))));
        verifyNoInteractions(submitClaim);
    }

    @Test
    void invalid_service_date_is_rejected() throws Exception {
        ResultActions result = postJson(jsonWith("serviceDate", "2026-13-45"));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value(containsString("serviceDate")));
    }

    @Test
    void malformed_json_returns_400_problem() throws Exception {
        assertProblem(postJson("{not json"), 400, "Validation Failed", BASE);
        verifyNoInteractions(submitClaim);
    }

    @Test
    void empty_body_returns_400_problem() throws Exception {
        assertProblem(mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)), 400,
                "Validation Failed", BASE);
    }

    @Test
    void domain_validation_failure_is_translated_to_400_with_its_message() throws Exception {
        when(submitClaim.submit(any())).thenThrow(
                new InvalidClaimException("billedAmount must be greater than or equal to 5.00"));

        ResultActions result = postJson(jsonWith("billedAmount", "4.99"));

        assertProblem(result, 400, "Validation Failed", BASE);
        result.andExpect(jsonPath("$.detail").value("billedAmount must be greater than or equal to 5.00"));
    }

    // ---- other failures ----------------------------------------------------------------------

    @Test
    void illegal_state_transition_is_translated_to_409() throws Exception {
        when(submitClaim.submit(any())).thenThrow(new IllegalClaimStateException("not allowed"));

        assertProblem(postJson(json(validRequest())), 409, "Conflict", BASE);
    }

    @Test
    void unexpected_error_returns_generic_500_and_leaks_nothing() throws Exception {
        when(submitClaim.submit(any())).thenThrow(new RuntimeException("jdbc:postgresql://db password=s3cret"));

        ResultActions result = postJson(json(validRequest()));

        assertProblem(result, 500, "Internal Server Error", BASE);
        result.andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("s3cret").doesNotContain("jdbc").doesNotContain("RuntimeException");
    }

    @Test
    void non_json_content_type_returns_415_problem() throws Exception {
        assertProblem(mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("x")), 415,
                "Unsupported Media Type", BASE);
    }

    @Test
    void unsupported_method_returns_405_problem() throws Exception {
        assertProblem(mvc.perform(delete(BASE)), 405, "Method Not Allowed", BASE);
    }

    // ---- GET /claims/{claimReference} --------------------------------------------------------

    @Test
    void get_existing_claim_returns_200_with_contract_fields() throws Exception {
        when(getClaim.getByReference("CLM-20261031-891023")).thenReturn(approvedClaim());

        mvc.perform(get(BASE + "/CLM-20261031-891023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimReference").value("CLM-20261031-891023"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.billedAmount").value("150.00"))
                .andExpect(jsonPath("$.adjudicatedAmount").value("120.00"))
                .andExpect(jsonPath("$.patientCoPay").value("30.00"));
    }

    @Test
    void get_unknown_claim_returns_404_problem() throws Exception {
        String path = BASE + "/CLM-20261031-000000";
        when(getClaim.getByReference("CLM-20261031-000000"))
                .thenThrow(new ClaimNotFoundException("CLM-20261031-000000"));

        ResultActions result = mvc.perform(get(path));

        assertProblem(result, 404, "Claim Not Found", path);
        result.andExpect(jsonPath("$.detail").value(containsString("CLM-20261031-000000")));
    }

    @Test
    void get_with_malformed_reference_returns_400_problem() throws Exception {
        String path = BASE + "/not-a-reference";
        when(getClaim.getByReference("not-a-reference"))
                .thenThrow(new InvalidClaimException("claimReference must match CLM-YYYYMMDD-XXXXXX"));

        assertProblem(mvc.perform(get(path)), 400, "Validation Failed", path);
    }
}
