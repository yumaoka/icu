// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ibm.icu.impl.number.Parse.ParseMode;
import com.ibm.icu.impl.number.formatters.BigDecimalMultiplier;
import com.ibm.icu.impl.number.formatters.CompactDecimalFormat;
import com.ibm.icu.impl.number.formatters.CurrencyFormat;
import com.ibm.icu.impl.number.formatters.CurrencyFormat.CurrencyStyle;
import com.ibm.icu.impl.number.formatters.MagnitudeMultiplier;
import com.ibm.icu.impl.number.formatters.MeasureFormat;
import com.ibm.icu.impl.number.formatters.PaddingFormat;
import com.ibm.icu.impl.number.formatters.PaddingFormat.PaddingLocation;
import com.ibm.icu.impl.number.formatters.PositiveDecimalFormat;
import com.ibm.icu.impl.number.formatters.PositiveNegativeAffixFormat;
import com.ibm.icu.impl.number.formatters.PositiveNegativeAffixFormat.IProperties;
import com.ibm.icu.impl.number.formatters.ScientificFormat;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;
import com.ibm.icu.text.CurrencyPluralInfo;
import com.ibm.icu.text.MeasureFormat.FormatWidth;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.Currency.CurrencyUsage;
import com.ibm.icu.util.MeasureUnit;

public class Properties
    implements Cloneable,
        PositiveDecimalFormat.IProperties,
        Rounder.IProperties,
        PositiveNegativeAffixFormat.IProperties,
        MagnitudeMultiplier.IProperties,
        ScientificFormat.IProperties,
        MeasureFormat.IProperties,
        CompactDecimalFormat.IProperties,
        PaddingFormat.IProperties,
        BigDecimalMultiplier.IProperties,
        CurrencyFormat.IProperties,
        Parse.IProperties {

  private static final Properties DEFAULT = new Properties();

  private boolean alwaysShowDecimal;
  private boolean alwaysShowPlusSign;
  private CompactStyle compactStyle;
  private Currency currency;
  private CurrencyPluralInfo currencyPluralInfo;
  private CurrencyStyle currencyStyle;
  private CurrencyUsage currencyUsage;
  private boolean decimalPatternMatchRequired;
  private int exponentDigits;
  private boolean exponentShowPlusSign;
  private int groupingSize;
  private int magnitudeMultiplier;
  private int maximumFractionDigits;
  private int maximumIntegerDigits;
  private int maximumSignificantDigits;
  private FormatWidth measureFormatWidth;
  private MeasureUnit measureUnit;
  private int minimumFractionDigits;
  private int minimumGroupingDigits;
  private int minimumIntegerDigits;
  private int minimumSignificantDigits;
  private BigDecimal multiplier;
  private CharSequence negativePrefix;
  private CharSequence negativePrefixPattern;
  private CharSequence negativeSuffix;
  private CharSequence negativeSuffixPattern;
  private PaddingLocation paddingLocation;
  private CharSequence paddingString;
  private int paddingWidth;
  private boolean parseCurrency;
  private boolean parseIgnoreExponent;
  private boolean parseIntegerOnly;
  private ParseMode parseMode;
  private CharSequence positivePrefix;
  private CharSequence positivePrefixPattern;
  private CharSequence positiveSuffix;
  private CharSequence positiveSuffixPattern;
  private BigDecimal roundingInterval;
  private RoundingMode roundingMode;
  private int secondaryGroupingSize;

  public Properties() {
    clear();
  }

  public Properties clear() {
    alwaysShowDecimal = DEFAULT_ALWAYS_SHOW_DECIMAL;
    alwaysShowPlusSign = DEFAULT_ALWAYS_SHOW_PLUS_SIGN;
    compactStyle = DEFAULT_COMPACT_STYLE;
    currency = DEFAULT_CURRENCY;
    currencyPluralInfo = DEFAULT_CURRENCY_PLURAL_INFO;
    currencyStyle = DEFAULT_CURRENCY_STYLE;
    currencyUsage = DEFAULT_CURRENCY_USAGE;
    decimalPatternMatchRequired = DEFAULT_DECIMAL_PATTERN_MATCH_REQUIRED;
    exponentDigits = DEFAULT_EXPONENT_DIGITS;
    exponentShowPlusSign = DEFAULT_EXPONENT_SHOW_PLUS_SIGN;
    groupingSize = DEFAULT_GROUPING_SIZE;
    magnitudeMultiplier = DEFAULT_MAGNITUDE_MULTIPLIER;
    maximumFractionDigits = DEFAULT_MAXIMUM_FRACTION_DIGITS;
    maximumIntegerDigits = DEFAULT_MAXIMUM_INTEGER_DIGITS;
    maximumSignificantDigits = DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS;
    measureFormatWidth = DEFAULT_MEASURE_FORMAT_WIDTH;
    measureUnit = DEFAULT_MEASURE_UNIT;
    minimumFractionDigits = DEFAULT_MINIMUM_FRACTION_DIGITS;
    minimumGroupingDigits = DEFAULT_MINIMUM_GROUPING_DIGITS;
    minimumIntegerDigits = DEFAULT_MINIMUM_INTEGER_DIGITS;
    minimumSignificantDigits = DEFAULT_MINIMUM_SIGNIFICANT_DIGITS;
    multiplier = DEFAULT_MULTIPLIER;
    negativePrefix = DEFAULT_NEGATIVE_PREFIX;
    negativePrefixPattern = DEFAULT_NEGATIVE_PREFIX_PATTERN;
    negativeSuffix = DEFAULT_NEGATIVE_SUFFIX;
    negativeSuffixPattern = DEFAULT_NEGATIVE_SUFFIX_PATTERN;
    paddingLocation = DEFAULT_PADDING_LOCATION;
    paddingString = DEFAULT_PADDING_STRING;
    paddingWidth = DEFAULT_PADDING_WIDTH;
    parseCurrency = DEFAULT_PARSE_CURRENCY;
    parseIgnoreExponent = DEFAULT_PARSE_IGNORE_EXPONENT;
    parseIntegerOnly = DEFAULT_PARSE_INTEGER_ONLY;
    parseMode = DEFAULT_PARSE_MODE;
    positivePrefix = DEFAULT_POSITIVE_PREFIX;
    positivePrefixPattern = DEFAULT_POSITIVE_PREFIX_PATTERN;
    positiveSuffix = DEFAULT_POSITIVE_SUFFIX;
    positiveSuffixPattern = DEFAULT_POSITIVE_SUFFIX_PATTERN;
    roundingInterval = DEFAULT_ROUNDING_INTERVAL;
    roundingMode = DEFAULT_ROUNDING_MODE;
    secondaryGroupingSize = DEFAULT_SECONDARY_GROUPING_SIZE;
    return this;
  }

  /** Creates and returns a shallow copy of the property bag. */
  @Override
  public Properties clone() {
    // super.clone() returns a shallow copy.
    try {
      return (Properties) super.clone();
    } catch (CloneNotSupportedException e) {
      // Should never happen since super is Object
      throw new UnsupportedOperationException(e);
    }
  }

  @Override
  public boolean getAlwaysShowDecimal() {
    return alwaysShowDecimal;
  }

  @Override
  public boolean getAlwaysShowPlusSign() {
    return alwaysShowPlusSign;
  }

  @Override
  public CompactStyle getCompactStyle() {
    return compactStyle;
  }

  @Override
  public Currency getCurrency() {
    return currency;
  }

  @Override
  @Deprecated
  public CurrencyPluralInfo getCurrencyPluralInfo() {
    return currencyPluralInfo;
  }

  @Override
  public CurrencyStyle getCurrencyStyle() {
    return currencyStyle;
  }

  @Override
  public CurrencyUsage getCurrencyUsage() {
    return currencyUsage;
  }

  @Override
  public boolean getDecimalPatternMatchRequired() {
    return decimalPatternMatchRequired;
  }

  @Override
  public int getExponentDigits() {
    return exponentDigits;
  }

  @Override
  public boolean getExponentShowPlusSign() {
    return exponentShowPlusSign;
  }

  @Override
  public int getGroupingSize() {
    return groupingSize;
  }

  @Override
  public int getMagnitudeMultiplier() {
    return magnitudeMultiplier;
  }

  @Override
  public int getMaximumFractionDigits() {
    return maximumFractionDigits;
  }

  @Override
  public int getMaximumIntegerDigits() {
    return maximumIntegerDigits;
  }

  @Override
  public int getMaximumSignificantDigits() {
    return maximumSignificantDigits;
  }

  @Override
  public FormatWidth getMeasureFormatWidth() {
    return measureFormatWidth;
  }

  @Override
  public MeasureUnit getMeasureUnit() {
    return measureUnit;
  }

  @Override
  public int getMinimumFractionDigits() {
    return minimumFractionDigits;
  }

  @Override
  public int getMinimumGroupingDigits() {
    return minimumGroupingDigits;
  }

  @Override
  public int getMinimumIntegerDigits() {
    return minimumIntegerDigits;
  }

  @Override
  public int getMinimumSignificantDigits() {
    return minimumSignificantDigits;
  }

  @Override
  public BigDecimal getMultiplier() {
    return multiplier;
  }

  @Override
  public CharSequence getNegativePrefix() {
    return negativePrefix;
  }

  @Override
  public CharSequence getNegativePrefixPattern() {
    return negativePrefixPattern;
  }

  @Override
  public CharSequence getNegativeSuffix() {
    return negativeSuffix;
  }

  @Override
  public CharSequence getNegativeSuffixPattern() {
    return negativeSuffixPattern;
  }

  @Override
  public PaddingLocation getPaddingLocation() {
    return paddingLocation;
  }

  @Override
  public CharSequence getPaddingString() {
    return paddingString;
  }

  @Override
  public int getPaddingWidth() {
    return paddingWidth;
  }

  @Override
  public boolean getParseCurrency() {
    return parseCurrency;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#getParseIgnoreExponent()
   */
  @Override
  public boolean getParseIgnoreExponent() {
    return parseIgnoreExponent;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#getParseIntegerOnly()
   */
  @Override
  public boolean getParseIntegerOnly() {
    return parseIntegerOnly;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#getParseMode()
   */
  @Override
  public ParseMode getParseMode() {
    return parseMode;
  }

  @Override
  public CharSequence getPositivePrefix() {
    return positivePrefix;
  }

  @Override
  public CharSequence getPositivePrefixPattern() {
    return positivePrefixPattern;
  }

  @Override
  public CharSequence getPositiveSuffix() {
    return positiveSuffix;
  }

  @Override
  public CharSequence getPositiveSuffixPattern() {
    return positiveSuffixPattern;
  }

  @Override
  public BigDecimal getRoundingInterval() {
    return roundingInterval;
  }

  @Override
  public RoundingMode getRoundingMode() {
    return roundingMode;
  }

  @Override
  public int getSecondaryGroupingSize() {
    return secondaryGroupingSize;
  }

  @Override
  public Properties setAlwaysShowDecimal(boolean alwaysShowDecimal) {
    this.alwaysShowDecimal = alwaysShowDecimal;
    return this;
  }

  @Override
  public IProperties setAlwaysShowPlusSign(boolean alwaysShowPlusSign) {
    this.alwaysShowPlusSign = alwaysShowPlusSign;
    return this;
  }

  @Override
  public Properties setCompactStyle(CompactStyle compactStyle) {
    this.compactStyle = compactStyle;
    return this;
  }

  @Override
  public Properties setCurrency(Currency currency) {
    this.currency = currency;
    return this;
  }

  @Override
  @Deprecated
  public Properties setCurrencyPluralInfo(CurrencyPluralInfo currencyPluralInfo) {
    this.currencyPluralInfo = currencyPluralInfo;
    return this;
  }

  @Override
  public Properties setCurrencyStyle(CurrencyStyle currencyStyle) {
    this.currencyStyle = currencyStyle;
    return this;
  }

  @Override
  public Properties setCurrencyUsage(CurrencyUsage currencyUsage) {
    this.currencyUsage = currencyUsage;
    return this;
  }

  @Override
  public Properties setDecimalPatternMatchRequired(boolean decimalPatternMatchRequired) {
    this.decimalPatternMatchRequired = decimalPatternMatchRequired;
    return this;
  }

  @Override
  public Properties setExponentDigits(int exponentDigits) {
    this.exponentDigits = exponentDigits;
    return this;
  }

  @Override
  public Properties setExponentShowPlusSign(boolean exponentShowPlusSign) {
    this.exponentShowPlusSign = exponentShowPlusSign;
    return this;
  }

  @Override
  public Properties setGroupingSize(int groupingSize) {
    this.groupingSize = groupingSize;
    return this;
  }

  @Override
  public Properties setMagnitudeMultiplier(int magnitudeMultiplier) {
    this.magnitudeMultiplier = magnitudeMultiplier;
    return this;
  }

  @Override
  public Properties setMaximumFractionDigits(int maximumFractionDigits) {
    this.maximumFractionDigits = maximumFractionDigits;
    return this;
  }

  @Override
  public Properties setMaximumIntegerDigits(int maximumIntegerDigits) {
    this.maximumIntegerDigits = maximumIntegerDigits;
    return this;
  }

  @Override
  public Properties setMaximumSignificantDigits(int maximumSignificantDigits) {
    this.maximumSignificantDigits = maximumSignificantDigits;
    return this;
  }

  @Override
  public Properties setMeasureFormatWidth(FormatWidth measureFormatWidth) {
    this.measureFormatWidth = measureFormatWidth;
    return this;
  }

  @Override
  public Properties setMeasureUnit(MeasureUnit measureUnit) {
    this.measureUnit = measureUnit;
    return this;
  }

  @Override
  public Properties setMinimumFractionDigits(int minimumFractionDigits) {
    this.minimumFractionDigits = minimumFractionDigits;
    return this;
  }

  @Override
  public Properties setMinimumGroupingDigits(int minimumGroupingDigits) {
    this.minimumGroupingDigits = minimumGroupingDigits;
    return this;
  }

  @Override
  public Properties setMinimumIntegerDigits(int minimumIntegerDigits) {
    this.minimumIntegerDigits = minimumIntegerDigits;
    return this;
  }

  @Override
  public Properties setMinimumSignificantDigits(int minimumSignificantDigits) {
    this.minimumSignificantDigits = minimumSignificantDigits;
    return this;
  }

  @Override
  public Properties setMultiplier(BigDecimal multiplier) {
    this.multiplier = multiplier;
    return this;
  }

  @Override
  public Properties setNegativePrefix(CharSequence negativePrefix) {
    this.negativePrefix = negativePrefix;
    return this;
  }

  @Override
  public Properties setNegativePrefixPattern(CharSequence negativePrefixPattern) {
    this.negativePrefixPattern = negativePrefixPattern;
    return this;
  }

  @Override
  public Properties setNegativeSuffix(CharSequence negativeSuffix) {
    this.negativeSuffix = negativeSuffix;
    return this;
  }

  @Override
  public Properties setNegativeSuffixPattern(CharSequence negativeSuffixPattern) {
    this.negativeSuffixPattern = negativeSuffixPattern;
    return this;
  }

  @Override
  public Properties setPaddingLocation(PaddingLocation paddingLocation) {
    this.paddingLocation = paddingLocation;
    return this;
  }

  @Override
  public Properties setPaddingString(CharSequence paddingString) {
    this.paddingString = paddingString;
    return this;
  }

  @Override
  public Properties setPaddingWidth(int paddingWidth) {
    this.paddingWidth = paddingWidth;
    return this;
  }

  @Override
  public com.ibm.icu.impl.number.Parse.IProperties setParseCurrency(boolean parseCurrency) {
    this.parseCurrency = parseCurrency;
    return this;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#setParseIgnoreExponent(boolean)
   */
  @Override
  public Properties setParseIgnoreExponent(boolean parseIgnoreExponent) {
    this.parseIgnoreExponent = parseIgnoreExponent;
    return this;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#setParseIntegerOnly(boolean)
   */
  @Override
  public Properties setParseIntegerOnly(boolean parseIntegerOnly) {
    this.parseIntegerOnly = parseIntegerOnly;
    return this;
  }

  /* (non-Javadoc)
   * @see com.ibm.icu.impl.number.Parse.IProperties#setParseMode(com.ibm.icu.impl.number.Parse.ParseMode)
   */
  @Override
  public Properties setParseMode(ParseMode parseMode) {
    this.parseMode = parseMode;
    return this;
  }

  @Override
  public Properties setPositivePrefix(CharSequence positivePrefix) {
    this.positivePrefix = positivePrefix;
    return this;
  }

  @Override
  public Properties setPositivePrefixPattern(CharSequence positivePrefixPattern) {
    this.positivePrefixPattern = positivePrefixPattern;
    return this;
  }

  @Override
  public Properties setPositiveSuffix(CharSequence positiveSuffix) {
    this.positiveSuffix = positiveSuffix;
    return this;
  }

  @Override
  public Properties setPositiveSuffixPattern(CharSequence positiveSuffixPattern) {
    this.positiveSuffixPattern = positiveSuffixPattern;
    return this;
  }

  @Override
  public Properties setRoundingInterval(BigDecimal interval) {
    this.roundingInterval = interval;
    return this;
  }

  @Override
  public Properties setRoundingMode(RoundingMode roundingMode) {
    this.roundingMode = roundingMode;
    return this;
  }

  @Override
  public Properties setSecondaryGroupingSize(int secondaryGroupingSize) {
    this.secondaryGroupingSize = secondaryGroupingSize;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("<Properties");
    Field[] fields = Properties.class.getDeclaredFields();
    for (Field field : fields) {
      Object myValue, defaultValue;
      try {
        myValue = field.get(this);
        defaultValue = field.get(DEFAULT);
      } catch (IllegalArgumentException e) {
        e.printStackTrace();
        continue;
      } catch (IllegalAccessException e) {
        e.printStackTrace();
        continue;
      }
      if (myValue == null && defaultValue == null) {
        continue;
      } else if (myValue == null || defaultValue == null) {
        result.append(" " + field.getName() + ":" + myValue);
      } else if (!myValue.equals(defaultValue)) {
        result.append(" " + field.getName() + ":" + myValue);
      }
    }
    result.append(">");
    return result.toString();
  }
}
