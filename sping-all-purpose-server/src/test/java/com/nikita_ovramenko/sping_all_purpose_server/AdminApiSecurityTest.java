package com.nikita_ovramenko.sping_all_purpose_server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.nikita_ovramenko.sping_all_purpose_server.configuration.SecurityConfig;
import com.nikita_ovramenko.sping_all_purpose_server.common.filter.JwtFilter;
import com.nikita_ovramenko.sping_all_purpose_server.common.service.JwtService;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.service.AppUserService;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.service.AuthSessionService;
import com.nikita_ovramenko.sping_all_purpose_server.common.dto.PageResponse;
import com.nikita_ovramenko.sping_all_purpose_server.quote.service.QuoteSubmissionService;
import com.nikita_ovramenko.sping_all_purpose_server.quote.controller.QuoteController;
import com.nikita_ovramenko.sping_all_purpose_server.common.exception.ConflictException;
import com.nikita_ovramenko.sping_all_purpose_server.common.exception.BadRequestException;
import com.nikita_ovramenko.sping_all_purpose_server.job.exception.JobNotFoundException;
import com.nikita_ovramenko.sping_all_purpose_server.organizationserviceoffering.exception.ServiceNotOfferedException;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.controller.AdminUserController;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.service.AdminUserService;
import com.nikita_ovramenko.sping_all_purpose_server.quote.controller.AdminQuoteController;
import com.nikita_ovramenko.sping_all_purpose_server.quote.service.QuoteAdminService;
import com.nikita_ovramenko.sping_all_purpose_server.quotelineitem.controller.AdminQuoteLineItemController;
import com.nikita_ovramenko.sping_all_purpose_server.quotelineitem.service.QuoteLineItemService;
import com.nikita_ovramenko.sping_all_purpose_server.job.controller.AdminJobController;
import com.nikita_ovramenko.sping_all_purpose_server.job.service.JobService;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.controller.AdminJobLineItemController;
import com.nikita_ovramenko.sping_all_purpose_server.joblineitem.service.JobLineItemService;
import com.nikita_ovramenko.sping_all_purpose_server.organization.controller.AdminOrganizationController;
import com.nikita_ovramenko.sping_all_purpose_server.organization.service.OrganizationAdminService;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.controller.AdminServiceOfferingController;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.service.ServiceOfferingAdminService;

@WebMvcTest(controllers = {AdminUserController.class, AdminQuoteController.class, AdminQuoteLineItemController.class, AdminJobController.class, AdminJobLineItemController.class, AdminOrganizationController.class, AdminServiceOfferingController.class, QuoteController.class},
        properties = "spring.app.cors_allowed=http://localhost:3000")
@Import({SecurityConfig.class, JwtFilter.class})
class AdminApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AdminOrganizationController organizationController;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthSessionService authSessionService;
    @MockitoBean AppUserService appUserService;
    @MockitoBean QuoteSubmissionService submission;
    @MockitoBean AdminUserService adminUserService;
    @MockitoBean QuoteAdminService quoteAdminService;
    @MockitoBean QuoteLineItemService quoteLineItemService;
    @MockitoBean JobService jobService;
    @MockitoBean JobLineItemService jobLineItemService;
    @MockitoBean OrganizationAdminService organizationAdminService;
    @MockitoBean ServiceOfferingAdminService serviceOfferingAdminService;

    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                "GET /quotes", "GET /quotes/1", "POST /quotes", "PATCH /quotes/1",
                "GET /quotes/1/items", "POST /quotes/1/items", "PATCH /quotes/1/items/2", "DELETE /quotes/1/items/2",
                "GET /jobs", "GET /jobs/1", "POST /jobs", "PATCH /jobs/1",
                "GET /jobs/1/items", "POST /jobs/1/items", "PATCH /jobs/1/items/2", "DELETE /jobs/1/items/2",
                "GET /users", "GET /users/1", "POST /users", "PATCH /users/1", "POST /users/1/verify", "DELETE /users/1",
                "GET /organizations", "GET /organizations/1", "POST /organizations", "PATCH /organizations/1",
                "GET /organizations/1/services", "PUT /organizations/1/services",
                "GET /services", "POST /services", "PATCH /services/1")
                .map(route -> route.split(" "))
                .map(parts -> Arguments.of(parts[0], "/api/admin" + parts[1]));
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void anonymousRequestsReceive401(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(quoteAdminService, quoteLineItemService, jobService, jobLineItemService,
                adminUserService, organizationAdminService, serviceOfferingAdminService, submission);
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    @WithMockUser(roles = "MEMBER")
    void membersReceive403(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(quoteAdminService, quoteLineItemService, jobService, jobLineItemService,
                adminUserService, organizationAdminService, serviceOfferingAdminService, submission);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/quotes", "/quotes/1/items", "/jobs", "/jobs/1/items",
            "/users", "/organizations", "/services"})
    @WithMockUser(roles = "ADMIN")
    void administratorsCanReadEveryController(String path) throws Exception {
        mvc.perform(get("/api/admin" + path)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void methodSecurityAlsoProtectsControllerCallsOutsideTheUrlFilter() {
        assertThatThrownBy(() -> organizationController.list(PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(organizationAdminService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void organizationListBindsPaginationAndReturnsMetadata() throws Exception {
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "name"));
        when(organizationAdminService.list(pageable))
                .thenReturn(new PageResponse<>(List.of(), 1, 2, 2, 1));
        mvc.perform(get("/api/admin/organizations")
                .param("page", "1").param("size", "2").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(organizationAdminService).list(pageable);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void organizationListDefaultsToTwentySortedByNameAndId() throws Exception {
        mvc.perform(get("/api/admin/organizations")).andExpect(status().isOk());
        verify(organizationAdminService).list(PageRequest.of(0, 20, Sort.by("name", "id")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void orgSlugFiltersBindAndPagesHaveTheStableResponseShape() throws Exception {
        when(quoteAdminService.list(eq("tcs"), isNull(), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/admin/quotes").param("orgSlug", "tcs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageable").doesNotExist());
        mvc.perform(get("/api/admin/jobs").param("orgSlug", "tcs")).andExpect(status().isOk());
        verify(jobService).list(eq("tcs"), isNull(), isNull(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidEnumIs400AndDomainErrorsKeepTheirStatusCodes() throws Exception {
        mvc.perform(get("/api/admin/jobs").param("status", "NOPE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Expected one of")));
        when(jobService.get(99L)).thenThrow(new JobNotFoundException(99L));
        mvc.perform(get("/api/admin/jobs/99")).andExpect(status().isNotFound());
        when(jobService.create(any())).thenThrow(new ConflictException("Quote 42 already has job 17"));
        mvc.perform(post("/api/admin/jobs").contentType(MediaType.APPLICATION_JSON).content("{\"quoteId\":42}"))
                .andExpect(status().isConflict());
        when(quoteLineItemService.add(eq(1L), any()))
                .thenThrow(new ServiceNotOfferedException("tcs", java.util.Set.of(99L)));
        mvc.perform(post("/api/admin/quotes/1/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceId\":99,\"quantity\":1}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mailSettingsUsesThePlannedFieldNameAndIncompleteSettingsAre400() throws Exception {
        when(organizationAdminService.update(eq(1L), any()))
                .thenThrow(new BadRequestException("port, username, passwordEnv, fromEmail must be set"));
        mvc.perform(patch("/api/admin/organizations/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mailSettings\":{\"host\":\"smtp.example.com\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("passwordEnv")));
        verify(organizationAdminService).update(eq(1L),
                argThat(value -> value.mail() != null && value.mail().host().equals("smtp.example.com")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nullEntriesInJobServicesAndOfferingSetsAre400() throws Exception {
        mvc.perform(post("/api/admin/jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgSlug\":\"tcs\",\"services\":[null]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/organizations/1/services").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceIds\":[null]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(jobService, organizationAdminService);
    }

    @Test
    void publicOrgWildcardDoesNotExposeQuoteLists() throws Exception {
        mvc.perform(get("/api/orgs/tcs/quotes")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(quoteAdminService, submission);
    }

    @Test
    void aRoleChangeTakesEffectOnTheNextRequestUsingTheSameToken() throws Exception {
        when(jwtService.readAccessToken("same-token")).thenReturn(
                new JwtService.TokenIdentity("admin@example.com", java.util.UUID.randomUUID()));
        when(appUserService.loadUserByUsername("admin@example.com")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("admin@example.com")
                        .password("unused").roles("ADMIN").build(),
                org.springframework.security.core.userdetails.User.withUsername("admin@example.com")
                        .password("unused").roles("MEMBER").build());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer same-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer same-token"))
                .andExpect(status().isForbidden());
        verify(appUserService, times(2)).loadUserByUsername("admin@example.com");
    }

    @Test
    void publicQuoteSubmissionStillReachesBodyValidationWithoutAuthentication() throws Exception {
        mvc.perform(post("/api/orgs/tcs/quotes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
