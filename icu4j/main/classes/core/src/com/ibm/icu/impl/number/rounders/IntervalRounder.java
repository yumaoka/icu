// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.rounders;

import java.math.BigDecimal;

import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;

public class IntervalRounder extends Rounder {

  public static interface IProperties extends IBasicRoundingProperties {

    static BigDecimal DEFAULT_ROUNDING_INTERVAL = null;

    /** @see #setRoundingInterval */
    public BigDecimal getRoundingInterval();

    /**
     * Sets the interval to which to round numbers. For example, with a rounding interval of 0.05,
     * the number 11.17 would be formatted as "11.15" in locale <em>en-US</em> with the default
     * rounding mode.
     *
     * <p>You can use either a rounding interval or significant digits, but not both at the same
     * time.
     *
     * <p>The rounding interval can be specified in a pattern string. For example, the pattern
     * "#,##0.05" corresponds to a rounding interval of 0.05 with 1 minimum integer digit and a
     * grouping size of 3.
     *
     * @param roundingInterval The interval to which to round.
     * @return The property bag, for chaining.
     */
    public IProperties setRoundingInterval(BigDecimal roundingInterval);
  }

  public static boolean useRoundingInterval(IProperties properties) {
    return properties.getRoundingInterval() != IProperties.DEFAULT_ROUNDING_INTERVAL;
  }

  private final BigDecimal roundingInterval;

  public static IntervalRounder getInstance(IProperties properties) {
    return new IntervalRounder(properties);
  }

  private IntervalRounder(IProperties properties) {
    super(properties);
    if (properties.getRoundingInterval().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Rounding interval must be greater than zero");
    }
    roundingInterval = properties.getRoundingInterval();
  }

  @Override
  public void apply(FormatQuantity input) {
    input.roundToInterval(roundingInterval, mathContext);
    applyDefaults(input);
  }

  @Override
  public void export(Properties properties) {
    super.export(properties);
    properties.setRoundingInterval(roundingInterval);
  }
}
