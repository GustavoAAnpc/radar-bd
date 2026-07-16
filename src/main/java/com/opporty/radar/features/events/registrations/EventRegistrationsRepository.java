package com.opporty.radar.features.events.registrations;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.events.core.Events;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRegistrationsRepository extends JpaRepository<EventRegistrations, Long> {
    Optional<EventRegistrations> findByUserAndEvent(Users user, Events event);
    boolean existsByUserAndEvent(Users user, Events event);
    List<EventRegistrations> findByUser(Users user);
    List<EventRegistrations> findByEvent(Events event);

    @Query("SELECT COUNT(er) FROM EventRegistrations er WHERE er.event = :event AND er.attendanceStatus NOT IN ('CANCELLED', 'REJECTED', 'PENDING_APPROVAL')")
    long countActiveRegistrationsByEvent(@Param("event") Events event);

    @Query("SELECT er FROM EventRegistrations er JOIN FETCH er.user WHERE er.event = :event AND er.attendanceStatus NOT IN ('CANCELLED', 'REJECTED')")
    List<EventRegistrations> findActiveRegistrationsWithUsers(@Param("event") Events event);
}
