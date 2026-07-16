package com.opporty.radar;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.opporty.radar.features.auth.roles.Roles;
import com.opporty.radar.features.auth.roles.RolesRepository;
import com.opporty.radar.features.auth.users.Users;
import com.opporty.radar.features.auth.users.UsersRepository;
import com.opporty.radar.features.auth.teachers.Teachers;
import com.opporty.radar.features.auth.teachers.TeachersRepository;
import com.opporty.radar.features.events.categories.EventCategories;
import com.opporty.radar.features.events.categories.EventCategoriesRepository;
import com.opporty.radar.features.events.tags.Tags;
import com.opporty.radar.features.events.tags.TagsRepository;

@SpringBootApplication
@EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class RadarApplication {

	public static void main(String[] args) {
		SpringApplication.run(RadarApplication.class, args);
	}

	/**
	 * Seed inicial: crea el rol ADMIN y el usuario administrador si no existen.
	 * Credenciales por defecto:
	 * username : c2026000001
	 * password : Admin1234!
	 */
	@Bean
	CommandLineRunner initDatabase(
			RolesRepository rolesRepository,
			UsersRepository usersRepository,
			TeachersRepository teachersRepository,
			EventCategoriesRepository categoriesRepository,
			TagsRepository tagsRepository,
			PasswordEncoder passwordEncoder) {

		return args -> {
			try {
				// 1. Crear rol ADMIN si no existe
				Roles adminRole = rolesRepository.findByName("ADMIN")
						.orElseGet(() -> rolesRepository.save(
								Roles.builder()
										.name("ADMIN")
										.description("Administrador del sistema")
										.build()));

				// Intentar migrar/renombrar rol USER a MANAGER si existe (solo si MANAGER no existe aún)
				if (rolesRepository.findByName("MANAGER").isEmpty()) {
					rolesRepository.findByName("USER").ifPresent(userRole -> {
						userRole.setName("MANAGER");
						userRole.setDescription("Manager de Eventos");
						rolesRepository.save(userRole);
					});
				}

				// 2. Crear rol MANAGER si no existe
				rolesRepository.findByName("MANAGER")
						.orElseGet(() -> rolesRepository.save(
								Roles.builder()
										.name("MANAGER")
										.description("Manager de Eventos")
										.build()));

				// 3. Crear rol STUDENT si no existe

				rolesRepository.findByName("STUDENT")
						.orElseGet(() -> rolesRepository.save(
								Roles.builder()
										.name("STUDENT")
										.description("Estudian te")
										.build()));

				// 4. Crear rol TEACHER si no existe
				rolesRepository.findByName("TEACHER")
						.orElseGet(() -> rolesRepository.save(
								Roles.builder()
										.name("TEACHER")
										.description("Profesor / Docente")
										.build()));

				// 3. Crear usuario admin y asociarlo a un perfil de profesor si no existe o migrarlo
				var adminOpt = usersRepository.findByUsername("c2026000001");
				if (adminOpt.isEmpty()) {
					var oldAdminOpt = usersRepository.findByUsername("mr01019000");
					if (oldAdminOpt.isPresent()) {
						Users oldAdmin = oldAdminOpt.get();
						oldAdmin.setUsername("c2026000001");
						usersRepository.save(oldAdmin);
					} else if (!usersRepository.existsByEmail("admin@radar.com")) {
						Users adminUser = Users.builder()
								.username("c2026000001")
								.email("admin@radar.com")
								.password(passwordEncoder.encode("Admin1234!"))
								.enabled(true)
								.role(adminRole)
								.build();
						usersRepository.save(adminUser);

						Teachers adminTeacher = Teachers.builder()
								.nombres("Administrador")
								.apellidos("del Sistema")
								.titulo("Magíster / Director")
								.especialidad("Administración Educativa")
								.telefono("999999999")
								.dni("00000000")
								.fechaNacimiento(java.time.LocalDate.of(1990, 1, 1))
								.biography("Administrador principal del sistema Echo.")
								.status("ACTIVE")
								.hiringDate(java.time.LocalDate.of(2020, 1, 1))
								.user(adminUser)
								.build();
						teachersRepository.save(adminTeacher);
					}
				}

				// 5. Crear categorías y sus tags asociados si no existen en la BD
				if (categoriesRepository.count() == 0) {
					// Tecnología
					Tags tagInnovacion = saveTag(tagsRepository, "innovación");
					Tags tagStartup = saveTag(tagsRepository, "startup");
					Tags tagTecnologia = saveTag(tagsRepository, "tecnología");
					Tags tagDesarrollo = saveTag(tagsRepository, "desarrollo");
					Tags tagProgramacion = saveTag(tagsRepository, "programación");
					Tags tagIA = saveTag(tagsRepository, "ia");

					EventCategories catTec = EventCategories.builder()
							.nombre("Tecnología")
							.descripcion("Eventos tecnológicos, hackathons, charlas de desarrollo de software e inteligencia artificial.")
							.tags(new java.util.HashSet<>(java.util.Arrays.asList(tagInnovacion, tagStartup, tagTecnologia, tagDesarrollo, tagProgramacion, tagIA)))
							.build();
					categoriesRepository.save(catTec);

					// Música
					Tags tagFestival = saveTag(tagsRepository, "festival");
					Tags tagConcierto = saveTag(tagsRepository, "concierto");
					Tags tagNeonbeats = saveTag(tagsRepository, "neonbeats");
					Tags tagCultura = saveTag(tagsRepository, "cultura");
					Tags tagElectro = saveTag(tagsRepository, "electrónica");

					EventCategories catMus = EventCategories.builder()
							.nombre("Música")
							.descripcion("Festivales musicales, conciertos, bandas universitarias y espectáculos sonoros.")
							.tags(new java.util.HashSet<>(java.util.Arrays.asList(tagFestival, tagConcierto, tagNeonbeats, tagCultura, tagElectro)))
							.build();
					categoriesRepository.save(catMus);

					// Deporte
					Tags tagTorneo = saveTag(tagsRepository, "torneo");
					Tags tagEsports = saveTag(tagsRepository, "esports");
					Tags tagCompetencia = saveTag(tagsRepository, "competencia");
					Tags tagSaludable = saveTag(tagsRepository, "saludable");
					Tags tagFutbol = saveTag(tagsRepository, "fútbol");

					EventCategories catDep = EventCategories.builder()
							.nombre("Deporte")
							.descripcion("Torneos deportivos, campeonatos de fútbol, vóley, atletismo y ligas de eSports.")
							.tags(new java.util.HashSet<>(java.util.Arrays.asList(tagTorneo, tagEsports, tagCompetencia, tagSaludable, tagFutbol)))
							.build();
					categoriesRepository.save(catDep);

					// Social
					Tags tagComunidad = saveTag(tagsRepository, "comunidad");
					Tags tagFeria = saveTag(tagsRepository, "feria");
					Tags tagArte = saveTag(tagsRepository, "arte");
					Tags tagSensorial = saveTag(tagsRepository, "sensorial");

					EventCategories catSoc = EventCategories.builder()
							.nombre("Social")
							.descripcion("Feria de integración, eventos de voluntariado, asambleas y proyectos de impacto social.")
							.tags(new java.util.HashSet<>(java.util.Arrays.asList(tagComunidad, tagFeria, tagArte, tagSensorial)))
							.build();
					categoriesRepository.save(catSoc);

					// Cultural
					Tags tagExposicion = saveTag(tagsRepository, "exposición");
					Tags tagHistoria = saveTag(tagsRepository, "historia");
					Tags tagAprendizaje = saveTag(tagsRepository, "aprendizaje");
					Tags tagUniversitario = saveTag(tagsRepository, "universitario");

					EventCategories catCul = EventCategories.builder()
							.nombre("Cultural")
							.descripcion("Charlas de historia, muestras de pintura, obras teatrales, danzas tradicionales y folclore.")
							.tags(new java.util.HashSet<>(java.util.Arrays.asList(tagExposicion, tagHistoria, tagAprendizaje, tagUniversitario)))
							.build();
					categoriesRepository.save(catCul);
				}
			} catch (Exception e) {
				System.err.println("Advertencia: No se pudo inicializar la base de datos semilla: " + e.getMessage());
			}
		};
	}

	private Tags saveTag(TagsRepository tagsRepository, String nombre) {
		return tagsRepository.findByNombreIgnoreCase(nombre)
				.orElseGet(() -> tagsRepository.save(
						Tags.builder()
								.nombre(nombre)
								.build()));
	}
}
