package com.opporty.radar.features.events.core;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventsRepository extends JpaRepository<Events, Long> {
    List<Events> findByEstado(Estado estado);

    /**
     * Obtiene el evento con bloqueo pesimista (SELECT FOR UPDATE).
     * Úsalo al verificar + modificar aforo para evitar race conditions
     * entre registros manuales y escaneos QR concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Events e WHERE e.id = :id")
    Optional<Events> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT e FROM Events e WHERE e.fechaInicio = :date AND e.estado IN :estados")
    List<Events> findByFechaInicioAndEstadoIn(@Param("date") java.time.LocalDate date, @Param("estados") List<Estado> estados);
}
