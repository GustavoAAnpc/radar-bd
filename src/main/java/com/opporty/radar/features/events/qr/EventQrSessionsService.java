package com.opporty.radar.features.events.qr;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.events.core.Events;
import com.opporty.radar.features.events.core.EventsRepository;
import com.opporty.radar.features.events.registrations.AttendanceStatus;
import com.opporty.radar.features.events.registrations.EventRegistrations;
import com.opporty.radar.features.events.registrations.EventRegistrationsMapper;
import com.opporty.radar.features.events.registrations.EventRegistrationsRepository;
import com.opporty.radar.features.events.registrations.EventRegistrationsViewDTO;
import com.opporty.radar.features.events.registrations.RoleInEvent;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EventQrSessionsService {

    private final EventQrSessionsRepository eventQrSessionsRepository;
    private final EventsRepository eventsRepository;
    private final EventRegistrationsRepository eventRegistrationsRepository;
    private final EventQrSessionsMapper eventQrSessionsMapper;
    private final EventRegistrationsMapper eventRegistrationsMapper;

    @Transactional
    public QrSessionResponse generateQrSession(EventQrSessionsWriteDTO dto, Users creator) {
        Events event = eventsRepository.findById(dto.eventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Evento no encontrado con ID: " + dto.eventId()));

        // Un MANAGER solo puede generar QR de sus propios eventos.
        // ADMIN y TEACHER tienen acceso global.
        String roleName = creator.getRole() != null ? creator.getRole().getName() : "";
        boolean isAdmin = "ADMIN".equalsIgnoreCase(roleName);
        boolean isTeacher = "TEACHER".equalsIgnoreCase(roleName);
        boolean isOwner = event.getCreatedBy() != null && event.getCreatedBy().getId().equals(creator.getId());

        if (!isAdmin && !isTeacher && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para generar códigos QR de este evento. Solo el creador del evento o un administrador pueden hacerlo.");
        }

        QrSessionType sessionType;
        try {
            sessionType = QrSessionType.valueOf(dto.type().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de sesión QR inválido: " + dto.type());
        }

        // Deactivate all current active sessions of same type for this event
        List<EventQrSessions> activeSessions = eventQrSessionsRepository.findByEventAndTypeAndActiveTrue(event,
                sessionType);
        for (EventQrSessions activeSession : activeSessions) {
            activeSession.setActive(false);
        }
        eventQrSessionsRepository.saveAll(activeSessions);

        // Generate token and session details
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(dto.durationMinutes());

        EventQrSessions session = EventQrSessions.builder()
                .event(event)
                .type(sessionType)
                .token(token)
                .generatedAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .createdBy(creator)
                .build();

        EventQrSessions savedSession = eventQrSessionsRepository.save(session);

        // Evitar que el CRON sobreescriba un QR generado manualmente
        if (sessionType == QrSessionType.ENTRY) {
            event.setAutoQrEntryGenerated(true);
        } else if (sessionType == QrSessionType.EXIT) {
            event.setAutoQrExitGenerated(true);
        }
        eventsRepository.save(event);

        String qrImageBase64 = generateQrCodeBase64(token);

        EventQrSessionsViewDTO viewDto = eventQrSessionsMapper.toDt(savedSession);
        long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        return new QrSessionResponse(viewDto, qrImageBase64, remainingSeconds);
    }

    @Transactional
    public EventRegistrationsViewDTO scanQrCode(String token, Users user) {
        EventQrSessions session = eventQrSessionsRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Código QR inválido o expirado."));

        // Validate expiration
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setActive(false);
            eventQrSessionsRepository.save(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código QR ha expirado.");
        }

        // findByIdWithLock: bloqueo pesimista para serializar acceso al aforo
        // Garantiza que registros manuales y escaneos QR concurrentes no produzcan
        // sobreaforo
        Events event = eventsRepository.findByIdWithLock(session.getEvent().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado."));

        EventRegistrations registration = eventRegistrationsRepository.findByUserAndEvent(user, event).orElse(null);

        if (registration == null) {
            // El usuario NO está registrado: intentar auto-inscripción vía QR

            // Check if the event requires approval (manual enrollment only)
            if (event.isRequiresApproval()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Este evento requiere aprobación previa. Regístrate en la app primero y espera la aprobación.");
            }

            // Verificar aforo con bloqueo ya adquirido — ninguna otra transacción puede
            // modificar registros concurrentes hasta que esta transacción finalice
            if (event.getCapacidad() != null && event.getCapacidad() > 0) {
                long activeCount = eventRegistrationsRepository.countActiveRegistrationsByEvent(event);
                if (activeCount >= event.getCapacidad()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "El aforo de este evento ha sido completado. No es posible registrarte vía código QR.");
                }
            }

            // Auto-inscribir al usuario
            registration = EventRegistrations.builder()
                    .user(user)
                    .event(event)
                    .attendanceStatus(AttendanceStatus.REGISTERED)
                    .qrEntryScanned(false)
                    .qrExitScanned(false)
                    .attendanceCompleted(false)
                    .certificateGenerated(false)
                    .roleInEvent(RoleInEvent.ATTENDEE)
                    .notes("Auto-inscripción vía escaneo QR")
                    .build();
            registration = eventRegistrationsRepository.save(registration);

        } else {
            // El usuario YA está registrado: validar estado antes de procesar el QR

            if (registration.getAttendanceStatus() == AttendanceStatus.PENDING_APPROVAL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tu inscripción está pendiente de aprobación por el organizador.");
            }
            if (registration.getAttendanceStatus() == AttendanceStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tu inscripción a este evento ha sido rechazada.");
            }
            if (registration.getAttendanceStatus() == AttendanceStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tu inscripción a este evento ha sido cancelada.");
            }
        }

        // Process Attendance Scan
        if (session.getType() == QrSessionType.ENTRY) {
            if (registration.isQrEntryScanned()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya has escaneado tu Insignia de Inicio.");
            }
            registration.setAttendanceStatus(AttendanceStatus.CHECKED_IN);
            registration.setQrEntryScanned(true);
            registration.setCheckInAt(LocalDateTime.now());
        } else if (session.getType() == QrSessionType.EXIT) {
            if (!registration.isQrEntryScanned()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No puedes registrar salida sin haber registrado tu entrada previamente.");
            }
            if (registration.isQrExitScanned()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya has escaneado tu Insignia de Fin.");
            }
            registration.setAttendanceStatus(AttendanceStatus.COMPLETED);
            registration.setQrExitScanned(true);
            registration.setCheckOutAt(LocalDateTime.now());
            registration.setAttendanceCompleted(true);
            registration.setCertificateGenerated(true);
            registration.setCertificateUrl("https://radar.opporty.com/certificates/" + registration.getId());
        }

        EventRegistrations saved = eventRegistrationsRepository.save(registration);
        return eventRegistrationsMapper.toDt(saved);
    }

    @Transactional
    public QrSessionResponse getActiveSession(Long eventId, String typeStr) {
        Events event = eventsRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Evento no encontrado con ID: " + eventId));

        QrSessionType sessionType;
        try {
            sessionType = QrSessionType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de sesión QR inválido: " + typeStr);
        }

        List<EventQrSessions> activeSessions = eventQrSessionsRepository.findByEventAndTypeAndActiveTrue(event,
                sessionType);
        if (activeSessions.isEmpty()) {
            return null;
        }

        EventQrSessions session = activeSessions.get(0);
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setActive(false);
            eventQrSessionsRepository.save(session);
            return null;
        }

        String qrImageBase64 = generateQrCodeBase64(session.getToken());
        EventQrSessionsViewDTO viewDto = eventQrSessionsMapper.toDt(session);
        long remainingSeconds = java.time.Duration.between(LocalDateTime.now(), session.getExpiresAt()).getSeconds();
        return new QrSessionResponse(viewDto, qrImageBase64, remainingSeconds);
    }

    private String generateQrCodeBase64(String token) {
        try {
            int width = 300;
            int height = 300;
            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(token,
                    com.google.zxing.BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al generar el código QR utilizando ZXing.", e);
        }
    }
}
