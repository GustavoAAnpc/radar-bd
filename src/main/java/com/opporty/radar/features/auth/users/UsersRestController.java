package com.opporty.radar.features.auth.users;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.opporty.radar.security.SecurityUtils;
import com.opporty.radar.features.events.categories.EventCategoriesViewDTO;

@RestController
@RequestMapping("users")
@RequiredArgsConstructor
public class UsersRestController {
    private final UsersService usersService;

    public record UpdateStatusRequest(
            @NotBlank(message = "El estado no puede estar vacío") String status
    ) {}

    @GetMapping
    public ResponseEntity<List<UsersViewDTO>> list() throws ResponseStatusException {
        List<UsersViewDTO> list = usersService.getAllUsers();
        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NO_CONTENT, "No data");
        }
        return ResponseEntity.ok(list);
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "El rol no puede estar vacío") String role
    ) {}

    @PutMapping("{id}/status")
    public ResponseEntity<UsersViewDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(usersService.updateUserStatus(id, request.status()));
    }

    @PutMapping("{id}/role")
    public ResponseEntity<UsersViewDTO> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(usersService.updateUserRole(id, request.role()));
    }

    public record UpdateInterestsRequest(
            List<Long> categoryIds
    ) {}

    @GetMapping("me/interests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventCategoriesViewDTO>> getMyInterests() {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return ResponseEntity.ok(usersService.getUserInterests(currentUser));
    }

    @PutMapping("me/interests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EventCategoriesViewDTO>> updateMyInterests(
            @Valid @RequestBody UpdateInterestsRequest request) {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return ResponseEntity.ok(usersService.updateUserInterests(currentUser, request.categoryIds()));
    }

    public record PushTokenRequest(
            @NotBlank(message = "El token no puede estar vacío") String token
    ) {}

    @PutMapping("me/push-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updatePushToken(@Valid @RequestBody PushTokenRequest request) {
        Users currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        usersService.savePushToken(currentUser, request.token());
        return ResponseEntity.ok().build();
    }
}
