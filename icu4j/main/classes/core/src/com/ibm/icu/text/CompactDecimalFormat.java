// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, Google, International Business Machines Corporation and
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package com.ibm.icu.text;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.AttributedCharacterIterator;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

import com.ibm.icu.impl.number.Endpoint;
import com.ibm.icu.impl.number.Format.SingularFormat;
import com.ibm.icu.impl.number.FormatQuantitySelector;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.util.CurrencyAmount;
import com.ibm.icu.util.ULocale;

/**
 * The CompactDecimalFormat produces abbreviated numbers, suitable for display in environments will
 * limited real estate. For example, 'Hits: 1.2B' instead of 'Hits: 1,200,000,000'. The format will
 * be appropriate for the given language, such as "1,2 Mrd." for German.
 *
 * <p>For numbers under 1000 trillion (under 10^15, such as 123,456,789,012,345), the result will be
 * short for supported languages. However, the result may sometimes exceed 7 characters, such as
 * when there are combining marks or thin characters. In such cases, the visual width in fonts
 * should still be short.
 *
 * <p>By default, there are 2 significant digits. After creation, if more than three significant
 * digits are set (with setMaximumSignificantDigits), or if a fixed number of digits are set (with
 * setMaximumIntegerDigits or setMaximumFractionDigits), then result may be wider.
 *
 * <p>The "short" style is also capable of formatting currency amounts, such as "$1.2M" instead of
 * "$1,200,000.00" (English) or "5,3 Mio. €" instead of "5.300.000,00 €" (German). Localized data
 * concerning longer formats is not available yet in the Unicode CLDR. Because of this, attempting
 * to format a currency amount using the "long" style will produce an UnsupportedOperationException.
 *
 * <p>At this time, negative numbers and parsing are not supported, and will produce an
 * UnsupportedOperationException. Resetting the pattern prefixes or suffixes is not supported; the
 * method calls are ignored.
 *
 * <p>Note that important methods, like setting the number of decimals, will be moved up from
 * DecimalFormat to NumberFormat.
 *
 * @author markdavis
 * @stable ICU 49
 */
public class CompactDecimalFormat extends DecimalFormat {

  /**
   * Style parameter for CompactDecimalFormat.
   *
   * @stable ICU 50
   */
  public enum CompactStyle {
    /**
     * Short version, like "1.2T"
     *
     * @stable ICU 50
     */
    SHORT,
    /**
     * Longer version, like "1.2 trillion", if available. May return same result as SHORT if not.
     *
     * @stable ICU 50
     */
    LONG
  }

  private final SingularFormat formatter;

  /**
   * Create a CompactDecimalFormat appropriate for a locale. The result may be affected by the
   * number system in the locale, such as ar-u-nu-latn.
   *
   * @param locale the desired locale
   * @param style the compact style
   * @stable ICU 50
   */
  public static CompactDecimalFormat getInstance(ULocale locale, CompactStyle style) {
    return new CompactDecimalFormat(locale, style);
  }

  /**
   * Create a CompactDecimalFormat appropriate for a locale. The result may be affected by the
   * number system in the locale, such as ar-u-nu-latn.
   *
   * @param locale the desired locale
   * @param style the compact style
   * @stable ICU 50
   */
  public static CompactDecimalFormat getInstance(Locale locale, CompactStyle style) {
    return new CompactDecimalFormat(ULocale.forLocale(locale), style);
  }

  /**
   * The public mechanism is CompactDecimalFormat.getInstance().
   *
   * @param locale the desired locale
   * @param style the compact style
   */
  CompactDecimalFormat(ULocale locale, CompactStyle style) {
    Properties properties = new Properties();
    properties.setCompactStyle(style);
    try {
      formatter = Endpoint.fromBTA(properties, locale);
    } catch (ParseException e) {
      // Should never happen unless data is bad
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public boolean equals(Object obj) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc): Implement FieldPosition
    formatter.format(FormatQuantitySelector.from(number), toAppendTo);
    return toAppendTo;
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 50
   */
  @Override
  public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc): Implement FieldPosition
    formatter.format(FormatQuantitySelector.from(number), toAppendTo);
    return toAppendTo;
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public StringBuffer format(BigInteger number, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc): Implement FieldPosition
    formatter.format(FormatQuantitySelector.from(number), toAppendTo);
    return toAppendTo;
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public StringBuffer format(BigDecimal number, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc): Implement FieldPosition
    formatter.format(FormatQuantitySelector.from(number), toAppendTo);
    return toAppendTo;
  }

  /**
   * {@inheritDoc}
   *
   * @stable ICU 49
   */
  @Override
  public StringBuffer format(
      com.ibm.icu.math.BigDecimal number, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc): Implement FieldPosition
    formatter.format(FormatQuantitySelector.from(number), toAppendTo);
    return toAppendTo;
  }

  /**
   * {@inheritDoc}
   *
   * @internal ICU 57 technology preview
   * @deprecated This API might change or be removed in a future release.
   */
  @Override
  @Deprecated
  public StringBuffer format(CurrencyAmount currAmt, StringBuffer toAppendTo, FieldPosition pos) {
    // TODO(sffc)
    throw new UnsupportedOperationException();
  }

  /**
   * Parsing is currently unsupported, and throws an UnsupportedOperationException.
   *
   * @stable ICU 49
   */
  @Override
  public Number parse(String text, ParsePosition parsePosition) {
    throw new UnsupportedOperationException();
  }

  // DISALLOW Serialization, at least while draft

  private void writeObject(ObjectOutputStream out) throws IOException {
    throw new NotSerializableException();
  }

  private void readObject(ObjectInputStream in) throws IOException {
    throw new NotSerializableException();
  }
}
