-- svc-of-atm-directory: the ATM directory, owned by this service (schema sc_of_atm_directory).
-- Public reference data, no personal data. Rows are written by the seed (dev/CI only)
-- and by db/import/import-atms.sh; the service itself only reads them.

CREATE TABLE atm (
    atm_id         VARCHAR(64)  NOT NULL,
    name           VARCHAR(140) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    address_line   VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    country_code   VARCHAR(2)   NOT NULL,
    accessibility  VARCHAR(64)  NOT NULL,
    services       TEXT[]       NOT NULL,
    currency       VARCHAR(3)   NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_atm PRIMARY KEY (atm_id),
    CONSTRAINT ck_atm_id_not_blank CHECK (btrim(atm_id) <> ''),
    CONSTRAINT ck_atm_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_atm_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_atm_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_atm_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_atm_services_not_empty CHECK (cardinality(services) > 0),
    CONSTRAINT ck_atm_version CHECK (version >= 0)
);

-- Radius search (GET /atms?lat&long&radius): bounding-box pre-filter
-- point(longitude, latitude) <@ box(...) is answered from this GiST index.
CREATE INDEX ix_atm_location ON atm USING gist (point(longitude, latitude));

-- Directory browsing by place and availability (country/city, status).
CREATE INDEX ix_atm_country_city ON atm (country_code, city);
CREATE INDEX ix_atm_status ON atm (status);

COMMENT ON TABLE atm IS 'ATM directory (open data). Authority: svc-of-atm-directory. See ADR-0001.';
COMMENT ON COLUMN atm.version IS 'Incremented by db/import/import-atms.sh whenever an imported row changes.';
