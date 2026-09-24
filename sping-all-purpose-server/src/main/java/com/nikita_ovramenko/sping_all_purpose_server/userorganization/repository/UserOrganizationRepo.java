package com.nikita_ovramenko.sping_all_purpose_server.userorganization.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganization;
import com.nikita_ovramenko.sping_all_purpose_server.userorganization.model.UserOrganizationId;

@Repository
public interface UserOrganizationRepo extends JpaRepository<UserOrganization, UserOrganizationId> {
    List<UserOrganization> findAllByUserId(Long userId);

    List<UserOrganization> findAllByOrganizationId(Long organizationId);
}
