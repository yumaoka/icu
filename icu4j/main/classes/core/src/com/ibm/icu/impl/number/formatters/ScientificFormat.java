// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.formatters;

import com.ibm.icu.impl.number.Format;
import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.FormatQuantity2;
import com.ibm.icu.impl.number.ModifierHolder;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;
import com.ibm.icu.impl.number.modifiers.ConstantAffixModifier;
import com.ibm.icu.impl.number.modifiers.PositiveNegativeAffixModifier;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberFormat.Field;

public class ScientificFormat extends Format.BeforeFormat implements Rounder.MultiplierGenerator {

  public static interface IProperties extends Rounder.IProperties {

    static boolean DEFAULT_EXPONENT_SHOW_PLUS_SIGN = false;

    /** @see #setExponentShowPlusSign */
    public boolean getExponentShowPlusSign();

    /**
     * Sets whether to show the plus sign in the exponent part of numbers with a zero or positive
     * exponent. For example, the number "1200" with the pattern "0.0E0" would be formatted as
     * "1.2E+3" instead of "1.2E3" in <em>en-US</em>.
     *
     * @param exponentShowPlusSign Whether to show the plus sign in positive exponents.
     * @return The property bag, for chaining.
     */
    public IProperties setExponentShowPlusSign(boolean exponentShowPlusSign);

    static int DEFAULT_EXPONENT_DIGITS = 0;

    /** @see #setExponentDigits */
    public int getExponentDigits();

    /**
     * Sets the minimum number of digits to display in the exponent. For example, the number "1200"
     * with the pattern "0.0E00", which has 2 exponent digits, would be formatted as "1.2E03" in
     * <em>en-US</em>.
     *
     * @param exponentDigits The minimum number of digits to display in the exponent field.
     * @return The property bag, for chaining.
     */
    public IProperties setExponentDigits(int exponentDigits);

    public IProperties clone();
  }

  public static boolean useScientificNotation(IProperties properties) {
    return properties.getExponentDigits() != IProperties.DEFAULT_EXPONENT_DIGITS;
  }

  public static ScientificFormat getInstance(DecimalFormatSymbols symbols, IProperties properties) {
    if (properties.getExponentDigits() <= 0) {
      throw new IllegalArgumentException("Exponent digits must be greater than zero");
    }

    boolean exponentShowPlusSign = properties.getExponentShowPlusSign();
    int expDigits = properties.getExponentDigits();
    int minInt = properties.getMinimumIntegerDigits();
    int maxInt = properties.getMaximumIntegerDigits();
    int minFrac = properties.getMinimumFractionDigits();
    int maxFrac = properties.getMaximumFractionDigits();
    Rounder rounder;

    // Special case
    if (maxInt > 8) {
      maxInt = minInt;
    }

    // If significant digits or rounding interval are specified through normal means, we use those.
    // Otherwise, we use the special significant digit rules for scientific notation.
    if (Rounder.useRoundingInterval(properties)) {
      rounder = Rounder.IntervalRounder.getInstance(properties);
    } else if (Rounder.useSignificantDigits(properties)) {
      rounder = Rounder.SignificantDigitsRounder.getInstance(properties);
    } else {
      IProperties rprops = new Properties();
      rprops.setRoundingMode(properties.getRoundingMode());
      if (minInt == 0 && maxFrac == 0) {
        // Special case for the pattern "#E0" with no significant digits specified.
        rprops.setMinimumSignificantDigits(1);
        rprops.setMaximumSignificantDigits(Integer.MAX_VALUE);
      } else {
        rprops.setMinimumSignificantDigits(minInt + minFrac);
        rprops.setMaximumSignificantDigits(
            (maxFrac < Integer.MAX_VALUE) ? minInt + maxFrac : Integer.MAX_VALUE);
      }
      rprops.setMinimumIntegerDigits(maxInt == 0 ? 0 : Math.max(1, minInt + minFrac - maxFrac));
      rprops.setMaximumIntegerDigits(maxInt);
      rprops.setMinimumFractionDigits(Math.max(0, minFrac + minInt - maxInt));
      rprops.setMaximumFractionDigits(maxFrac);
      rounder = Rounder.SignificantDigitsRounder.getInstance(rprops);
    }

    return new ScientificFormat(symbols, exponentShowPlusSign, expDigits, minInt, maxInt, rounder);
  }

  // Properties
  private final boolean exponentShowPlusSign;
  private final int exponentDigits;
  private final int minimumIntegerDigits;
  private final int maximumIntegerDigits;
  private final int interval;
  private final Rounder rounder;
  private final ConstantAffixModifier separatorMod;
  private final PositiveNegativeAffixModifier signMod;

  // Symbols
  private final String[] digitStrings;

  public ScientificFormat(
      DecimalFormatSymbols symbols,
      boolean exponentShowPlusSign,
      int exponentDigits,
      int minimumIntegerDigits,
      int maximumIntegerDigits,
      Rounder rounder) {
    this.exponentShowPlusSign = exponentShowPlusSign;
    this.exponentDigits = exponentDigits;
    this.minimumIntegerDigits = minimumIntegerDigits;
    this.maximumIntegerDigits = maximumIntegerDigits;
    this.interval = Math.max(1, maximumIntegerDigits);
    this.rounder = rounder;
    digitStrings = symbols.getDigitStrings(); // makes a copy

    separatorMod =
        new ConstantAffixModifier("", symbols.getExponentSeparator(), Field.EXPONENT_SYMBOL, true);
    signMod =
        new PositiveNegativeAffixModifier(
            new ConstantAffixModifier(
                "", exponentShowPlusSign ? symbols.getPlusSignString() : "", Field.EXPONENT_SIGN, true),
            new ConstantAffixModifier("", symbols.getMinusSignString(), Field.EXPONENT_SIGN, true));
  }

  @Override
  public void before(FormatQuantity input, ModifierHolder mods) {
    int exponent;
    // Special case for zero handling
    if (input.isZero()) {
      exponent = 0;
      rounder.apply(input);
    } else {
      exponent = -rounder.chooseMultiplierAndApply(input, this);
    }

    // Format the exponent part of the scientific format.
    // Insert digits starting from the left so that append can be used.
    FormatQuantity exponentQ = new FormatQuantity2(exponent);
    StringBuilder exponentSB = new StringBuilder();
    exponentQ.setIntegerFractionLength(exponentDigits, Integer.MAX_VALUE, 0, 0);
    for (int i = exponentQ.integerCount() - 1; i >= 0; i--) {
      exponentSB.append(digitStrings[exponentQ.getIntegerDigit(i)]);
    }

    // Add modifiers from the outside in.
    mods.add(new ConstantAffixModifier("", exponentSB.toString(), Field.EXPONENT, true));
    mods.add(signMod.getModifier(exponent < 0));
    mods.add(separatorMod);
  }

  @Override
  public int getMultiplier(int magnitude) {
    int digitsShown = ((magnitude % interval + interval) % interval) + 1;
    if (digitsShown < minimumIntegerDigits) {
      digitsShown = minimumIntegerDigits;
    } else if (digitsShown > maximumIntegerDigits) {
      digitsShown = maximumIntegerDigits;
    }
    int retval = digitsShown - magnitude - 1;
    return retval;
  }

  @Override
  public void export(Properties properties) {
    properties.setExponentDigits(exponentDigits);
    properties.setExponentShowPlusSign(exponentShowPlusSign);

    // Set the transformed object into the property bag.  This may result in a pattern string that
    // uses different syntax from the original, but it will be functionally equivalent.
    rounder.export(properties);
  }
}
