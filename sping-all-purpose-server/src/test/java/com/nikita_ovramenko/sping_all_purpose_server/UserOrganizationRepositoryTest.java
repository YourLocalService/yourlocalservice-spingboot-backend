package com.nikita_ovramenko.sping_all_purpose_server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.nikita_ovramenko.sping_all_purpose_server.app_user.enums.Role;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.model.AppUser;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.repository.AppUserRepo;
import com.nikita_ovramenko.sping_all_purpose_server.organization.repository.OrganizationRepo;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganization;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganizationId;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.repository.UserOrganizationRepo;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserOrganizationRepositoryTest extends AbstractPostgresTest {
    @Autowired private AppUserRepo users;
    @Autowired private OrganizationRepo organizations;
    @Autowired private UserOrganizationRepo memberships;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void multipleUsersCanBelongToMultipleOrganizationsAndMembershipsCanBeRemoved() {
        AppUser first = createUser("membership-first@example.com");
        AppUser second = createUser("membership-second@example.com");
        var tcs = organizations.findBySlugIgnoreCase("tcs").orElseThrow();
        var paints = organizations.findBySlugIgnoreCase("yourlocalpaints").orElseThrow();
        memberships.save(new UserOrganization(first, tcs));
        memberships.save(new UserOrganization(first, paints));
        memberships.save(new UserOrganization(second, tcs));
        memberships.saveAndFlush(new UserOrganization(second, paints));
        entityManager.clear();

        assertThat(memberships.findAllByUserId(first.getId()))
                .extracting(m -> m.getOrganization().getId())
                .containsExactlyInAnyOrder(tcs.getId(), paints.getId());
        assertThat(memberships.findAllByUserId(second.getId())).hasSize(2);
        assertThat(memberships.findAllByOrganizationId(tcs.getId()))
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());

        var removedId = new UserOrganizationId(first.getId(), tcs.getId());
        memberships.deleteById(removedId);
        memberships.flush();
        entityManager.clear();
        assertThat(memberships.existsById(removedId)).isFalse();
        assertThat(memberships.findAllByUserId(first.getId())).hasSize(1);
        assertThat(users.existsById(first.getId())).isTrue();
        assertThat(organizations.existsById(tcs.getId())).isTrue();
    }

    @Test
    void databaseRejectsDuplicateMemberships() {
        AppUser user = createUser("membership-duplicate@example.com");
        var org = organizations.findBySlugIgnoreCase("tcs").orElseThrow();
        memberships.saveAndFlush(new UserOrganization(user, org));

        assertThatThrownBy(() -> jdbc.update(
                "insert into user_organization (user_id, organization_id) values (?, ?)",
                user.getId(), org.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingUsers() {
        var org = organizations.findBySlugIgnoreCase("tcs").orElseThrow();
        assertThatThrownBy(() -> jdbc.update(
                "insert into user_organization (user_id, organization_id) values (?, ?)",
                -1L, org.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingOrganizations() {
        AppUser user = createUser("membership-missing-org@example.com");
        assertThatThrownBy(() -> jdbc.update(
                "insert into user_organization (user_id, organization_id) values (?, ?)",
                user.getId(), -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserRemovesMembershipsButKeepsTheOrganization() {
        AppUser user = createUser("membership-delete@example.com");
        var org = organizations.findBySlugIgnoreCase("tcs").orElseThrow();
        memberships.saveAndFlush(new UserOrganization(user, org));
        users.delete(user);
        users.flush();
        entityManager.clear();

        assertThat(memberships.findAllByUserId(user.getId())).isEmpty();
        assertThat(organizations.existsById(org.getId())).isTrue();
    }

    @Test
    void deletingAnOrganizationRemovesMembershipsButKeepsTheUser() {
        AppUser user = createUser("membership-delete-org@example.com");
        var org = new com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization();
        org.setName("Membership test");
        org.setSlug("membership-delete-test");
        org.setContactEmail("membership-org@example.com");
        organizations.saveAndFlush(org);
        memberships.saveAndFlush(new UserOrganization(user, org));
        organizations.delete(org);
        organizations.flush();
        entityManager.clear();

        assertThat(memberships.findAllByOrganizationId(org.getId())).isEmpty();
        assertThat(users.existsById(user.getId())).isTrue();
    }

    private AppUser createUser(String email) {
        return users.saveAndFlush(AppUser.builder().email(email).name("Membership test")
                .role(Role.MEMBER).passwordHash("test-only-hash").verified(true).build());
    }
}
