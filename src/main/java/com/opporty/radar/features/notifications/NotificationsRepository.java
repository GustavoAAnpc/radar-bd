package com.opporty.radar.features.notifications;

import com.opporty.radar.features.auth.users.Users;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {
    List<Notifications> findByUserOrderByCreatedAtDesc(Users user);
    long countByUserAndIsReadFalse(Users user);
    List<Notifications> findByUserAndIsReadFalse(Users user);
    boolean existsByEventIdAndTitle(Long eventId, String title);
}
