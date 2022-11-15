ALTER TABLE public.firds_data DROP CONSTRAINT firds_data_isin_currency_price_currency_maturity_date_classific;
ALTER TABLE public.firds_data
 ADD CONSTRAINT firds_data_isin_currency_price_currency_maturity_date_classific UNIQUE (isin, currency, price_currency, maturity_date, classification, venue);
