// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.text;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.AttributedCharacterIterator;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;

import com.ibm.icu.impl.number.Endpoint;
import com.ibm.icu.impl.number.Format.SingularFormat;
import com.ibm.icu.impl.number.FormatQuantity2;
import com.ibm.icu.impl.number.Parse;
import com.ibm.icu.impl.number.PatternString;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;
import com.ibm.icu.impl.number.formatters.PaddingFormat.PaddingLocation;
import com.ibm.icu.impl.number.formatters.PositiveDecimalFormat;
import com.ibm.icu.impl.number.formatters.ScientificFormat;
import com.ibm.icu.math.BigDecimal;
import com.ibm.icu.math.MathContext;
import com.ibm.icu.text.PluralRules.IFixedDecimal;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.Currency.CurrencyUsage;
import com.ibm.icu.util.CurrencyAmount;

/** @author sffc */
public class DecimalFormat extends NumberFormat {

  private final Properties properties;
  private SingularFormat formatter;
  private DecimalFormatSymbols symbols;

  /** @stable ICU 2.0 */
  public DecimalFormat() {
    symbols = getDefaultSymbols();
    properties = new Properties();
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public DecimalFormat(String pattern) {
    symbols = getDefaultSymbols();
    properties = new Properties();
    setPropertiesFromPattern(pattern);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public DecimalFormat(String pattern, DecimalFormatSymbols symbols) {
    this.symbols = (DecimalFormatSymbols) symbols.clone();
    properties = new Properties();
    setPropertiesFromPattern(pattern);
    refreshFormatter();
  }

  /** @stable ICU 4.2 */
  public DecimalFormat(
      String pattern, DecimalFormatSymbols symbols, CurrencyPluralInfo infoInput, int style) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  private DecimalFormat(DecimalFormat other) {
    symbols = (DecimalFormatSymbols) other.symbols.clone();
    properties = other.properties.clone();
    refreshFormatter();
  }

  /** Package-private constructor used by NumberFormat. */
  DecimalFormat(String pattern, DecimalFormatSymbols symbols, int choice) {
    this.symbols = (DecimalFormatSymbols) symbols.clone();
    properties = new Properties();
    setPropertiesFromPattern(pattern);
    // FIXME: Deal with choice
//    switch (choice) {
//      case NumberFormat.PLURALCURRENCYSTYLE:
//        properties.setCurrencyStyle(CurrencyStyle.PLURAL);
//        break;
//    }
    refreshFormatter();
  }

  private static DecimalFormatSymbols getDefaultSymbols() {
    return DecimalFormatSymbols.getInstance();
  }

  /** @stable ICU 2.0 */
  public synchronized void applyPattern(String pattern) {
    setPropertiesFromPattern(pattern);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized void applyLocalizedPattern(String pattern) {
    // TODO(sffc): What exactly is the difference between a "localized" pattern and a regular pattern?
    setPropertiesFromPattern(pattern);
    refreshFormatter();
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 2.0
   */
  @Override
  public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition) {
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(long number, StringBuffer result, FieldPosition fieldPosition) {
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(BigInteger number, StringBuffer result, FieldPosition fieldPosition) {
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(
      java.math.BigDecimal number, StringBuffer result, FieldPosition fieldPosition) {
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(BigDecimal number, StringBuffer result, FieldPosition fieldPosition) {
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public Number parse(String text, ParsePosition parsePosition) {
    try {
      return Parse.parse(text, parsePosition, properties, symbols);
    } catch (ParseException e) {
      return null;
    }
  }

  /** @stable ICU 49 */
  @Override
  public CurrencyAmount parseCurrency(CharSequence text, ParsePosition pos) {
    // TODO(sffc): Implement fieldPosition
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 3.6 */
  @Override
  public synchronized AttributedCharacterIterator formatToCharacterIterator(Object obj) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a copy of the decimal format symbols used by this format.
   *
   * @return desired DecimalFormatSymbols
   * @see DecimalFormatSymbols
   * @stable ICU 2.0
   */
  public synchronized DecimalFormatSymbols getDecimalFormatSymbols() {
    return (DecimalFormatSymbols) symbols.clone();
  }

  /**
   * Sets the decimal format symbols used by this format. The format uses a copy of the provided
   * symbols.
   *
   * @param newSymbols desired DecimalFormatSymbols
   * @see DecimalFormatSymbols
   * @stable ICU 2.0
   */
  public synchronized void setDecimalFormatSymbols(DecimalFormatSymbols newSymbols) {
    symbols = (DecimalFormatSymbols) newSymbols.clone();
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized String getPositivePrefix() {
    CharSequence result = properties.getPositivePrefix();
    return (result == null) ? "" : result.toString();
  }

  /** @stable ICU 2.0 */
  public synchronized void setPositivePrefix(String newValue) {
    properties.setPositivePrefix(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized String getNegativePrefix() {
    CharSequence result = properties.getNegativePrefix();
    return (result == null) ? "" : result.toString();
  }

  /** @stable ICU 2.0 */
  public synchronized void setNegativePrefix(String newValue) {
    properties.setNegativePrefix(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized String getPositiveSuffix() {
    CharSequence result = properties.getPositiveSuffix();
    return (result == null) ? "" : result.toString();
  }

  /** @stable ICU 2.0 */
  public synchronized void setPositiveSuffix(String newValue) {
    properties.setPositiveSuffix(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized String getNegativeSuffix() {
    CharSequence result = properties.getNegativeSuffix();
    return (result == null) ? "" : result.toString();
  }

  /** @stable ICU 2.0 */
  public synchronized void setNegativeSuffix(String newValue) {
    properties.setNegativeSuffix(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized int getMultiplier() {
    if (properties.getMultiplier() != null) {
      return properties.getMultiplier().intValue();
    } else {
      return (int) Math.pow(10, properties.getMagnitudeMultiplier());
    }
  }

  /** @stable ICU 2.0 */
  public synchronized void setMultiplier(int newValue) {
    assert newValue > 0;
    // Try to convert to a magnitude multiplier first
    int delta = 0;
    int value = newValue;
    while (newValue != 1) {
      delta++;
      int temp = value / 10;
      if (temp * 10 != value) {
        delta = -1;
        break;
      }
      value = temp;
    }
    if (delta != -1) {
      properties.setMagnitudeMultiplier(delta);
    } else {
      properties.setMultiplier(java.math.BigDecimal.valueOf(newValue));
    }
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized java.math.BigDecimal getRoundingIncrement() {
    return properties.getRoundingInterval();
  }

  /** @stable ICU 2.0 */
  public synchronized void setRoundingIncrement(java.math.BigDecimal newValue) {
    properties.setRoundingInterval(newValue);
    refreshFormatter();
  }

  /** @stable ICU 3.6 */
  public synchronized void setRoundingIncrement(BigDecimal newValue) {
    java.math.BigDecimal javaBigDecimal = newValue.toBigDecimal();
    properties.setRoundingInterval(javaBigDecimal);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized void setRoundingIncrement(double newValue) {
    java.math.BigDecimal javaBigDecimal = java.math.BigDecimal.valueOf(newValue);
    properties.setRoundingInterval(javaBigDecimal);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getRoundingMode() {
    RoundingMode mode = properties.getRoundingMode();
    return (mode == null) ? 0 : mode.ordinal();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setRoundingMode(int roundingMode) {
    properties.setRoundingMode(RoundingMode.valueOf(roundingMode));
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized int getFormatWidth() {
    return properties.getPaddingWidth();
  }

  /** @stable ICU 2.0 */
  public synchronized void setFormatWidth(int width) {
    properties.setPaddingWidth(width);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized char getPadCharacter() {
    CharSequence paddingString = properties.getPaddingString();
    if (paddingString == null) {
      return '.'; // TODO: Is this the correct behavior?
    } else {
      return paddingString.charAt(0);
    }
  }

  /** @stable ICU 2.0 */
  public synchronized void setPadCharacter(char padChar) {
    properties.setPaddingString(Character.toString(padChar));
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized int getPadPosition() {
    PaddingLocation loc = properties.getPaddingLocation();
    return (loc == null) ? PAD_BEFORE_PREFIX : loc.toOld();
  }

  /** @stable ICU 2.0 */
  public synchronized void setPadPosition(int padPos) {
    properties.setPaddingLocation(PaddingLocation.fromOld(padPos));
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized boolean isScientificNotation() {
    return ScientificFormat.useScientificNotation(properties);
  }

  /** @stable ICU 2.0 */
  public synchronized void setScientificNotation(boolean useScientific) {
    properties.setExponentDigits(1);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized byte getMinimumExponentDigits() {
    return (byte) properties.getExponentDigits();
  }

  /** @stable ICU 2.0 */
  public synchronized void setMinimumExponentDigits(byte minExpDig) {
    properties.setExponentDigits(minExpDig);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized boolean isExponentSignAlwaysShown() {
    return properties.getExponentShowPlusSign();
  }

  /** @stable ICU 2.0 */
  public synchronized void setExponentSignAlwaysShown(boolean expSignAlways) {
    properties.setExponentShowPlusSign(expSignAlways);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized int getGroupingSize() {
    return properties.getGroupingSize();
  }

  /** @stable ICU 2.0 */
  public synchronized void setGroupingSize(int newValue) {
    properties.setGroupingSize(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  public synchronized int getSecondaryGroupingSize() {
    return properties.getSecondaryGroupingSize();
  }

  /** @stable ICU 2.0 */
  public synchronized void setSecondaryGroupingSize(int newValue) {
    properties.setSecondaryGroupingSize(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized boolean isGroupingUsed() {
    return PositiveDecimalFormat.useGrouping(properties);
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setGroupingUsed(boolean newValue) {
    if (newValue) {
      // TODO(sffc): how should this be handled?
      properties.setGroupingSize(3);
    } else {
      properties.setGroupingSize(Properties.DEFAULT_SECONDARY_GROUPING_SIZE);
    }
    refreshFormatter();
  }

  /** @stable ICU 4.2 */
  public synchronized MathContext getMathContextICU() {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 4.2 */
  public synchronized void setMathContextICU(MathContext newValue) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 4.2 */
  public synchronized java.math.MathContext getMathContext() {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 4.2 */
  public synchronized void setMathContext(java.math.MathContext newValue) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 54 */
  public synchronized void setDecimalPatternMatchRequired(boolean value) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 54 */
  public synchronized boolean isDecimalPatternMatchRequired() {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 2.0 */
  public synchronized boolean isDecimalSeparatorAlwaysShown() {
    return properties.getAlwaysShowDecimal();
  }

  /** @stable ICU 2.0 */
  public synchronized void setDecimalSeparatorAlwaysShown(boolean newValue) {
    properties.setAlwaysShowDecimal(newValue);
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMaximumIntegerDigits() {
    return properties.getMaximumIntegerDigits();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMinimumIntegerDigits() {
    return properties.getMinimumFractionDigits();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setMaximumIntegerDigits(int newValue) {
    properties.setMaximumIntegerDigits(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setMinimumIntegerDigits(int newValue) {
    properties.setMinimumFractionDigits(newValue);
    refreshFormatter();
  }

  /** @stable ICU 3.0 */
  public synchronized int getMinimumSignificantDigits() {
    return properties.getMinimumSignificantDigits();
  }

  /** @stable ICU 3.0 */
  public synchronized int getMaximumSignificantDigits() {
    return properties.getMaximumSignificantDigits();
  }

  /** @stable ICU 3.0 */
  public synchronized void setMinimumSignificantDigits(int min) {
    properties.setMinimumSignificantDigits(min);
    refreshFormatter();
  }

  /** @stable ICU 3.0 */
  public synchronized void setMaximumSignificantDigits(int max) {
    properties.setMaximumSignificantDigits(max);
    refreshFormatter();
  }

  /** @stable ICU 3.0 */
  public synchronized boolean areSignificantDigitsUsed() {
    return Rounder.useSignificantDigits(properties);
  }

  /** @stable ICU 3.0 */
  public synchronized void setSignificantDigitsUsed(boolean useSignificantDigits) {
    // TODO(sffc): is this the correct behavior?
    properties.setMaximumSignificantDigits(3);
    refreshFormatter();
  }

  /** @stable ICU 2.2 */
  @Override
  public synchronized void setCurrency(Currency theCurrency) {
    properties.setCurrency(theCurrency);
    refreshFormatter();
  }

  /** @stable ICU 54 */
  public synchronized void setCurrencyUsage(CurrencyUsage newUsage) {
    properties.setCurrencyUsage(newUsage);
    refreshFormatter();
  }

  /** @stable ICU 54 */
  public synchronized CurrencyUsage getCurrencyUsage() {
    return properties.getCurrencyUsage();
  }

  /** @stable ICU 4.2 */
  public CurrencyPluralInfo getCurrencyPluralInfo() {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 4.2 */
  public void setCurrencyPluralInfo(CurrencyPluralInfo newInfo) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setMaximumFractionDigits(int newValue) {
    properties.setMaximumFractionDigits(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized void setMinimumFractionDigits(int newValue) {
    properties.setMinimumFractionDigits(newValue);
    refreshFormatter();
  }

  /** @stable ICU 3.6 */
  public synchronized void setParseBigDecimal(boolean value) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 3.6 */
  public synchronized boolean isParseBigDecimal() {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /**
   * Set the maximum number of exponent digits when parsing a number. If the limit is set too high,
   * an OutOfMemoryException may be triggered. The default value is 1000.
   *
   * @param newValue the new limit
   * @stable ICU 51
   */
  public synchronized void setParseMaxDigits(int newValue) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get the current maximum number of exponent digits when parsing a number.
   *
   * @return the maximum number of exponent digits for parsing
   * @stable ICU 51
   */
  public synchronized int getParseMaxDigits() {
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 2.0 */
  @Override
  public Object clone() {
    return new DecimalFormat(this);
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized boolean equals(Object obj) {
    // TODO(sffc)
    // Implement Properties#equals() and call it
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 2.0 */
  @Override
  public int hashCode() {
    // TODO(sffc)
    // Implement Properties#hashCode() and call it
    throw new UnsupportedOperationException();
  }

  /** @stable ICU 2.0 */
  public synchronized String toPattern() {
    // Since we keep the properties object around, use it to generate the pattern.
    return PatternString.propertiesToString(properties);
  }

  /** @stable ICU 2.0 */
  public synchronized String toLocalizedPattern() {
    // TODO(sffc): What exactly is the difference between a "localized" pattern and a regular pattern?
    return PatternString.propertiesToString(properties);
  }

  /**
   * @internal
   * @deprecated This API is ICU internal only.
   */
  @Deprecated
  public IFixedDecimal getFixedDecimal(double number) {
    FormatQuantity2 fq = new FormatQuantity2(number);
    formatter.format(fq);
    return fq;
  }

  private void refreshFormatter() {
    try {
      formatter = Endpoint.fromBTA(properties, symbols);
    } catch (ParseException e) {
      // For backwards compatibility, convert from ParseException to IllegalArgumentException
      throw new IllegalArgumentException(e);
    }
  }

  private void setPropertiesFromPattern(String pattern) {
    try {
      PatternString.parseToExistingProperties(pattern, properties);
      formatter = Endpoint.fromBTA(properties, symbols);
    } catch (ParseException e) {
      // For backwards compatibility, convert from ParseException to IllegalArgumentException
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * {@icu} Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
   * characters inserted before the prefix.
   *
   * @see #setPadPosition
   * @see #getPadPosition
   * @see #PAD_AFTER_PREFIX
   * @see #PAD_BEFORE_SUFFIX
   * @see #PAD_AFTER_SUFFIX
   * @stable ICU 2.0
   */
  public static final int PAD_BEFORE_PREFIX = 0;

  /**
   * {@icu} Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
   * characters inserted after the prefix.
   *
   * @see #setPadPosition
   * @see #getPadPosition
   * @see #PAD_BEFORE_PREFIX
   * @see #PAD_BEFORE_SUFFIX
   * @see #PAD_AFTER_SUFFIX
   * @stable ICU 2.0
   */
  public static final int PAD_AFTER_PREFIX = 1;

  /**
   * {@icu} Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
   * characters inserted before the suffix.
   *
   * @see #setPadPosition
   * @see #getPadPosition
   * @see #PAD_BEFORE_PREFIX
   * @see #PAD_AFTER_PREFIX
   * @see #PAD_AFTER_SUFFIX
   * @stable ICU 2.0
   */
  public static final int PAD_BEFORE_SUFFIX = 2;

  /**
   * {@icu} Constant for {@link #getPadPosition()} and {@link #setPadPosition(int)} to specify pad
   * characters inserted after the suffix.
   *
   * @see #setPadPosition
   * @see #getPadPosition
   * @see #PAD_BEFORE_PREFIX
   * @see #PAD_AFTER_PREFIX
   * @see #PAD_BEFORE_SUFFIX
   * @stable ICU 2.0
   */
  public static final int PAD_AFTER_SUFFIX = 3;
}
