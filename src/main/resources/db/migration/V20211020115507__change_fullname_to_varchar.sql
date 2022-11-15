/*
 * Copyright notice:
 * Copyright 2021 ©, Compliance Solutions Strategies Holdings, LCC.
 * All rights reserved.
 * Not to be reproduced or distributed without express written consent of Compliance Solutions Strategies Holdings, LLC.
 */

alter table firds_data alter column fullname type varchar using fullname::varchar;

alter table firds_data alter column issuer set not null;

