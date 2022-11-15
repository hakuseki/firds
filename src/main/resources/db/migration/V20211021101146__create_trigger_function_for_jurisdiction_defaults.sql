/*
 * Copyright notice:
 * Copyright 2021 ©, Compliance Solutions Strategies Holdings, LCC.
 * All rights reserved.
 * Not to be reproduced or distributed without express written consent of Compliance Solutions Strategies Holdings, LLC.
 */

CREATE OR REPLACE FUNCTION trigger_jurisdiction_defaults()
    RETURNS trigger
    LANGUAGE plpgsql AS
$func$
BEGIN
    NEW.jurisdiction := SUBSTRING(NEW.isin, 1, 2);
    RETURN NEW;
END;
$func$;



CREATE TRIGGER jurisdiction_defaults
    BEFORE INSERT
    ON firds_data
    FOR EACH ROW
    WHEN ( NEW.jurisdiction IS NULL AND NEW.isin IS NOT NULL)
EXECUTE PROCEDURE trigger_jurisdiction_defaults();
