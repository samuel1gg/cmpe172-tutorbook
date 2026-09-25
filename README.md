# TutorBook: Tutoring Center Appointment System

**SJSU CMPE 172 Term Project, Milestone 1 (Requirements, Design & Skeleton)**
Author: Samuel Dinkayehu

TutorBook lets students (**customers**) browse and book tutoring sessions with tutors (**providers**). Milestone 1 is a running, layered skeleton: **Java 17+, Spring Boot, and plain SQL over JDBC (`JdbcClient`), with no ORM**. It loads its database from `schema.sql` and `seed.sql` on startup and serves read-only pages and JSON endpoints.

## How to run

**You need:** JDK 17 or newer (`java -version`). You do not need to install Maven or a database. The Maven wrapper (`mvnw`) downloads Maven, and the database is an embedded H2 created in memory.

```bash
git clone <this-repo-url>
cd cmpe172-tutorbook
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Then open **http://localhost:8080**.

Run the tests (11 tests, including the double-booking guard):

```bash
./mvnw test
```

## Endpoints (all read from the database)

| Method | URL | Returns |
|---|---|---|
| GET | `/` | Home page: counts, tutors, services (`HomeDto`) |
| GET | `/slots` | Available slots. Optional `providerId`, `serviceId`, `date=YYYY-MM-DD`, `page`, `size` (SQL `LIMIT`/`OFFSET`) |
| GET | `/slots/{id}/book` | Booking form for one open slot (submitting it is Milestone 2) |
| GET | `/appointments/{id}/confirmation` | Confirmation page for an appointment (try `/appointments/1/confirmation`) |
| GET | `/api/slots` | Same slot search as JSON: `PageDto<SlotDto>` |
| GET | `/api/slots/{id}` | One available slot as JSON |
| GET | `/api/providers` | `List<ProviderDto>` |
| GET | `/api/services` | `List<ServiceDto>` |
| GET | `/actuator/health` | Health check |
| GET | `/h2-console` | Database browser. JDBC URL `jdbc:h2:mem:tutorbook`, user `sa`, empty password |

## Architecture

```
Browser ──HTTP──▶ Controller ──▶ Service ──▶ Repository ──JDBC/SQL──▶ H2 database
 (Thymeleaf HTML   (@Controller /   (@Service:   (@Repository:
  or JSON)         @RestController)  logic,      JdbcClient +
                                     DTO mapping) RowMapper)
```

```
src/main/java/edu/sjsu/cmpe172/tutorbook/
├── controller/   HomeController, SlotController, AppointmentController (HTML, Page Controller)
│                 CatalogApiController (JSON)
├── service/      CatalogService, SlotService, AppointmentService
├── repository/   ProviderRepository, ServiceOfferingRepository, SlotRepository, AppointmentRepository
├── model/        Records that mirror database rows
├── dto/          Records sent to the view/JSON (HomeDto, SlotDto, PageDto, ...)
└── exception/    NotFoundException → HTTP 404
src/main/resources/
├── schema.sql    5 tables + constraints + the double-booking guard
├── seed.sql      sample users, providers, services, slots, appointments
├── templates/    Thymeleaf pages (home, slots, book, confirmation)
└── application.properties
```

## Double-booking guard

`appointments.active_slot_id` is a generated column. It equals `slot_id` while the appointment's status is `BOOKED` and is `NULL` once the appointment is `CANCELLED`. The table has `UNIQUE (active_slot_id)`, so the **database** allows at most one active booking per slot, even when two requests arrive at the same instant. Cancelled rows are kept as history, and the slot can be booked again. `DoubleBookingGuardTest` proves this.

## Seed accounts (logins arrive in Milestone 2)

| Role | Email | Password |
|---|---|---|
| Customer | alice@student.sjsu.edu | password123 |
| Provider | dr.patel@tutorbook.edu | password123 |
| Admin | admin@tutorbook.edu | admin123 |

## Roadmap

- **M1 (this):** design, schema, seed, layered read-only skeleton
- **M2:** features 1–8 (logins, filter/paginate, book, my appointments, cancel, provider slot management)
- **M3:** mock notification service, logging, health, metrics
- **M4:** AI booking agent, autonomous agent, grounded Q&A (RAG)

## Design documents

See [`docs/`](docs): [Milestone 1 Report](docs/Milestone1_Report.pdf) · [ER Diagram](docs/ER_Diagram.png) · [Relational Schema](docs/Relational_Schema.pdf) · [Wireframes](docs/Wireframes.pdf) · [Block Diagram](docs/Block_Diagram.png)
