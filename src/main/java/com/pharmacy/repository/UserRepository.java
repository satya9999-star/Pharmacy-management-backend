package com.pharmacy.repository;

import com.pharmacy.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameAndActiveTrue(String username);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    Optional<UserAccount> findByMobile(String mobile);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}
