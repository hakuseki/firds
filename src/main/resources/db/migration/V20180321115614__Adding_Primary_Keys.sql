ALTER TABLE public.firds_data
 ADD CONSTRAINT firds_data_isin_currency_price_currency_maturity_date_classification_pk UNIQUE (isin, currency, price_currency, maturity_date, classification);
