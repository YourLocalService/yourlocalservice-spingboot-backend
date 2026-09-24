package com.nikita_ovramenko.sping_all_purpose_server.userorganization.model;

import com.nikita_ovramenko.sping_all_purpose_server.app_user.model.AppUser;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's membership in an organization. Each pair can occur only once. */
@Entity
@Table(name = "user_organization")
@Getter
@Setter
@NoArgsConstructor
public class UserOrganization {
    @EmbeddedId
    private UserOrganizationId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @MapsId("organizationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    public UserOrganization(AppUser user, Organization organization) {
        this.user = user;
        this.organization = organization;
        this.id = new UserOrganizationId(user.getId(), organization.getId());
    }
}
