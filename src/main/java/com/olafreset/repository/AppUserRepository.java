package com.olafreset.repository;

import com.olafreset.domain.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AppUser> findByInviteCode(String inviteCode);

    List<AppUser> findAllByOrderByIdAsc();
}
