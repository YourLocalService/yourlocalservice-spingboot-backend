package com.nikita_ovramenko.sping_all_purpose_server.organization.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nikita_ovramenko.sping_all_purpose_server.common.exception.BadRequestException;
import com.nikita_ovramenko.sping_all_purpose_server.common.dto.PageResponse;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.MailSettingsRequest;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationCreateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationDetail;
import com.nikita_ovramenko.sping_all_purpose_server.organization.dto.OrganizationUpdateRequest;
import com.nikita_ovramenko.sping_all_purpose_server.organization.exception.OrganizationNotFoundException;
import com.nikita_ovramenko.sping_all_purpose_server.organization.mapper.OrganizationMapper;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.MailSettings;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;
import com.nikita_ovramenko.sping_all_purpose_server.organization.repository.OrganizationRepo;
import com.nikita_ovramenko.sping_all_purpose_server.organizationserviceoffering.model.OrganizationServiceOffering;
import com.nikita_ovramenko.sping_all_purpose_server.organizationserviceoffering.repository.OrganizationServiceOfferingRepo;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.dto.ServiceSummary;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.exception.UnknownServiceException;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.model.ServiceOffering;
import com.nikita_ovramenko.sping_all_purpose_server.serviceoffering.repository.ServiceOfferingRepo;

/**
 * Managing the businesses this backend serves and what each of them offers.
 *
 * <p>There is no delete. Organizations are referenced by quote, job and their line
 * items with no cascade, so removing one either fails on the foreign key or destroys
 * history that a report still needs. Setting active = false is the mechanism, and
 * OrganizationLookup.requireBySlug already refuses inactive organizations on the public
 * routes.
 */
@Service
public class OrganizationAdminService {

    private final OrganizationRepo organizationRepo;
    private final ServiceOfferingRepo serviceOfferingRepo;
    private final OrganizationServiceOfferingRepo offeringRepo;
    private final OrganizationMapper organizationMapper;

    public OrganizationAdminService(OrganizationRepo organizationRepo,
            ServiceOfferingRepo serviceOfferingRepo,
            OrganizationServiceOfferingRepo offeringRepo,
            OrganizationMapper organizationMapper) {
        this.organizationRepo = organizationRepo;
        this.serviceOfferingRepo = serviceOfferingRepo;
        this.offeringRepo = offeringRepo;
        this.organizationMapper = organizationMapper;
    }

    /** Includes inactive organizations: this is the screen you use to reactivate one. */
    @Transactional(readOnly = true)
    public PageResponse<OrganizationDetail> list(Pageable pageable) {
        return PageResponse.of(organizationRepo.findAll(pageable), organizationMapper::toDetail);
    }

    @Transactional(readOnly = true)
    public OrganizationDetail get(Long id) {
        return organizationMapper.toDetail(require(id));
    }

    @Transactional
    public OrganizationDetail create(OrganizationCreateRequest request) {
        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setSlug(request.slug().trim().toLowerCase());
        organization.setContactEmail(request.contactEmail().trim());
        organization.setActive(request.active() == null || request.active());
        organization.setMailSettings(toMailSettings(request.mail()));

        // A duplicate slug surfaces as DataIntegrityViolationException -> 409 from the
        // uq_organization_slug constraint, which is a clear enough message here.
        return organizationMapper.toDetail(organizationRepo.save(organization));
    }

    /** Partial update: a null field is left unchanged. */
    @Transactional
    public OrganizationDetail update(Long id, OrganizationUpdateRequest request) {
        Organization organization = require(id);

        if (request.name() != null) {
            organization.setName(request.name().trim());
        }
        if (request.contactEmail() != null) {
            organization.setContactEmail(request.contactEmail().trim());
        }
        if (request.active() != null) {
            organization.setActive(request.active());
        }
        if (request.mail() != null) {
            organization.setMailSettings(mergeMailSettings(organization.getMailSettings(), request.mail()));
        }

        return organizationMapper.toDetail(organizationRepo.save(organization));
    }

    /** The admin view, which unlike the public one includes inactive services. */
    @Transactional(readOnly = true)
    public List<ServiceSummary> services(Long organizationId) {
        Organization organization = require(organizationId);
        return offeringRepo.findAllByOrganizationId(organization.getId()).stream()
                .map(OrganizationServiceOffering::getService)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(organizationMapper::toSummary)
                .toList();
    }

    /**
     * Replaces the offering set outright.
     *
     * <p>Deleting rows from organization_service does not touch quotes or jobs that
     * already reference the service: those point at the service directly, so past work
     * survives an organization dropping a service from its menu.
     */
    @Transactional
    public List<ServiceSummary> replaceServices(Long organizationId, List<Long> serviceIds) {
        Organization organization = require(organizationId);
        Set<Long> wanted = new LinkedHashSet<>(serviceIds);

        List<ServiceOffering> found = serviceOfferingRepo.findAllById(wanted);
        // findAllById silently drops ids it cannot find, so the size check is required.
        if (found.size() != wanted.size()) {
            Set<Long> unknown = new LinkedHashSet<>(wanted);
            found.forEach(service -> unknown.remove(service.getId()));
            throw new UnknownServiceException(unknown);
        }

        offeringRepo.deleteAll(offeringRepo.findAllByOrganizationId(organization.getId()));
        // Flush the deletes before inserting, or re-adding a service that was already
        // present collides with its own not-yet-removed row on the composite key.
        offeringRepo.flush();

        List<OrganizationServiceOffering> offerings = new ArrayList<>();
        for (ServiceOffering service : found) {
            offerings.add(new OrganizationServiceOffering(organization, service));
        }
        offeringRepo.saveAll(offerings);

        return found.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(organizationMapper::toSummary)
                .toList();
    }

    /**
     * Enforces the all-or-nothing rule that ck_organization_smtp_complete also enforces,
     * so an incomplete configuration is a 400 naming the gaps rather than an opaque
     * constraint violation from the database.
     */
    private static MailSettings mergeMailSettings(MailSettings existing, MailSettingsRequest request) {
        MailSettings current = existing == null ? new MailSettings() : existing;
        return toMailSettings(new MailSettingsRequest(
                request.host() == null ? current.getHost() : request.host(),
                request.port() == null ? current.getPort() : request.port(),
                request.username() == null ? current.getUsername() : request.username(),
                request.passwordEnv() == null ? current.getPasswordEnv() : request.passwordEnv(),
                request.sslEnabled() == null ? current.getSslEnabled() : request.sslEnabled(),
                request.starttlsEnabled() == null ? current.getStarttlsEnabled() : request.starttlsEnabled(),
                request.fromEmail() == null ? current.getFromEmail() : request.fromEmail(),
                request.fromName() == null ? current.getFromName() : request.fromName()));
    }

    private static MailSettings toMailSettings(MailSettingsRequest request) {
        MailSettings settings = new MailSettings();
        if (request == null || request.host() == null || request.host().isBlank()) {
            // No host means "fall back to the application-wide sender", which is a
            // legitimate state -- three of the four organizations are in it.
            return settings;
        }

        List<String> missing = new ArrayList<>();
        if (request.port() == null) {
            missing.add("port");
        }
        if (request.username() == null || request.username().isBlank()) {
            missing.add("username");
        }
        if (request.passwordEnv() == null || request.passwordEnv().isBlank()) {
            missing.add("passwordEnv");
        }
        if (request.fromEmail() == null || request.fromEmail().isBlank()) {
            missing.add("fromEmail");
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Mail settings are all-or-nothing: with a host set, "
                    + String.join(", ", missing) + " must be set too. "
                    + "Omit host entirely to use the application-wide sender.");
        }

        settings.setHost(request.host().trim());
        settings.setPort(request.port());
        settings.setUsername(request.username().trim());
        settings.setPasswordEnv(request.passwordEnv().trim());
        settings.setSslEnabled(request.sslEnabled());
        settings.setStarttlsEnabled(request.starttlsEnabled());
        settings.setFromEmail(request.fromEmail().trim());
        settings.setFromName(request.fromName());
        return settings;
    }

    private Organization require(Long id) {
        return organizationRepo.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException("No organization with id " + id));
    }
}
