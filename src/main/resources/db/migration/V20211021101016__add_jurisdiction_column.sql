/*
 * Copyright notice:
 * Copyright 2021 ©, Compliance Solutions Strategies Holdings, LCC.
 * All rights reserved.
 * Not to be reproduced or distributed without express written consent of Compliance Solutions Strategies Holdings, LLC.
 */

alter table firds_data
    add jurisdiction varchar(2);

alter sequence firds_data_id_seq owned by firds_data.jurisdiction;

