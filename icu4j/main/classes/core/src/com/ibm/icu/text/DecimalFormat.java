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
import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.FormatQuantity1;
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
  public synchronized void applyLocalizedPattern(String localizedPattern) {
    String pattern = PatternString.convertLocalized(localizedPattern, symbols, false);
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
    // FIXME: Implement alternative FormatQuantity implementations
    formatter.format(new FormatQuantity2(number), result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(long number, StringBuffer result, FieldPosition fieldPosition) {
    FormatQuantity fq;
    if (number == Long.MIN_VALUE) {
      fq = new FormatQuantity1(java.math.BigDecimal.valueOf(number));
    } else if (Math.abs(number) >= 1e16) {
      fq = new FormatQuantity1(number);
    } else {
      fq = new FormatQuantity2(number);
    }
    formatter.format(fq, result, fieldPosition);
    return result;
  }

  private static final BigInteger BIGINT_1E16 = BigInteger.valueOf((long) 1e16);

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(BigInteger number, StringBuffer result, FieldPosition fieldPosition) {
    FormatQuantity fq;
    if (number.abs().compareTo(BIGINT_1E16) >= 0) {
      fq = new FormatQuantity1(new java.math.BigDecimal(number));
    } else {
      fq = new FormatQuantity2(number);
    }
    formatter.format(fq, result, fieldPosition);
    return result;
  }

  private static final java.math.BigDecimal BIGDEC_1E16 = java.math.BigDecimal.valueOf(1e16);

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(
      java.math.BigDecimal number, StringBuffer result, FieldPosition fieldPosition) {
    FormatQuantity fq;
    if (number.abs().compareTo(BIGDEC_1E16) >= 0) {
      fq = new FormatQuantity1(number);
    } else {
      fq = new FormatQuantity2(number);
    }
    formatter.format(fq, result, fieldPosition);
    return result;
  }

  private static final BigDecimal ICUBIGDEC_1E16 = BigDecimal.valueOf(1e16);

  /** @stable ICU 2.0 */
  @Override
  public StringBuffer format(BigDecimal number, StringBuffer result, FieldPosition fieldPosition) {
    FormatQuantity fq;
    if (number.abs().compareTo(ICUBIGDEC_1E16) >= 0) {
      fq = new FormatQuantity1(number.toBigDecimal());
    } else {
      fq = new FormatQuantity2(number);
    }
    formatter.format(fq, result, fieldPosition);
    return result;
  }

  /** @stable ICU 2.0 */
  @Override
  public Number parse(String text, ParsePosition parsePosition) {
    try {
      // Backwards compatibility: use currency parse mode if this is a currency instance
      Number result;
      if (com.ibm.icu.impl.number.formatters.CurrencyFormat.useCurrency(properties)) {
        CurrencyAmount temp = Parse.parseCurrency(text, parsePosition, properties, symbols);
        if (temp == null) return null;
        result = temp.getNumber();
      } else {
        result = Parse.parse(text, parsePosition, properties, symbols);
      }
      // Backwards compatibility: return com.ibm.icu.math.BigDecimal
      if (result instanceof java.math.BigDecimal) {
        result = new com.ibm.icu.math.BigDecimal((java.math.BigDecimal) result);
      }
      return result;
    } catch (ParseException e) {
      return null;
    }
  }

  /** @stable ICU 49 */
  @Override
  public CurrencyAmount parseCurrency(CharSequence text, ParsePosition parsePosition) {
    try {
      CurrencyAmount result = Parse.parseCurrency(text, parsePosition, properties, symbols);
      if (result == null) return null;
      Number number = result.getNumber();
      // Backwards compatibility: return com.ibm.icu.math.BigDecimal
      if (number instanceof java.math.BigDecimal) {
        number = new com.ibm.icu.math.BigDecimal((java.math.BigDecimal) number);
        result = new CurrencyAmount(number, result.getCurrency());
      }
      return result;
    } catch (ParseException e) {
      return null;
    }
  }

  /** @stable ICU 3.6 */
  @Override
  public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
    if (!(obj instanceof Number)) throw new IllegalArgumentException();
    Number number = (Number) obj;
    return formatter.formatToCharacterIterator(new FormatQuantity2(number));
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
    if (newValue == 0) {
      throw new IllegalArgumentException("Multiplier must be nonzero.");
    }

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
    // Backwards compatibility: ignore rounding increment if zero,
    // and instead set maximum fraction digits.
    if (newValue.compareTo(java.math.BigDecimal.ZERO) == 0) {
      properties.setMaximumFractionDigits(Integer.MAX_VALUE);
      return;
    }

    properties.setRoundingInterval(newValue);
    refreshFormatter();
  }

  /** @stable ICU 3.6 */
  public synchronized void setRoundingIncrement(BigDecimal newValue) {
    java.math.BigDecimal javaBigDecimal = newValue.toBigDecimal();
    setRoundingIncrement(javaBigDecimal);
  }

  /** @stable ICU 2.0 */
  public synchronized void setRoundingIncrement(double newValue) {
    java.math.BigDecimal javaBigDecimal = java.math.BigDecimal.valueOf(newValue);
    setRoundingIncrement(javaBigDecimal);
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
    if (useScientific) {
      properties.setExponentDigits(1);
    } else {
      properties.setExponentDigits(0);
    }
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
    properties.setDecimalPatternMatchRequired(value);
    refreshFormatter();
  }

  /** @stable ICU 54 */
  public synchronized boolean isDecimalPatternMatchRequired() {
    return properties.getDecimalPatternMatchRequired();
  }

  /** @stable ICU 2.0 */
  public synchronized boolean isDecimalSeparatorAlwaysShown() {
    return properties.getAlwaysShowDecimal();
  }

  /** @stable ICU 2.0 */
  public synchronized void setDecimalSeparatorAlwaysShown(boolean newValue) {
    properties.setAlwaysShowDecimal(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMaximumIntegerDigits() {
    return properties.getMaximumIntegerDigits();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMinimumIntegerDigits() {
    return properties.getMinimumIntegerDigits();
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
    properties.setMinimumIntegerDigits(newValue);
    refreshFormatter();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMaximumFractionDigits() {
    return properties.getMaximumFractionDigits();
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int getMinimumFractionDigits() {
    return properties.getMinimumFractionDigits();
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
    if (useSignificantDigits) {
      // These are the default values from the old implementation.
      properties.setMinimumSignificantDigits(1);
      properties.setMaximumSignificantDigits(6);
    } else {
      properties.setMinimumSignificantDigits(Properties.DEFAULT_MINIMUM_SIGNIFICANT_DIGITS);
      properties.setMaximumSignificantDigits(Properties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS);
    }
    refreshFormatter();
  }

  /** @stable ICU 2.2 */
  @Override
  public synchronized void setCurrency(Currency theCurrency) {
    properties.setCurrency(theCurrency);
    // Backwards compatibility: also set the currency in the DecimalFormatSymbols
    if (theCurrency != null) {
      symbols.setCurrency(theCurrency);
      String symbol = theCurrency.getName(symbols.getULocale(), Currency.SYMBOL_NAME, null);
      symbols.setCurrencySymbol(symbol);
    }
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
    return properties.getCurrencyPluralInfo();
  }

  /** @stable ICU 4.2 */
  public void setCurrencyPluralInfo(CurrencyPluralInfo newInfo) {
    properties.setCurrencyPluralInfo(newInfo);
    refreshFormatter();
  }

  /** @stable ICU 3.6 */
  public synchronized void setParseBigDecimal(boolean value) {
    properties.setParseToBigDecimal(value);
    // refreshFormatter() not needed
  }

  /** @stable ICU 3.6 */
  public synchronized boolean isParseBigDecimal() {
    return properties.getParseToBigDecimal();
  }

  /**
   * Setting max parse digits has no effect since ICU 59.
   *
   * @stable ICU 51
   */
  public synchronized void setParseMaxDigits(int _) {
  }

  /**
   * Setting max parse digits has no effect since ICU 59.
   * Always returns 1000.
   *
   * @stable ICU 51
   */
  public synchronized int getParseMaxDigits() {
    return 1000;
  }

  @Override
  public synchronized void setParseStrict(boolean parseStrict) {
    Parse.ParseMode mode = parseStrict ? Parse.ParseMode.STRICT : Parse.ParseMode.LENIENT;
    properties.setParseMode(mode);
    // refreshFormatter() not needed
  }

  @Override
  public synchronized boolean isParseStrict() {
    return properties.getParseMode() == Parse.ParseMode.STRICT;
  }

  /** @stable ICU 2.0 */
  @Override
  public Object clone() {
    return new DecimalFormat(this);
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj == this) return true;
    if (!(obj instanceof DecimalFormat)) return false;
    DecimalFormat other = (DecimalFormat) obj;
    return properties.equals(other.properties) && symbols.equals(other.symbols);
  }

  /** @stable ICU 2.0 */
  @Override
  public synchronized int hashCode() {
    return properties.hashCode();
  }

  /** @stable ICU 2.0 */
  public synchronized String toPattern() {
    // Since we keep the properties object around, use it to generate the pattern.
    return PatternString.propertiesToString(properties);
  }

  /** @stable ICU 2.0 */
  public synchronized String toLocalizedPattern() {
    String pattern = PatternString.propertiesToString(properties);
    return PatternString.convertLocalized(pattern, symbols, true);
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
