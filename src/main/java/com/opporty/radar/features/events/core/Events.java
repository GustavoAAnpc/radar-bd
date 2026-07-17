package com.opporty.radar.features.events.core;

import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.events.categories.EventCategories;
import com.opporty.radar.features.events.tags.Tags;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Events {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(nullable = false, length = 255)
    @ToString.Include
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    private Integer capacidad;

    @Formula("(SELECT COUNT(er.id) FROM event_registrations er WHERE er.event_id = id AND er.attendance_status NOT IN ('CANCELLED', 'REJECTED', 'PENDING_APPROVAL'))")
    private Integer inscritosCount;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Modalidad modalidad;

    @Column(length = 255)
    private String lugar;

    @Column(length = 255)
    private String referencia;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitud;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Estado estado;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "edad_minima")
    private Integer edadMinima;

    @Column(columnDefinition = "TEXT")
    private String requisitos;

    @Column(name = "allow_qr_attendance", nullable = false)
    private boolean allowQrAttendance;

    @Column(name = "auto_qr_entry_generated", nullable = false)
    private boolean autoQrEntryGenerated;

    @Column(name = "auto_qr_exit_generated", nullable = false)
    private boolean autoQrExitGenerated;

    /** Multiple categories per event (join table: event_categories_map) */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_categories_map",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<EventCategories> categories = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Users createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_tags",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tags> tags = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventImages> images = new ArrayList<>();

    @Column(name = "grabacion_url", length = 500)
    private String grabacionUrl;

    @Builder.Default
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.opporty.radar.features.events.qr.EventQrSessions> qrSessions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.opporty.radar.features.events.registrations.EventRegistrations> registrations = new ArrayList<>();
}

