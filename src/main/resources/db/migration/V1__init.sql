-- Table: public.firds_data

-- DROP TABLE public.firds_data;

CREATE TABLE public.firds_data
(
    isin character varying(12) COLLATE pg_catalog."default" NOT NULL,
    fullname character varying(100) COLLATE pg_catalog."default",
    currency character varying(3) COLLATE pg_catalog."default",
    maturity_date date,
    venue character varying(4) COLLATE pg_catalog."default",
    classification character varying(6) COLLATE pg_catalog."default",
    price_currency character varying(3) COLLATE pg_catalog."default",
    termination_date timestamp(6) without time zone
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

-- ALTER TABLE public.firds_data
--     OWNER to mgr;

-- Index: idx_firds_data_currencies_maturity

-- DROP INDEX public.idx_firds_data_currencies_maturity;

CREATE INDEX idx_firds_data_currencies_maturity
    ON public.firds_data USING btree
    (currency COLLATE pg_catalog."default", price_currency COLLATE pg_catalog."default", maturity_date)
    TABLESPACE pg_default;

-- Index: idx_firds_data_isin

-- DROP INDEX public.idx_firds_data_isin;

CREATE INDEX idx_firds_data_isin
    ON public.firds_data USING btree
    (isin COLLATE pg_catalog."default")
    TABLESPACE pg_default;
