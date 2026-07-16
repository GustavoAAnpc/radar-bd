package com.opporty.radar.features.events.core;

import com.opporty.radar.features.notifications.NotificationsService;
import com.opporty.radar.features.notifications.NotificationsRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduler que envía recordatorios push a los usuarios inscritos
 * en un evento, 1 hora y 15 minutos antes de su inicio.
 *
 * Se ejecuta cada 60 segundos. Para evitar duplicados, verifica
 * si ya existe una notificación con el mismo título y eventId
 * para cada usuario antes de crearla.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventsRepository eventsRepository;
    private final NotificationsService notificationsService;
    private final NotificationsRepository notificationsRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void sendEventReminders() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Buscar eventos de hoy que estén PUBLISHED o SCHEDULED
        List<Events> todaysEvents = eventsRepository.findByFechaInicioAndEstadoIn(
                today, List.of(Estado.PUBLISHED, Estado.SCHEDULED));

        for (Events event : todaysEvents) {
            if (event.getHoraInicio() == null) {
                continue;
            }

            long minutesUntilStart = Duration.between(now, event.getHoraInicio()).toMinutes();

            // Recordatorio 1 hora antes (ventana: entre 59 y 61 minutos)
            if (minutesUntilStart >= 59 && minutesUntilStart <= 61) {
                String title = "⏰ Tu evento comienza en 1 hora";
                String message = "'" + event.getTitulo() + "' comienza pronto. ¡Prepárate!";

                // Verificar que no se haya enviado ya este recordatorio
                if (!reminderAlreadySent(event.getId(), title)) {
                    notificationsService.notifyEventAttendees(event, title, message);
                    log.info("[Reminder] Recordatorio 1h enviado para evento ID: {}", event.getId());
                }
            }

            // Recordatorio 15 minutos antes (ventana: entre 14 y 16 minutos)
            if (minutesUntilStart >= 14 && minutesUntilStart <= 16) {
                String title = "🚀 Tu evento comienza en 15 minutos";
                String message = "'" + event.getTitulo() + "' está por comenzar. ¡No te lo pierdas!";

                if (!reminderAlreadySent(event.getId(), title)) {
                    notificationsService.notifyEventAttendees(event, title, message);
                    log.info("[Reminder] Recordatorio 15min enviado para evento ID: {}", event.getId());
                }
            }
        }
    }

    /**
     * Verifica si ya existe una notificación con ese título para ese evento,
     * evitando duplicados si el scheduler corre más de una vez en la misma ventana.
     */
    private boolean reminderAlreadySent(Long eventId, String title) {
        return notificationsRepository.existsByEventIdAndTitle(eventId, title);
    }
}
