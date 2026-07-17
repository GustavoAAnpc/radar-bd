package com.opporty.radar.features.events.core;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.events.categories.EventCategories;
import com.opporty.radar.features.events.categories.EventCategoriesRepository;
import com.opporty.radar.features.events.tags.Tags;
import com.opporty.radar.features.events.tags.TagsRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EventsService {

    private final EventsRepository eventsRepository;
    private final EventCategoriesRepository eventCategoriesRepository;
    private final TagsRepository tagsRepository;
    private final EventsMapper eventsMapper;
    private final com.opporty.radar.features.notifications.NotificationsService notificationsService;

    @Transactional(readOnly = true)
    public UpdateCheckDTO checkUpdates() {
        long eventsCount = eventsRepository.count();
        return new UpdateCheckDTO(
                eventsCount,
                java.time.LocalDateTime.now(),
                0L,
                java.time.LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<EventsViewDTO> getAllEvents() {
        return eventsRepository.findAll()
                .stream()
                .map(eventsMapper::toDt)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventsViewDTO getEventById(Long id) {
        Events event = eventsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado con ID: " + id));
        return eventsMapper.toDt(event);
    }

    @Transactional
    public EventsViewDTO createEvent(EventsWriteDTO dto, Users createdBy) {
        if (dto.fechaFin().isBefore(dto.fechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        // Resolve multiple categories
        Set<EventCategories> categories = new HashSet<>(eventCategoriesRepository.findAllById(dto.categoryIds()));
        if (categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron las categorías con los IDs proporcionados.");
        }

        Events event = eventsMapper.toEntity(dto);
        event.setCategories(categories);
        event.setCreatedBy(createdBy);

        // Rule for MANAGER role
        if (createdBy.getRole() != null && "MANAGER".equals(createdBy.getRole().getName())) {
            event.setEstado(Estado.PENDING);
            event.setMotivoRechazo(null);
        }

        // Resolve tags
        if (dto.tagIds() != null && !dto.tagIds().isEmpty()) {
            Set<Tags> tags = new HashSet<>(tagsRepository.findAllById(dto.tagIds()));
            event.setTags(tags);
        }

        // Resolve images: first image also populates imagenUrl as the main image
        if (dto.imageUrls() != null && !dto.imageUrls().isEmpty()) {
            if (event.getImagenUrl() == null || event.getImagenUrl().isBlank()) {
                event.setImagenUrl(dto.imageUrls().get(0));
            }
            List<EventImages> images = dto.imageUrls().stream()
                    .map(url -> EventImages.builder().event(event).imageUrl(url).build())
                    .collect(Collectors.toList());
            event.setImages(images);
        }

        Events savedEvent = eventsRepository.save(event);

        if (savedEvent.getEstado() == Estado.PENDING) {
            notificationsService.notifyAdmins(
                    "Solicitud de Aprobación",
                    "El manager @" + createdBy.getUsername() + " ha solicitado publicar '" + savedEvent.getTitulo() + "'.",
                    savedEvent.getId()
            );
        }

        // Notificar a usuarios interesados si el evento se publica directamente (ADMIN/TEACHER)
        if (savedEvent.getEstado() == Estado.PUBLISHED || savedEvent.getEstado() == Estado.SCHEDULED) {
            notificationsService.notifyInterestedUsers(savedEvent);
        }

        return eventsMapper.toDt(savedEvent);
    }

    @Transactional
    public EventsViewDTO updateEvent(Long id, EventsWriteDTO dto, Users currentUser) {
        Events event = eventsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado con ID: " + id));
        Estado oldState = event.getEstado();

        if (dto.fechaFin().isBefore(dto.fechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        // Parse enums
        Modalidad modality;
        try {
            modality = Modalidad.valueOf(dto.modalidad().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidad inválida: " + dto.modalidad());
        }

        Estado state;
        try {
            state = Estado.valueOf(dto.estado().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido: " + dto.estado());
        }

        // Rules for Roles:
        if (currentUser.getRole() != null && "MANAGER".equals(currentUser.getRole().getName())) {
            if (event.getEstado() == Estado.SCHEDULED && 
                (state == Estado.PUBLISHED || state == Estado.SUSPENDED || state == Estado.CANCELLED)) {
                // Permitir al manager iniciar, suspender o cancelar un evento programado sin volver a PENDING
                event.setMotivoRechazo(null);
            } else if (state == Estado.PUBLISHED || state == Estado.PENDING || state == Estado.FINISHED) {
                state = Estado.PENDING;
                event.setMotivoRechazo(null);
            } else {
                state = Estado.DRAFT;
            }
        } else {
            if (state == Estado.REJECTED) {
                event.setMotivoRechazo(dto.motivoRechazo());
            } else {
                event.setMotivoRechazo(null);
            }
        }

        // Update scalar fields
        event.setTitulo(dto.titulo());
        event.setDescripcion(dto.descripcion());
        event.setFechaInicio(dto.fechaInicio());
        event.setFechaFin(dto.fechaFin());
        event.setHoraInicio(dto.horaInicio());
        event.setHoraFin(dto.horaFin());
        event.setCapacidad(dto.capacidad());
        event.setImagenUrl(dto.imagenUrl());
        event.setModalidad(modality);
        event.setLugar(dto.lugar());
        event.setReferencia(dto.referencia());
        event.setLatitud(dto.latitud());
        event.setLongitud(dto.longitud());
        event.setEstado(state);
        event.setRequiresApproval(dto.requiresApproval());
        event.setAllowQrAttendance(dto.allowQrAttendance());
        event.setEdadMinima(dto.edadMinima());
        event.setRequisitos(dto.requisitos());
        event.setGrabacionUrl(dto.grabacionUrl());

        // Update categories
        if (dto.categoryIds() != null && !dto.categoryIds().isEmpty()) {
            Set<EventCategories> categories = new HashSet<>(eventCategoriesRepository.findAllById(dto.categoryIds()));
            event.setCategories(categories);
        } else {
            event.getCategories().clear();
        }

        // Update tags
        if (dto.tagIds() != null) {
            Set<Tags> tags = new HashSet<>(tagsRepository.findAllById(dto.tagIds()));
            event.setTags(tags);
        } else {
            event.getTags().clear();
        }

        // Update images (clear old ones and add new ones)
        event.getImages().clear();
        if (dto.imageUrls() != null && !dto.imageUrls().isEmpty()) {
            // First image becomes the main image if not explicitly set
            if (event.getImagenUrl() == null || event.getImagenUrl().isBlank()) {
                event.setImagenUrl(dto.imageUrls().get(0));
            }
            List<EventImages> images = dto.imageUrls().stream()
                    .map(url -> EventImages.builder().event(event).imageUrl(url).build())
                    .collect(Collectors.toList());
            event.getImages().addAll(images);
        }

        String newState = state.toString();
        Events updatedEvent = eventsRepository.save(event);

        if (oldState == Estado.PENDING && ("PUBLISHED".equals(newState) || "SCHEDULED".equals(newState) || "FINISHED".equals(newState) || "REJECTED".equals(newState))) {
            boolean isApproved = !"REJECTED".equals(newState);
            String title = isApproved ? "Evento Aprobado 🎉" : "Evento Rechazado ❌";
            String msg = "Tu evento '" + updatedEvent.getTitulo() + "' ha sido " + (isApproved ? "aprobado." : "rechazado.");
            notificationsService.createNotification(updatedEvent.getCreatedBy(), title, msg, updatedEvent.getId());

            // Si fue aprobado y está publicado o programado, notificar a usuarios interesados
            if (isApproved && ("PUBLISHED".equals(newState) || "SCHEDULED".equals(newState))) {
                notificationsService.notifyInterestedUsers(updatedEvent);
            }
        }

        if (oldState != Estado.PENDING && "PENDING".equals(newState)) {
            notificationsService.notifyAdmins(
                    "Correcciones Enviadas",
                    "El manager @" + currentUser.getUsername() + " ha reenviado el evento '" + updatedEvent.getTitulo() + "' para su revisión.",
                    updatedEvent.getId()
            );
        }

        // Notificar inscritos si el evento fue cancelado o suspendido
        if (state == Estado.CANCELLED && oldState != Estado.CANCELLED) {
            notificationsService.notifyEventAttendees(updatedEvent,
                    "❌ Evento Cancelado",
                    "El evento '" + updatedEvent.getTitulo() + "' ha sido cancelado.");
        }
        if (state == Estado.SUSPENDED && oldState != Estado.SUSPENDED) {
            notificationsService.notifyEventAttendees(updatedEvent,
                    "⚠️ Evento Suspendido",
                    "El evento '" + updatedEvent.getTitulo() + "' ha sido suspendido temporalmente.");
        }

        return eventsMapper.toDt(updatedEvent);
    }

    @Transactional
    public void deleteEventById(Long id) {
        if (!eventsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado con ID: " + id);
        }
        eventsRepository.deleteById(id);
    }
}
