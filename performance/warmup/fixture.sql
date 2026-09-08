-- Only used in the new, dedicated benchmark database after schema bootstrap.
INSERT INTO show_scheduled (id, show_id, title, venue_name, start_at, poster_img)
VALUES (1, 1, 'Warmup fixture', 'Local venue', '2026-10-01 19:00:00', 'fixture');
INSERT INTO seat_section (id, venue_id, name, floor, grade_name, price)
VALUES (1, 1, 'A', 1, 'VIP', 100000), (2, 1, 'B', 1, 'R', 80000);
INSERT INTO seat (id, seat_section_id, seat_row, seat_number)
WITH RECURSIVE numbers AS (
    SELECT 1 AS n UNION ALL SELECT n + 1 FROM numbers WHERE n < 200
)
SELECT n, IF(n <= 100, 1, 2), CHAR(65 + FLOOR(MOD(n - 1, 100) / 10)), MOD(n - 1, 10) + 1
FROM numbers;
INSERT INTO scheduled_seat (id, seat_id, show_schedule_id, status, version)
SELECT id, id, 1, 'AVAILABLE', 0 FROM seat ORDER BY id;
