# TutorBook: Tutoring Center Appointment System

**SJSU CMPE 172 Term Project, Milestone 2 (Booking & Concurrency)**
Author: Samuel Dinkayehu

TutorBook lets students (**customers**) browse and book tutoring sessions with tutors (**providers**). It is built with **Java 17+, Spring Boot, and plain SQL over JDBC (`JdbcClient`), with no ORM**, plus Spring Security session login and Thymeleaf pages.

Milestone 2 adds the full booking workflow: a role-based login with BCrypt passwords, booking, owner-only cancel, my appointments, and provider slot management. Bookings are safe under concurrency through a `@Transactional` booking with an **optimistic version check and retry**, backed by a database **unique constraint**. The Milestone 1 version is tagged [`milestone-1`](../../tree/milestone-1).

## How to run

**You need:** JDK 17 or newer (`java -version`). You do not need to install Maven or a database. The Maven wrapper (`mvnw`) downloads Maven, and the database is an embedded H2 created in memory.

```bash
git clone <this-repo-url>
cd cmpe172-tutorbook
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Then open **http://localhost:8080**.

Run the tests (61 tests, including the two-thread concurrency test):

```bash
./mvnw test
```

## Log in

| Role | Email | Password |
|---|---|---|
| Customer | alice@student.sjsu.edu (also bob@, chen@) | password123 |
| Provider | dr.patel@tutorbook.edu (also j.kim@, m.garcia@, d.okafor@) | password123 |

Passwords are stored as BCrypt hashes. On login, the role is read from the `users` row over JDBC and kept in the server-side session.

## Endpoints

| Method | URL | Who | What |
|---|---|---|---|
| GET | `/` | anyone | Home page |
| GET | `/slots` | anyone | Available slots. Filter by `providerId`, `serviceId`, `date`; paginate with `page`, `size` (SQL `LIMIT`/`OFFSET`) |
| GET / POST | `/slots/{id}/book` | customer | Booking form / book the slot |
| GET | `/appointments/{id}/confirmation` | the appointment's customer or provider | Confirmation |
| GET | `/my/appointments` | customer | Upcoming + history (BOOKED / CANCELLED / COMPLETED) |
| POST | `/my/appointments/{id}/cancel` | owner only | Cancel |
| GET / POST | `/provider/slots` | provider | My slots / create a slot for a service |
| POST | `/provider/slots/{id}/remove` | owning provider | Remove an unbooked slot |
| GET | `/provider/appointments` | provider | Appointments booked with me |
| GET | `/api/slots`, `/api/providers`, `/api/services` | anyone | JSON |
| GET | `/actuator/health` | anyone | Health check |

**Status codes:** `400` invalid input · `403` wrong role or not the owner · `404` not found · `409` slot conflict. Errors never include stack traces.

## Concurrency design (double-booking)

1. `BookingService.book()` loops for up to 3 attempts.
2. Each attempt is `BookingTransaction.tryBook()`, which is `@Transactional(isolation = READ_COMMITTED)`. It:
   - reads the slot and its `version`;
   - checks the rules (exists, in the future, not booked);
   - runs `UPDATE availability_slots SET version = version + 1 WHERE slot_id = ? AND version = ?`. If 0 rows change, someone else got there first, so it throws `OptimisticConflictException`, which triggers a retry;
   - inserts the appointment.
3. On retry, the slot now reads as booked, so the request returns **409**.
4. Backstop: `UNIQUE(active_slot_id)` on `appointments` makes a second active booking impossible, even if the application logic had a bug.

`ConcurrentBookingTest` runs 2 threads (and 8 threads) booking the same slot at the same instant and asserts that **exactly one succeeds**.

## Architecture

```
Browser ──HTTP──▶ Controller ──▶ Service ──▶ Repository ──JDBC/SQL──▶ H2 database
 (Thymeleaf HTML   (@Controller /   (@Service:   (@Repository:
  or JSON)         @RestController)  logic,      JdbcClient +
                                     DTO mapping) RowMapper)
```

```
src/main/java/edu/sjsu/cmpe172/tutorbook/
├── config/       SecurityConfig (login, RBAC, BCrypt), TimeConfig
├── security/     AppUser, JdbcUserDetailsService (loads users over JDBC)
├── controller/   Home, Slot, Appointment, Provider, Login controllers (HTML, Page Controller)
│                 CatalogApiController (JSON)
├── service/      BookingService (retry) + BookingTransaction (@Transactional), AppointmentService,
│                 ProviderSlotService, CatalogService, SlotService, AppointmentStatusJob
├── repository/   User, Provider, ServiceOffering, Slot, Appointment repositories (SQL)
├── model/        Records that mirror database rows
├── dto/          Records sent to the view/JSON (HomeDto, SlotDto, PageDto, ...)
└── exception/    GlobalExceptionHandler (HTML) + ApiExceptionHandler (JSON) → 400/403/404/409
src/main/resources/
├── schema.sql    5 tables + constraints + the double-booking guard
├── seed.sql      sample users, providers, services, slots, appointments
├── templates/    Thymeleaf pages (home, slots, book, confirmation)
└── application.properties
```

## Double-booking guard

`appointments.active_slot_id` is a generated column. It equals `slot_id` while the appointment's status is `BOOKED` and is `NULL` once the appointment is `CANCELLED`. The table has `UNIQUE (active_slot_id)`, so the **database** allows at most one active booking per slot, even when two requests arrive at the same instant. Cancelled rows are kept as history, and the slot can be booked again. `DoubleBookingGuardTest` proves this. Since Milestone 2 this constraint is the backstop behind the optimistic version check.

## Roadmap

- **M1:** design, schema, seed, layered read-only skeleton (tag `milestone-1`)
- **M2 (this):** features 1–8 (logins, filter/paginate, book, my appointments, cancel, provider slot management)
- **M3:** mock notification service, logging, health, metrics
- **M4:** AI booking agent, autonomous agent, grounded Q&A (RAG)

## Design documents

See [`docs/`](docs): **[Milestone 2 Report](docs/Milestone2_Report.pdf)** · [Race condition diagram](docs/Race_Condition.png) · [Milestone 1 Report](docs/Milestone1_Report.pdf) · [ER Diagram](docs/ER_Diagram.png) · [Relational Schema](docs/Relational_Schema.pdf) · [Wireframes](docs/Wireframes.pdf) · [Block Diagram](docs/Block_Diagram.png)
