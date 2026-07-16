package com.opporty.radar.features.notifications;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.auth.users.UsersRepository;
import com.opporty.radar.features.events.core.Events;
import com.opporty.radar.features.events.categories.EventCategories;
import com.opporty.radar.features.events.registrations.EventRegistrations;
import com.opporty.radar.features.events.registrations.EventRegistrationsRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationsService {

    private final NotificationsRepository notificationsRepository;
    private final NotificationsMapper notificationsMapper;
    private final UsersRepository usersRepository;
    private final ExpoPushService expoPushService;
    private final EventRegistrationsRepository eventRegistrationsRepository;

    @Transactional(readOnly = true)
    public List<NotificationsViewDTO> getUserNotifications(Users user) {
        return notificationsRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(notificationsMapper::toDt)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Users user) {
        return notificationsRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public NotificationsViewDTO markAsRead(Long notificationId, Users user) {
        Notifications notification = notificationsRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada con ID: " + notificationId));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para modificar esta notificación.");
        }

        notification.setRead(true);
        Notifications saved = notificationsRepository.save(notification);
        return notificationsMapper.toDt(saved);
    }

    @Transactional
    public void markAllAsRead(Users user) {
        List<Notifications> unread = notificationsRepository.findByUserAndIsReadFalse(user);
        for (Notifications n : unread) {
            n.setRead(true);
        }
        notificationsRepository.saveAll(unread);
    }

    /**
     * Crea una notificación interna en la BD Y envía push al dispositivo del usuario.
     * Este es el punto central: todos los flujos del sistema pasan por aquí,
     * así que al agregar push aquí, TODOS los escenarios existentes heredan push automáticamente.
     */
    @Transactional
    public void createNotification(Users user, String title, String message, Long eventId) {
        Notifications notification = Notifications.builder()
                .user(user)
                .title(title)
                .message(message)
                .isRead(false)
                .eventId(eventId)
                .build();
        notificationsRepository.save(notification);

        // Enviar push notification al dispositivo
        Map<String, Object> data = new HashMap<>();
        if (eventId != null) {
            data.put("eventId", eventId);
            data.put("screen", "event-detail");
        }
        expoPushService.sendPush(user.getExpoPushToken(), title, message, data);
    }

    @Transactional
    public void notifyAdmins(String title, String message, Long eventId) {
        List<Users> admins = usersRepository.findByRoleName("ADMIN");
        for (Users admin : admins) {
            createNotification(admin, title, message, eventId);
        }
    }

    /**
     * Notifica a todos los usuarios inscritos activos de un evento.
     * Usado para: cancelación, suspensión, recordatorios, etc.
     */
    @Transactional
    public void notifyEventAttendees(Events event, String title, String message) {
        List<EventRegistrations> registrations = eventRegistrationsRepository.findActiveRegistrationsWithUsers(event);
        for (EventRegistrations reg : registrations) {
            createNotification(reg.getUser(), title, message, event.getId());
        }
    }

    /**
     * Notifica a usuarios cuyas preferencias (interests) coincidan con las categorías del evento.
     * Excluye al creador del evento para no auto-notificarse.
     */
    @Transactional
    public void notifyInterestedUsers(Events event) {
        Set<EventCategories> eventCategories = event.getCategories();
        if (eventCategories == null || eventCategories.isEmpty()) {
            return;
        }

        List<Users> interestedUsers = usersRepository.findByInterestsInAndExpoPushTokenNotNull(eventCategories);

        String title = "🔔 Nuevo evento de tu interés";
        String message = "Se ha publicado '" + event.getTitulo() + "'. ¡Podría interesarte!";

        for (Users user : interestedUsers) {
            // No notificar al creador del evento
            if (event.getCreatedBy() != null && event.getCreatedBy().getId().equals(user.getId())) {
                continue;
            }
            createNotification(user, title, message, event.getId());
        }
    }
}
