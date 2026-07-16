package com.opporty.radar.features.auth.users;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.opporty.radar.features.events.categories.EventCategories;

public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<Users> findByRoleName(String roleName);

    @Query("SELECT DISTINCT u FROM Users u JOIN u.interests c WHERE c IN :categories AND u.expoPushToken IS NOT NULL")
    List<Users> findByInterestsInAndExpoPushTokenNotNull(@Param("categories") Set<EventCategories> categories);
}
