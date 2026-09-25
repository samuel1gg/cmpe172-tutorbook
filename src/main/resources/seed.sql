-- =====================================================================
-- TutorBook - seed.sql : sample users, providers, services, slots.
-- Slot times are relative to CURRENT_DATE so the demo always shows
-- upcoming slots no matter when the app is started.
-- Passwords use the {noop} prefix for now; Milestone 2 (login) switches
-- them to BCrypt hashes.
-- =====================================================================

-- Users: 3 customers, 4 providers, 1 admin
INSERT INTO users (user_id, email, password_hash, full_name, role) VALUES
 (1, 'alice@student.sjsu.edu',  '{noop}password123', 'Alice Nguyen',     'CUSTOMER'),
 (2, 'bob@student.sjsu.edu',    '{noop}password123', 'Bob Martinez',     'CUSTOMER'),
 (3, 'chen@student.sjsu.edu',   '{noop}password123', 'Chen Li',          'CUSTOMER'),
 (4, 'dr.patel@tutorbook.edu',  '{noop}password123', 'Priya Patel',      'PROVIDER'),
 (5, 'j.kim@tutorbook.edu',     '{noop}password123', 'James Kim',        'PROVIDER'),
 (6, 'm.garcia@tutorbook.edu',  '{noop}password123', 'Maria Garcia',     'PROVIDER'),
 (7, 'd.okafor@tutorbook.edu',  '{noop}password123', 'David Okafor',     'PROVIDER'),
 (8, 'admin@tutorbook.edu',     '{noop}admin123',    'Center Admin',     'ADMIN');

-- Provider profiles (1:1 with a PROVIDER user)
INSERT INTO providers (provider_id, user_id, display_name, specialty, bio) VALUES
 (1, 4, 'Dr. Priya Patel', 'Mathematics',       'PhD in Applied Math. Calculus, linear algebra and discrete math.'),
 (2, 5, 'James Kim',       'Computer Science',  'Senior software engineer. Java, data structures and Spring Boot.'),
 (3, 6, 'Maria Garcia',    'Physics',           'Physics lecturer. Mechanics and electricity & magnetism.'),
 (4, 7, 'David Okafor',    'Writing',           'Writing center lead. Essays, lab reports and resumes.');

-- Service catalog
INSERT INTO services (service_id, name, description, duration_minutes, price_cents) VALUES
 (1, 'Calculus Tutoring',        'Limits, derivatives, integrals and series.',         60, 4000),
 (2, 'Java Programming Help',    'Debugging, OOP and data structures in Java.',        60, 4500),
 (3, 'Physics Problem Session',  'Worked problems for mechanics and E&M.',             45, 3500),
 (4, 'Essay Review',             'Structure, clarity and grammar feedback.',           30, 2500),
 (5, 'Exam Prep Crash Course',   'Intensive review before a midterm or final.',        90, 6000);

-- Availability slots over the next 7 days
INSERT INTO availability_slots (slot_id, provider_id, service_id, start_time, end_time) VALUES
 ( 1, 1, 1, DATEADD('HOUR',  9, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 10, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP))),
 ( 2, 1, 1, DATEADD('HOUR', 11, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 12, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP))),
 ( 3, 1, 5, DATEADD('HOUR', 14, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 930, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP))),
 ( 4, 2, 2, DATEADD('HOUR', 10, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 11, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP))),
 ( 5, 2, 2, DATEADD('HOUR', 13, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 14, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP))),
 ( 6, 2, 5, DATEADD('HOUR', 16, CAST(DATEADD('DAY', 3, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 1050, CAST(DATEADD('DAY', 3, CURRENT_DATE) AS TIMESTAMP))),
 ( 7, 3, 3, DATEADD('HOUR',  9, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 585, CAST(DATEADD('DAY', 2, CURRENT_DATE) AS TIMESTAMP))),
 ( 8, 3, 3, DATEADD('HOUR', 15, CAST(DATEADD('DAY', 4, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 945, CAST(DATEADD('DAY', 4, CURRENT_DATE) AS TIMESTAMP))),
 ( 9, 4, 4, DATEADD('HOUR', 10, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 630, CAST(DATEADD('DAY', 1, CURRENT_DATE) AS TIMESTAMP))),
 (10, 4, 4, DATEADD('HOUR', 11, CAST(DATEADD('DAY', 3, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 690, CAST(DATEADD('DAY', 3, CURRENT_DATE) AS TIMESTAMP))),
 (11, 1, 1, DATEADD('HOUR', 13, CAST(DATEADD('DAY', 5, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 14, CAST(DATEADD('DAY', 5, CURRENT_DATE) AS TIMESTAMP))),
 (12, 2, 2, DATEADD('HOUR', 10, CAST(DATEADD('DAY', 6, CURRENT_DATE) AS TIMESTAMP)), DATEADD('HOUR', 11, CAST(DATEADD('DAY', 6, CURRENT_DATE) AS TIMESTAMP))),
 (13, 3, 5, DATEADD('HOUR', 12, CAST(DATEADD('DAY', 6, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 810, CAST(DATEADD('DAY', 6, CURRENT_DATE) AS TIMESTAMP))),
 (14, 4, 4, DATEADD('HOUR', 14, CAST(DATEADD('DAY', 7, CURRENT_DATE) AS TIMESTAMP)), DATEADD('MINUTE', 870, CAST(DATEADD('DAY', 7, CURRENT_DATE) AS TIMESTAMP)));

-- Existing bookings: slot 2 is actively booked (so it is hidden from
-- "available slots"); slot 5 was booked then cancelled (so it is
-- available again - this demonstrates the double-booking guard).
INSERT INTO appointments (appointment_id, slot_id, customer_id, status, notes, cancelled_at) VALUES
 (1, 2, 1, 'BOOKED',    'Need help with integration by parts.', NULL),
 (2, 5, 2, 'CANCELLED', 'Recursion questions.',                 CURRENT_TIMESTAMP),
 (3, 9, 3, 'BOOKED',    'Review of my English 1A essay.',       NULL);

-- Explicit ids were used above, so move each identity counter past them.
ALTER TABLE users              ALTER COLUMN user_id        RESTART WITH 100;
ALTER TABLE providers          ALTER COLUMN provider_id    RESTART WITH 100;
ALTER TABLE services           ALTER COLUMN service_id     RESTART WITH 100;
ALTER TABLE availability_slots ALTER COLUMN slot_id        RESTART WITH 100;
ALTER TABLE appointments       ALTER COLUMN appointment_id RESTART WITH 100;
