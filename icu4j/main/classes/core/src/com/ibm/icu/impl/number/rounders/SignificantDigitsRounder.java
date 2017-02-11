// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.rounders;

import java.math.RoundingMode;

import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;

public class SignificantDigitsRounder extends Rounder {

  public static interface IProperties extends IBasicRoundingProperties {

    static int DEFAULT_MINIMUM_SIGNIFICANT_DIGITS = 1;

    /** @see #setMinimumSignificantDigits */
    public int getMinimumSignificantDigits();

    /**
     * Sets the minimum number of significant digits to display. If, after rounding to the number of
     * significant digits specified by {@link #setMaximumSignificantDigits}, the number of remaining
     * significant digits is less than the minimum, the number will be padded with zeros. For
     * example, if minimum significant digits is 3, the number 5.8 will be formatted as "5.80" in
     * locale <em>en-US</em>. Note that minimum significant digits is relevant only when numbers
     * have digits after the decimal point.
     *
     * <p>If both minimum significant digits and minimum integer/fraction digits are set at the same
     * time, both values will be respected, and the one that results in the greater number of
     * padding zeros will be used. For example, formatting the number 73 with 3 minimum significant
     * digits and 2 minimum fraction digits will produce "73.00".
     *
     * <p>The number of significant digits can be specified in a pattern string using the '@'
     * character. For example, the pattern "@@#" corresponds to a minimum of 2 and a maximum of 3
     * significant digits.
     *
     * @param minimumSignificantDigits The minimum number of significant digits to display.
     * @return The property bag, for chaining.
     */
    public IProperties setMinimumSignificantDigits(int minimumSignificantDigits);

    static int DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS = Integer.MAX_VALUE;

    /** @see #setMaximumSignificantDigits */
    public int getMaximumSignificantDigits();

    /**
     * Sets the maximum number of significant digits to display. The number of significant digits is
     * equal to the number of digits counted from the leftmost nonzero digit through the rightmost
     * nonzero digit; for example, the number "2010" has 3 significant digits. If the number has
     * more significant digits than specified here, the extra significant digits will be rounded off
     * using the rounding mode specified by {@link #setRoundingMode(RoundingMode)}. For example, if
     * maximum significant digits is 3, the number 1234.56 will be formatted as "1230" in locale
     * <em>en-US</em> with the default rounding mode.
     *
     * <p>If both maximum significant digits and maximum integer/fraction digits are set at the same
     * time, the behavior is undefined.
     *
     * <p>The number of significant digits can be specified in a pattern string using the '@'
     * character. For example, the pattern "@@#" corresponds to a minimum of 2 and a maximum of 3
     * significant digits.
     *
     * @param maximumSignificantDigits The maximum number of significant digits to display.
     * @return The property bag, for chaining.
     */
    public IProperties setMaximumSignificantDigits(int maximumSignificantDigits);

    static boolean DEFAULT_SIGNIFICANT_DIGITS_OVERRIDE_MAXIMUM_DIGITS = true;

    /** @see #setSignificantDigitsOverride */
    public boolean getSignificantDigitsOverride();

    /**
     * Sets whether the minimum significant digits should override the maximum integer and fraction
     * digits. This affects both display and rounding. Default is true.
     *
     * <p>For example, if this option is enabled, formatting the number 4.567 with 3 min/max
     * significant digits against the pattern "0.0" (1 min/max fraction digits) will result in
     * "4.57" in locale <em>en-US</em> with the default rounding mode. If this option is disabled,
     * the max fraction digits take priority instead, and the output will be "4.6".
     *
     * @param significantDigitsOverride true to ensure that the minimum significant digits are
     *     always shown; false to ensure that the maximum integer and fraction digits are obeyed.
     * @return The property bag, for chaining.
     */
    public IProperties setSignificantDigitsOverride(boolean significantDigitsOverride);
  }

  public static boolean useSignificantDigits(IProperties properties) {
    return properties.getMinimumSignificantDigits()
            != IProperties.DEFAULT_MINIMUM_SIGNIFICANT_DIGITS
        || properties.getMaximumSignificantDigits()
            != IProperties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS;
  }

  public static SignificantDigitsRounder getInstance(IProperties properties) {
    return new SignificantDigitsRounder(properties);
  }

  private final int minSig;
  private final int maxSig;
  private final boolean override;

  private SignificantDigitsRounder(IProperties properties) {
    super(properties);
    // TODO: Throw an exception on invalid input or fail silently?
    maxSig = Math.max(1, properties.getMaximumSignificantDigits());
    minSig = Math.min(maxSig, Math.max(1, properties.getMinimumSignificantDigits()));
    override = properties.getSignificantDigitsOverride();
  }

  @Override
  public void apply(FormatQuantity input) {

    int magnitude;
    if (input.isZero()) {
      // Treat zero as if magnitude corresponded to the minimum number of zeros
      magnitude = minInt - 1;
    } else {
      magnitude = input.getMagnitude();
      if (maxSig < Integer.MAX_VALUE) {
        int roundingMagnitude;
        if (override) {
          // Always round to maxSig.
          roundingMagnitude = magnitude - maxSig + 1;
        } else {
          // Round to the strongest of maxFrac, maxInt, and maxSig.
          roundingMagnitude = Math.max(-maxFrac, Math.min(maxInt - maxSig, magnitude - maxSig + 1));
        }
        input.roundToMagnitude(roundingMagnitude, mathContext);
        magnitude = input.getMagnitude(); // in case magnitude changed
      }
    }

    if (override) {
      // Ensure minSig is always displayed.
      input.setIntegerFractionLength(
          Math.max(minInt, magnitude),
          Integer.MAX_VALUE,
          Math.max(minFrac, minSig - magnitude - 1),
          Integer.MAX_VALUE);
    } else {
      // Ensure minSig is displayed, unless doing so is in violation of maxInt or maxFrac.
      input.setIntegerFractionLength(
          Math.min(maxInt, Math.max(minInt, magnitude)),
          maxInt,
          Math.min(maxFrac, Math.max(minFrac, minSig - magnitude - 1)),
          maxFrac);
    }
  }

  @Override
  public void export(Properties properties) {
    super.export(properties);
    properties.setMinimumSignificantDigits(minSig);
    properties.setMaximumSignificantDigits(maxSig);
  }
}
