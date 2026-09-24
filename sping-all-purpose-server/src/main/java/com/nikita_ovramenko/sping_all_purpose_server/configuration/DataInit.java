package com.nikita_ovramenko.sping_all_purpose_server.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nikita_ovramenko.sping_all_purpose_server.app_user.enums.Role;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.model.AppUser;
import com.nikita_ovramenko.sping_all_purpose_server.app_user.repository.AppUserRepo;
import com.nikita_ovramenko.sping_all_purpose_server.organization.model.Organization;
import com.nikita_ovramenko.sping_all_purpose_server.organization.repository.OrganizationRepo;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganization;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.repository.UserOrganizationRepo;

/**
 * Seeds the admin account and ensures it belongs to every existing organization.
 *
 * <p>Registration creates unverified users and there is no verification flow yet, so
 * without this there would be no way to obtain a token at all.
 */
@Component
public class DataInit implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInit.class);
    private static final String ADMIN_EMAIL = "tcs.ontario@gmail.com";

    private final AppUserRepo userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepo organizationRepository;
    private final UserOrganizationRepo userOrganizationRepository;

    @Value("${spring.app.admin_password}")
    private String adminPassword;

    public DataInit(AppUserRepo userRepository, PasswordEncoder passwordEncoder,
            OrganizationRepo organizationRepository, UserOrganizationRepo userOrganizationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        AppUser admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL)
                .orElseGet(this::createAdmin);

        for (Organization organization : organizationRepository.findAll()) {
            UserOrganization membership = new UserOrganization(admin, organization);
            if (!userOrganizationRepository.existsById(membership.getId())) {
                userOrganizationRepository.save(membership);
            }
        }
    }

    private AppUser createAdmin() {
        AppUser admin = AppUser.builder()
                .email(ADMIN_EMAIL)
                .name("Administrator")   // NOT NULL -- omitting it failed the insert
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .verified(true)          // otherwise login rejects the only usable account
                .build();
        

        admin = userRepository.save(admin);
        log.info("Seeded admin account {}", ADMIN_EMAIL);
        return admin;
    }
    
}
