package com.opporty.radar.features.auth.users;

import java.util.List;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.opporty.radar.features.auth.roles.Roles;
import com.opporty.radar.features.auth.roles.RolesRepository;
import com.opporty.radar.features.auth.students.StudentsRepository;
import com.opporty.radar.features.auth.teachers.TeachersRepository;
import com.opporty.radar.features.events.categories.EventCategories;
import com.opporty.radar.features.events.categories.EventCategoriesRepository;
import com.opporty.radar.features.events.categories.EventCategoriesMapper;
import com.opporty.radar.features.events.categories.EventCategoriesViewDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {
    private final UsersRepository userRepository;
    private final UsersMapper userMapper;
    private final StudentsRepository studentsRepository;
    private final TeachersRepository teachersRepository;
    private final RolesRepository rolesRepository;
    private final EventCategoriesRepository eventCategoriesRepository;
    private final EventCategoriesMapper eventCategoriesMapper;

    public List<UsersViewDTO> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toDt).toList();
    }

    @Transactional
    public UsersViewDTO updateUserStatus(Long id, String status) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado con ID: " + id));

        boolean isEnabled = "ACTIVE".equalsIgnoreCase(status);
        user.setEnabled(isEnabled);
        userRepository.save(user);

        // Sync with student profile if it exists
        var studentOpt = studentsRepository.findByUser(user);
        if (studentOpt.isPresent()) {
            var student = studentOpt.get();
            student.setStatus(status.toUpperCase());
            studentsRepository.save(student);
        }

        // Sync with teacher profile if it exists
        var teacherOpt = teachersRepository.findByUser(user);
        if (teacherOpt.isPresent()) {
            var teacher = teacherOpt.get();
            teacher.setStatus(status.toUpperCase());
            teachersRepository.save(teacher);
        }

        return userMapper.toDt(user);
    }

    @Transactional
    public UsersViewDTO updateUserRole(Long id, String roleName) {
        Users user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado con ID: " + id));

        if ("STUDENT".equalsIgnoreCase(user.getRole().getName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se puede modificar el rol de un estudiante");
        }

        if ("STUDENT".equalsIgnoreCase(roleName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se puede asignar el rol de estudiante a este usuario");
        }

        Roles newRole = rolesRepository.findByName(roleName.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Rol no encontrado: " + roleName));

        user.setRole(newRole);
        userRepository.save(user);

        return userMapper.toDt(user);
    }

    @Transactional(readOnly = true)
    public List<EventCategoriesViewDTO> getUserInterests(Users user) {
        Users persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return persistedUser.getInterests().stream()
                .map(eventCategoriesMapper::toDt)
                .toList();
    }

    @Transactional
    public List<EventCategoriesViewDTO> updateUserInterests(Users user, List<Long> categoryIds) {
        Users persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        List<EventCategories> categories = eventCategoriesRepository.findAllById(categoryIds);
        persistedUser.setInterests(new HashSet<>(categories));
        userRepository.save(persistedUser);

        return persistedUser.getInterests().stream()
                .map(eventCategoriesMapper::toDt)
                .toList();
    }

    @Transactional
    public void savePushToken(Users user, String token) {
        Users persistedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        persistedUser.setExpoPushToken(token);
        userRepository.save(persistedUser);
    }
}
