package com.opporty.radar.features.events.registrations;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("event-registrations")
@RequiredArgsConstructor
public class EventRegistrationsRestController {

    private final EventRegistrationsService eventRegistrationsService;

    public record UpdateStatusRequest(
            @NotBlank(message = "El estado no puede estar vacío") String status
    ) {}

    @GetMapping("/me")
    public ResponseEntity<List<EventRegistrationsViewDTO>> getMyRegistrations() {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        List<EventRegistrationsViewDTO> list = eventRegistrationsService.getRegistrationsByUser(currentUser);
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT, "No estás registrado en ningún evento");
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'MANAGER')")
    public ResponseEntity<List<EventRegistrationsViewDTO>> getByEvent(@PathVariable Long eventId) {
        List<EventRegistrationsViewDTO> list = eventRegistrationsService.getRegistrationsByEvent(eventId);
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT, "No hay registros para este evento");
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<EventRegistrationsViewDTO> register(@Valid @RequestBody EventRegistrationsWriteDTO dto) {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return ResponseEntity.ok(eventRegistrationsService.registerUser(dto, currentUser));
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> unregister(@PathVariable Long eventId) {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        eventRegistrationsService.unregisterUser(eventId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<EventRegistrationsViewDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(eventRegistrationsService.updateStatus(id, request.status()));
    }

    @PostMapping("{id}/certificate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<EventRegistrationsViewDTO> generateCertificate(@PathVariable Long id) {
        return ResponseEntity.ok(eventRegistrationsService.generateCertificate(id));
    }
}
