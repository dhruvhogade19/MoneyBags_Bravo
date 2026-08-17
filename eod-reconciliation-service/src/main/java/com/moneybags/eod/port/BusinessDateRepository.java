package com.moneybags.eod.port;

import com.moneybags.eod.domain.EodDomain.BusinessDateState;
import java.util.Optional;

public interface BusinessDateRepository {
    Optional<BusinessDateState> current();
    BusinessDateState save(BusinessDateState state);
}
