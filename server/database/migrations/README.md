# Database migrations

The application currently does not run Flyway or Liquibase migrations automatically.
Apply `20260808_exam_configuration_and_attempts.sql` manually to the MySQL database before deploying this API version.

Recommended deployment order:

1. Back up the database.
2. Stop the old server version so Hibernate cannot recreate the former two-column unique indexes.
3. Run the migration with a database account that can alter tables, create indexes, and create temporary stored procedures.
4. Deploy and start the new server version.
5. Verify that `exam_recipients` exists and that the three submission tables have unique indexes on `(exam_id, student_id, attempt_number)`.

Do not run this migration twice: the column and index statements are intentionally written as a one-time migration.
