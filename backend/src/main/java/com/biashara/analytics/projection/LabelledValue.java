package com.biashara.analytics.projection;

import java.math.BigDecimal;

/** A single categorical measure — the shape behind pie, donut and bar charts. */
public interface LabelledValue {

    String getLabel();

    BigDecimal getValue();

    Long getCount();
}
