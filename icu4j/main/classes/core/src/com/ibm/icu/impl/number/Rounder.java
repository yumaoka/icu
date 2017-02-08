// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ibm.icu.impl.number.formatters.CompactDecimalFormat;
import com.ibm.icu.impl.number.formatters.ScientificFormat;

/**
 * The base class for a Rounder used by ICU Decimal Format.
 *
 * <p>A Rounder must implement the method {@link #round}, and can optionally override the method
 * {@link #setIntegerAndFractionLength}.
 *
 * <p>In order to be used by {@link CompactDecimalFormat} and {@link ScientificFormat}, among
 * others, your rounder must be stable upon <em>decreasing</em> the magnitude of the input number.
 * For example, if your rounder converts "999" to "1000", it must also convert "99.9" to "100" and
 * "0.999" to "1". (The opposite does not need to be the case: you can round "0.999" to "1" but keep
 * "999" as "999".)
 */
public abstract class Rounder extends Format.BeforeFormat {

  public static interface IBasicRoundingProperties {

    static int DEFAULT_MINIMUM_INTEGER_DIGITS = 1;

    /** @see #setMinimumIntegerDigits */
    public int getMinimumIntegerDigits();

    /**
     * Sets the minimum number of digits to display before the decimal point. If the number has
     * fewer than this number of digits, the number will be padded with zeros. The pattern "#00.0#",
     * for example, corresponds to 2 minimum integer digits, and the number 5.3 would be formatted
     * as "05.3" in locale <em>en-US</em>.
     *
     * @param minimumIntegerDigits The minimum number of integer digits to output.
     * @return The property bag, for chaining.
     */
    public IProperties setMinimumIntegerDigits(int minimumIntegerDigits);

    static int DEFAULT_MAXIMUM_INTEGER_DIGITS = Integer.MAX_VALUE;

    /** @see #setMaximumIntegerDigits */
    public int getMaximumIntegerDigits();

    /**
     * Sets the maximum number of digits to display before the decimal point. If the number has more
     * than this number of digits, the extra digits will be truncated. For example, if maximum
     * integer digits is 2, and you attempt to format the number 1970, you will get "70" in locale
     * <em>en-US</em>. It is not possible to specify the maximum integer digits using a pattern
     * string, except in the special case of a scientific format pattern.
     *
     * @param maximumIntegerDigits The maximum number of integer digits to output.
     * @return The property bag, for chaining.
     */
    public IProperties setMaximumIntegerDigits(int maximumIntegerDigits);

    static int DEFAULT_MINIMUM_FRACTION_DIGITS = 0;

    /** @see #setMinimumFractionDigits */
    public int getMinimumFractionDigits();

    /**
     * Sets the minimum number of digits to display after the decimal point. If the number has fewer
     * than this number of digits, the number will be padded with zeros. The pattern "#00.0#", for
     * example, corresponds to 1 minimum fraction digit, and the number 456 would be formatted as
     * "456.0" in locale <em>en-US</em>.
     *
     * @param minimumFractionDigits The minimum number of fraction digits to output.
     * @return The property bag, for chaining.
     */
    public IProperties setMinimumFractionDigits(int minimumFractionDigits);

    static int DEFAULT_MAXIMUM_FRACTION_DIGITS = Integer.MAX_VALUE;

    /** @see #setMaximumFractionDigits */
    public int getMaximumFractionDigits();

    /**
     * Sets the maximum number of digits to display after the decimal point. If the number has fewer
     * than this number of digits, the number will be rounded off using the rounding mode specified
     * by {@link #setRoundingMode(RoundingMode)}. The pattern "#00.0#", for example, corresponds to
     * 2 maximum fraction digits, and the number 456.789 would be formatted as "456.79" in locale
     * <em>en-US</em> with the default rounding mode. Note that the number 456.999 would be
     * formatted as "457.0" given the same configurations.
     *
     * @param maximumFractionDigits The maximum number of fraction digits to output.
     * @return The property bag, for chaining.
     */
    public IProperties setMaximumFractionDigits(int maximumFractionDigits);

    static RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    /** @see #setRoundingMode */
    public RoundingMode getRoundingMode();

    /**
     * Sets the rounding mode, which determines under which conditions extra decimal places are
     * rounded either up or down. See {@link RoundingMode} for details on the choices of rounding
     * mode. The default if not set explicitly is {@link RoundingMode#HALF_EVEN}.
     *
     * @param roundingMode The rounding mode to use when rounding is required.
     * @return The property bag, for chaining.
     * @see RoundingMode
     */
    public IProperties setRoundingMode(RoundingMode roundingMode);
  }

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
     * Sets the maximum number of significant digits to display. The number of significant digits
     * is equal to the number of digits counted from the leftmost nonzero digit through the
     * rightmost nonzero digit; for example, the number "2010" has 3 significant digits. If the
     * number has more significant digits than specified here, the extra significant digits will be
     * rounded off using the rounding mode specified by {@link #setRoundingMode(RoundingMode)}. For
     * example, if maximum significant digits is 3, the number 1234.56 will be formatted as "1230"
     * in locale <em>en-US</em> with the default rounding mode.
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

  public static boolean useFractionFormat(IProperties properties) {
    return properties.getMinimumFractionDigits() != IProperties.DEFAULT_MINIMUM_FRACTION_DIGITS
        || properties.getMaximumFractionDigits() != IProperties.DEFAULT_MAXIMUM_FRACTION_DIGITS;
  }

  public static boolean useSignificantDigits(IProperties properties) {
    return properties.getMinimumSignificantDigits()
            != IProperties.DEFAULT_MINIMUM_SIGNIFICANT_DIGITS
        || properties.getMaximumSignificantDigits()
            != IProperties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS;
  }

  public static boolean useRoundingInterval(IProperties properties) {
    return properties.getRoundingInterval() != IProperties.DEFAULT_ROUNDING_INTERVAL;
  }

  public static interface MultiplierGenerator {
    public int getMultiplier(int magnitude);
  }

  // Properties available to all rounding strategies
  protected final RoundingMode roundingMode;
  protected final int minimumIntegerDigits;
  protected final int maximumIntegerDigits;
  protected final int minimumFractionDigits;
  protected final int maximumFractionDigits;

  public static Rounder getDefaultRounder(IProperties properties) {
    Rounder candidate = getDefaultRounderOrNull(properties);
    if (candidate == null) {
      candidate = NoRounder.getInstance(properties);
    }
    return candidate;
  }

  public static Rounder getDefaultRounderOrNull(IProperties properties) {
    if (useSignificantDigits(properties)) {
      return SignificantDigitsRounder.getInstance(properties);
    } else if (useRoundingInterval(properties)) {
      return IntervalRounder.getInstance(properties);
    } else if (useFractionFormat(properties)) {
      return MagnitudeRounder.getInstance(properties);
    } else {
      return null;
    }
  }

  /**
   * Constructor that uses integer and fraction digit lengths from IBasicRoundingProperties.
   *
   * @param properties
   */
  protected Rounder(IBasicRoundingProperties properties) {
    roundingMode = properties.getRoundingMode();
    minimumIntegerDigits = properties.getMinimumIntegerDigits();
    maximumIntegerDigits = properties.getMaximumIntegerDigits();
    minimumFractionDigits = properties.getMinimumFractionDigits();
    maximumFractionDigits = properties.getMaximumFractionDigits();
  }

  /**
   * Constructor that uses an explicit rounding mode but default values for integer and fraction
   * digit lengths.
   *
   * @param roundingMode
   */
  protected Rounder(RoundingMode roundingMode) {
    this.roundingMode = roundingMode;
    minimumIntegerDigits = IBasicRoundingProperties.DEFAULT_MINIMUM_INTEGER_DIGITS;
    maximumIntegerDigits = IBasicRoundingProperties.DEFAULT_MAXIMUM_INTEGER_DIGITS;
    minimumFractionDigits = IBasicRoundingProperties.DEFAULT_MINIMUM_FRACTION_DIGITS;
    maximumFractionDigits = IBasicRoundingProperties.DEFAULT_MAXIMUM_FRACTION_DIGITS;
  }

  /** Constructor for implementations that wish to set custom values to the final fields. */
  protected Rounder(int minInt, int maxInt, int minFrac, int maxFrac, RoundingMode roundingMode) {
    this.roundingMode = roundingMode;
    minimumIntegerDigits = minInt;
    maximumIntegerDigits = maxInt;
    minimumFractionDigits = minFrac;
    maximumFractionDigits = maxFrac;
  }

  /**
   * Sets the integer and fraction length based on the properties, but does not perform rounding.
   */
  public static final class NoRounder extends Rounder {
    public static NoRounder getInstance(IBasicRoundingProperties properties) {
      return new NoRounder(properties);
    }

    private NoRounder(IBasicRoundingProperties properties) {
      super(properties);
    }

    @Override
    protected void round(FormatQuantity input) {}
  }

  public static class SignificantDigitsRounder extends Rounder {
    private final int minimumSignificantDigits;
    private final int maximumSignificantDigits;

    public static SignificantDigitsRounder getInstance(IProperties properties) {
      return new SignificantDigitsRounder(properties);
    }

    private SignificantDigitsRounder(IProperties properties) {
      super(properties);
      if (properties.getMinimumSignificantDigits() > properties.getMaximumSignificantDigits()) {
        throw new IllegalArgumentException(
            "Minimum significant digits must be less than or equal to maximum significant digits");
      }
      if (properties.getMinimumSignificantDigits() < 0) {
        throw new IllegalArgumentException("Minimum significant digits must be at least zero");
      }
      minimumSignificantDigits = properties.getMinimumSignificantDigits();
      maximumSignificantDigits = properties.getMaximumSignificantDigits();
    }

    @Override
    protected void setIntegerAndFractionLength(FormatQuantity input) {
      // In significant digits, obey the minima but not the maxima.  The significant digit counts
      // control how many digits are to be displayed.
      // Special case: a value of zero bypasses the significant digit format.
      if (input.isZero()) {
        input.setIntegerFractionLength(
            minimumIntegerDigits,
            maximumIntegerDigits,
            Math.max(minimumFractionDigits, minimumSignificantDigits - minimumIntegerDigits),
            maximumFractionDigits);
      } else {
        input.setIntegerFractionLength(
            minimumIntegerDigits, Integer.MAX_VALUE, minimumFractionDigits, Integer.MAX_VALUE);
      }
    }

    @Override
    protected void round(FormatQuantity input) {
      if (!input.isZero()) {
        input.roundToSignificantDigits(
            minimumSignificantDigits, maximumSignificantDigits, roundingMode);
      }
    }

    @Override
    public void export(Properties properties) {
      super.export(properties);
      properties.setMinimumSignificantDigits(minimumSignificantDigits);
      properties.setMaximumSignificantDigits(maximumSignificantDigits);
    }
  }

  public static class IntervalRounder extends Rounder {
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
    protected void round(FormatQuantity input) {
      input.roundToInterval(roundingInterval, roundingMode);
    }

    @Override
    public void export(Properties properties) {
      super.export(properties);
      properties.setRoundingInterval(roundingInterval);
    }
  }

  public static class MagnitudeRounder extends Rounder {

    public static MagnitudeRounder getInstance(IBasicRoundingProperties properties) {
      return new MagnitudeRounder(properties);
    }

    private MagnitudeRounder(IBasicRoundingProperties properties) {
      super(properties);
    }

    @Override
    protected void round(FormatQuantity input) {
      input.roundToMagnitude(-maximumFractionDigits, roundingMode);
    }
  }

  /**
   * Perform rounding and specification of integer and fraction digit lengths on the input quantity.
   * Calling this method will change the state of the FormatQuantity.
   *
   * @param input The {@link FormatQuantity} to be modified and rounded.
   */
  public void apply(FormatQuantity input) {
    setIntegerAndFractionLength(input);
    round(input);
  }

  /**
   * Specify the integer and fraction digit lengths on the input quantity. This method should change
   * the internal state of the quantity.
   *
   * @param input The {@link FormatQuantity} to be modified.
   */
  protected void setIntegerAndFractionLength(FormatQuantity input) {
    input.setIntegerFractionLength(
        minimumIntegerDigits, maximumIntegerDigits, minimumFractionDigits, maximumFractionDigits);
  }

  /**
   * Perform rounding on the input quantity. This method should change the internal state of the
   * quantity.
   *
   * @param input The {@link FormatQuantity} to be rounded.
   */
  protected abstract void round(FormatQuantity input);

  /**
   * Rounding can affect the magnitude. First we attempt to adjust according to the original
   * magnitude, and if the magnitude changes, we adjust according to a magnitude one greater. Note
   * that this algorithm assumes that increasing the multiplier never increases the number of digits
   * that can be displayed.
   *
   * @param input The quantity to be rounded.
   * @param mg The implementation that returns magnitude adjustment based on a given starting
   *     magnitude.
   * @return The multiplier that was chosen to best fit the input.
   */
  public int chooseMultiplierAndApply(FormatQuantity input, MultiplierGenerator mg) {
    FormatQuantity copy = input.clone();

    int magnitude = input.getMagnitude();
    int multiplier = mg.getMultiplier(magnitude);
    input.adjustMagnitude(multiplier);
    apply(input);
    if (input.getMagnitude() == magnitude + multiplier + 1) {
      magnitude += 1;
      input.copyFrom(copy);
      multiplier = mg.getMultiplier(magnitude);
      input.adjustMagnitude(multiplier);
      assert input.getMagnitude() == magnitude + multiplier - 1;
      apply(input);
      assert input.getMagnitude() == magnitude + multiplier;
    }

    return multiplier;
  }

  @Override
  public void before(FormatQuantity input, ModifierHolder mods) {
    apply(input);
  }

  @Override
  public void export(Properties properties) {
    properties.setRoundingMode(roundingMode);
    properties.setMinimumFractionDigits(minimumFractionDigits);
    properties.setMinimumIntegerDigits(minimumIntegerDigits);
    properties.setMaximumFractionDigits(maximumFractionDigits);
    properties.setMaximumIntegerDigits(maximumIntegerDigits);
  }
}
