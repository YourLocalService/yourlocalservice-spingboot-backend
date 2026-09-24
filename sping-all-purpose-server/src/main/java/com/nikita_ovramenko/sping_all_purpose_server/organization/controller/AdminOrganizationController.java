package com.nikita_ovramenko.sping_all_purpose_server.organization.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationCreateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.common.dto.PageResponse;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationDetail;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationServicesRequest;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationUpdateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.organization.service.OrganizationAdminService;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.dto.ServiceSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * The businesses this backend serves.
 *
 * <p>No DELETE: quotes, jobs and line items reference an organization with no cascade,
 * so deleting one would either fail on the foreign key or destroy history. Deactivate
 * with PATCH active=false, which the public routes already respect.
 */
@RestController
@RequestMapping("/api/admin/organizations")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: organizations", description = "Manage businesses, their mail senders and what they offer")
public class AdminOrganizationController {

    private final OrganizationAdminService organizationAdminService;

    public AdminOrganizationController(OrganizationAdminService organizationAdminService) {
        this.organizationAdminService = organizationAdminService;
    }

    @GetMapping
    @Operation(summary = "List organizations, including deactivated ones",
            description = "Paginated with zero-based page, size and sort parameters. Defaults to 20 results sorted by name, then id.")
    public PageResponse<OrganizationDetail> list(
            @PageableDefault(size = 20, sort = {"name", "id"}) Pageable pageable) {
        return organizationAdminService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one organization, including its mail configuration",
            description = "smtpPasswordEnv is the name of an environment variable, never a password.")
    public OrganizationDetail get(@PathVariable Long id) {
        return organizationAdminService.get(id);
    }

    @PostMapping
    @Operation(summary = "Create an organization")
    public ResponseEntity<OrganizationDetail> create(
            @Valid @RequestBody OrganizationCreateRequest request) {
        OrganizationDetail created = organizationAdminService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/organizations/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name, contact email, active flag or mail settings",
            description = "Omitted or null fields are left unchanged, including within mailSettings. "
                    + "The resulting mail settings must be complete: with a host set, port, "
                    + "username, passwordEnv and fromEmail are all required. The slug cannot be "
                    + "changed -- it is embedded in public URLs and stored object keys.")
    public OrganizationDetail update(@PathVariable Long id,
            @Valid @RequestBody OrganizationUpdateRequest request) {
        return organizationAdminService.update(id, request);
    }

    @GetMapping("/{id}/services")
    @Operation(summary = "The services this organization offers, including inactive ones")
    public List<ServiceSummary> services(@PathVariable Long id) {
        return organizationAdminService.services(id);
    }

    @PutMapping("/{id}/services")
    @Operation(summary = "Replace the set of services this organization offers",
            description = "Send the intended end state. Removing a service does not affect "
                    + "quotes or jobs that already reference it.")
    public List<ServiceSummary> replaceServices(@PathVariable Long id,
            @Valid @RequestBody OrganizationServicesRequest request) {
        return organizationAdminService.replaceServices(id, request.serviceIds());
    }
}
