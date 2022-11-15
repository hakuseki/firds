/*
 * Copyright notice:
 * Copyright 2021 ©, Compliance Solutions Strategies Holdings, LCC.
 * All rights reserved.
 * Not to be reproduced or distributed without express written consent of Compliance Solutions Strategies Holdings, LLC.
 */

create index firds_data_isin_currency_venue_index
    on firds_data (isin, currency, venue);
