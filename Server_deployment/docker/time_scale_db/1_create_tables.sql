DROP TABLE IF EXISTS "time_series";
DROP TABLE IF EXISTS "measured_variables";

CREATE TABLE "measured_variables"
(
    measured_variable_id SERIAL PRIMARY KEY,
    name TEXT,
    unit TEXT,
    UNIQUE(name)
);

CREATE TABLE "time_series"
(
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    period DOUBLE PRECISION,
    qmin INTEGER,
    qmax INTEGER,
    observations_name TEXT,
    UNIQUE(observations_name),
    measured_variable_id INTEGER REFERENCES measured_variables(measured_variable_id) ON DELETE RESTRICT,
    time_series_id SERIAL PRIMARY KEY
);
