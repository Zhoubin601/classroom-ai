-- Existing databases: apply before deploying US-03. New development schemas are also created by JPA.
CREATE TABLE IF NOT EXISTS t_academic_term_lock (
    academic_term VARCHAR(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
