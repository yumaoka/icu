// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.rounders;

import java.math.RoundingMode;

import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;

public class SignificantDigitsRounder extends Rounder {

  /**
   * Sets whether the minimum significant digits should override the maximum integer and fraction
   * digits. This affects both display and rounding. Default is true.
   *
   * <p>For example, if this option is enabled, formatting the number 4.567 with 3 min/max
   * significant digits against the pattern "0.0" (1 min/max fraction digits) will result in "4.57"
   * in locale <em>en-US</em> with the default rounding mode. If this option is disabled, the max
   * fraction digits take priority instead, and the output will be "4.6".
   *
   * @param significantDigitsOverride true to ensure that the minimum significant digits are always
   *     shown; false to ensure that the maximum integer and fraction digits are obeyed.
   * @return The property bag, for chaining.
   */
  public static enum SignificantDigitsMode {
    OVERRIDE_MAXIMUM_FRACTION,
    RESPECT_MAXIMUM_FRACTION,
    ENSURE_MINIMUM_SIGNIFICANT
  };

  public static interface IProperties extends IBasicRoundingProperties {

    static int DEFAULT_MINIMUM_SIGNIFICANT_DIGITS = -1;

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

    static int DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS = -1;

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

    static SignificantDigitsMode DEFAULT_SIGNIFICANT_DIGITS_MODE = null;

    /** @see #setSignificantDigitsMode */
    public SignificantDigitsMode getSignificantDigitsMode();

    /**
     * Sets the strategy used when reconciling significant digits versus integer and fraction
     * lengths.
     *
     * @param significantDigitsMode One of the options from {@link SignificantDigitsMode}.
     * @return The property bag, for chaining.
     */
    public IProperties setSignificantDigitsMode(SignificantDigitsMode significantDigitsMode);
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
  private final SignificantDigitsMode mode;

  private SignificantDigitsRounder(IProperties properties) {
    super(properties);
    int _minSig = properties.getMinimumSignificantDigits();
    int _maxSig = properties.getMaximumSignificantDigits();
    minSig = _minSig < 1 ? 1 : _minSig;
    maxSig = _maxSig < 0 ? Integer.MAX_VALUE : _maxSig < minSig ? minSig : _maxSig;
    SignificantDigitsMode _mode = properties.getSignificantDigitsMode();
    mode = _mode == null ? SignificantDigitsMode.OVERRIDE_MAXIMUM_FRACTION : _mode;
  }

  @Override
  public void apply(FormatQuantity input) {

    int magnitude;
    if (input.isZero()) {
      // Treat zero as if magnitude corresponded to the minimum number of zeros
      magnitude = minInt - 1;
    } else {
      magnitude = input.getMagnitude();
    }

    int magMinSig = magnitude - minSig + 1;
    int magMaxSig = magnitude - maxSig + 1;
    int magMinFrac = -minFrac;
    int magMaxFrac = -maxFrac;
    int magMaxInt = maxInt - maxSig; // maxSig digits to the right of maxInt
    int roundingMagnitude;
    switch (mode) {
      case OVERRIDE_MAXIMUM_FRACTION:
        // Always round to maxSig.
        roundingMagnitude = magMaxSig;
        break;
      case RESPECT_MAXIMUM_FRACTION:
        // Round to the strongest of maxFrac, maxInt, and maxSig.
        // Math.max() picks the rounding magnitude farthest to the left (most significant).
        // Math.min() picks the rounding magnitude farthest to the right (least significant).
        roundingMagnitude = Math.max(magMaxFrac, Math.min(magMaxInt, magMaxSig));
        break;
      case ENSURE_MINIMUM_SIGNIFICANT:
        // Round to the strongest of maxFrac and maxSig, and always ensure minSig.
        roundingMagnitude =
            Math.min(magMinFrac, Math.min(magMinSig, Math.max(magMaxFrac, magMaxSig)));
        break;
      default:
        throw new AssertionError();
    }

    input.roundToMagnitude(roundingMagnitude, mathContext);

    // In case magnitude changed:
    if (input.isZero()) {
      magnitude = minInt - 1;
    } else {
      magnitude = input.getMagnitude();
    }

    switch (mode) {
      case OVERRIDE_MAXIMUM_FRACTION:
        // Ensure minSig is always displayed.
        input.setIntegerFractionLength(
            Math.max(minInt, magnitude),
            Integer.MAX_VALUE,
            Math.max(minFrac, minSig - magnitude - 1),
            Integer.MAX_VALUE);
        break;
      case RESPECT_MAXIMUM_FRACTION:
        // Ensure minSig is displayed, unless doing so is in violation of maxInt or maxFrac.
        input.setIntegerFractionLength(
            Math.min(maxInt, Math.max(minInt, magnitude)),
            maxInt,
            Math.min(maxFrac, Math.max(minFrac, minSig - magnitude - 1)),
            maxFrac);
        break;
      case ENSURE_MINIMUM_SIGNIFICANT:
        // Follow minInt/minFrac, but ensure all digits are allowed to be visible.
        input.setIntegerFractionLength(minInt, Integer.MAX_VALUE, minFrac, Integer.MAX_VALUE);
        break;
    }
  }

  @Override
  public void export(Properties properties) {
    super.export(properties);
    properties.setMinimumSignificantDigits(minSig);
    properties.setMaximumSignificantDigits(maxSig);
    properties.setSignificantDigitsMode(mode);
  }
}
