package ru.kamoved.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kamoved.auth.domain.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    List<AppUser> findAllByActiveTrueOrderByIdAsc();
}
