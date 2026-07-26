package com.biashara.analytics.projection;

import java.math.BigDecimal;

/**
 * One point on a time series. Spring Data projects aggregate query results onto
 * this interface, so trend charts are computed by the database rather than by
 * pulling rows into memory and looping.
 */
public interface DailySeriesPoint {

    /** ISO date, as returned by the database's date truncation. */
    String getBucket();

    BigDecimal getValue();

    BigDecimal getSecondary();

    Long getCount();
}
