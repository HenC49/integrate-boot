-- Idempotent seed data: every application context runs this script (sql.init.mode=always)
-- against the same JVM-wide H2 database, so re-running it must not duplicate rows.
MERGE INTO `user` (user_name, age) KEY (user_name) VALUES ('alice', 28);
MERGE INTO `user` (user_name, age) KEY (user_name) VALUES ('bob', 35);
