// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.formatters;

import java.math.BigDecimal;
import java.text.ParseException;

import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.impl.number.Format;
import com.ibm.icu.impl.number.LiteralString;
import com.ibm.icu.impl.number.PNAffixGenerator;
import com.ibm.icu.impl.number.Rounder;
import com.ibm.icu.impl.number.modifiers.GeneralPluralModifier;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.Currency.CurrencyUsage;
import com.ibm.icu.util.ULocale;

public class CurrencyFormat {

  public enum CurrencyStyle {
    SYMBOL,
    ISO_CODE;
  }

  public static interface ICurrencyProperties {
    static Currency DEFAULT_CURRENCY = null;

    /** @see #setCurrency */
    public Currency getCurrency();

    /**
     * Use the specified currency to substitute currency placeholders ('¤') in the pattern string.
     *
     * @param currency The currency.
     * @return The property bag, for chaining.
     */
    public IProperties setCurrency(Currency currency);

    static CurrencyStyle DEFAULT_CURRENCY_STYLE = CurrencyStyle.SYMBOL;

    /** @see #setCurrencyStyle */
    public CurrencyStyle getCurrencyStyle();

    /**
     * Use the specified {@link CurrencyStyle} to replace currency placeholders ('¤').
     * CurrencyStyle.SYMBOL will use the short currency symbol, like "$" or "€", whereas
     * CurrencyStyle.ISO_CODE will use the ISO 4217 currency code, like "USD" or "EUR".
     *
     * <p>For long currency names, use {@link MeasureFormat.IProperties#setMeasureUnit}.
     *
     * @param currencyStyle The currency style. Defaults to CurrencyStyle.SYMBOL.
     * @return The property bag, for chaining.
     */
    public IProperties setCurrencyStyle(CurrencyStyle currencyStyle);

    /**
     * An old enum that specifies how currencies should be rounded. It contains a subset of the
     * functionality supported by RoundingInterval.
     */
    static Currency.CurrencyUsage DEFAULT_CURRENCY_USAGE = Currency.CurrencyUsage.STANDARD;

    /** @see #setCurrencyUsage */
    public Currency.CurrencyUsage getCurrencyUsage();

    /**
     * Use the specified {@link CurrencyUsage} instance, which provides default rounding rules for
     * the currency in two styles, CurrencyUsage.CASH and CurrencyUsage.STANDARD.
     *
     * <p>The CurrencyUsage specified here will not be used unless there is a currency placeholder
     * in the pattern.
     *
     * @param currencyUsage The currency usage. Defaults to CurrencyUsage.STANDARD.
     * @return The property bag, for chaining.
     */
    public IProperties setCurrencyUsage(Currency.CurrencyUsage currencyUsage);

    public IProperties clone();
  }

  public static interface IProperties
      extends ICurrencyProperties, Rounder.IProperties, PositiveNegativeAffixFormat.IProperties {}

  /**
   * Returns true if the currency is set in The property bag or if currency symbols are present
   * in the prefix/suffix pattern.
   */
  public static boolean useCurrency(IProperties properties) {
    return ((properties.getCurrency() != null)
        || LiteralString.hasCurrencySymbols(properties.getPositivePrefixPattern())
        || LiteralString.hasCurrencySymbols(properties.getPositiveSuffixPattern())
        || LiteralString.hasCurrencySymbols(properties.getNegativePrefixPattern())
        || LiteralString.hasCurrencySymbols(properties.getNegativeSuffixPattern()));
  }

  public static String getPreferredCurrencySymbol(
      DecimalFormatSymbols symbols, ICurrencyProperties properties) {
    Currency currency = getCurrencyOrNull(symbols, properties);
    if (currency == null) {
      // Use a sensible fallback value
      return symbols.getCurrencySymbol();
    }

    CurrencyStyle style = properties.getCurrencyStyle();
    if (style == CurrencyStyle.ISO_CODE) {
      return currency.getCurrencyCode();
    } else {
      return currency.getName(symbols.getULocale(), Currency.SYMBOL_NAME, null);
    }
  }

  static Currency getCurrencyOrNull(DecimalFormatSymbols symbols, ICurrencyProperties properties) {
    Currency currency = properties.getCurrency();
    if (currency == null) {
      currency = symbols.getCurrency();
    }
    return currency;
  }

  public static Format.BeforeFormat getCurrencyModifier(
      DecimalFormatSymbols symbols, IProperties properties) throws ParseException {
    Currency currency = getCurrencyOrNull(symbols, properties);

    if (currency == null) {
      // There is a currency symbol in the pattern, but we have no currency available to use.
      return PositiveNegativeAffixFormat.getInstance(symbols, properties);
    }

    PNAffixGenerator pnag = PNAffixGenerator.getThreadLocalInstance();
    ULocale uloc = symbols.getULocale();
    String symbol = currency.getName(uloc, Currency.SYMBOL_NAME, null);
    String isoCode = currency.getCurrencyCode();

    // CurrencyStyle is the new API for specifying how you want your currency symbol displayed in
    // the prefix/suffix.  Occurrences of '¤' will be replaced with that choice.
    CurrencyStyle style = properties.getCurrencyStyle();
    String preferredSymbol = (style == CurrencyStyle.ISO_CODE) ? isoCode : symbol;

    // Previously, the user was also able to specify '¤¤' and '¤¤¤' directly into the prefix or
    // suffix, which is how the user specified whether they wanted the ISO code or long name.
    // For backwards compatibility support, that feature is implemented here.

    GeneralPluralModifier mod = new GeneralPluralModifier();
    for (StandardPlural plural : StandardPlural.VALUES) {
      String longName = currency.getName(uloc, Currency.PLURAL_LONG_NAME, plural.name(), null);
      PNAffixGenerator.Result result =
          pnag.getModifiers(symbols, preferredSymbol, isoCode, longName, properties);
      mod.put(plural, result.positive, result.negative);
    }

    return mod;
  }

  public static Rounder getCurrencyRounder(DecimalFormatSymbols symbols, IProperties properties) {
    Currency currency = getCurrencyOrNull(symbols, properties);

    if (currency == null) {
      // There is a currency symbol in the pattern, but we have no currency available to use.
      return Rounder.getDefaultRounder(properties);
    }

    Currency.CurrencyUsage currencyUsage = properties.getCurrencyUsage();
    double incrementDouble = currency.getRoundingIncrement(currencyUsage);
    int fractionDigits = currency.getDefaultFractionDigits(currencyUsage);

    // TODO: The object clone could be avoided here if the contructors to IntervalRounder and
    // MagnitudeRounder took all of their properties directly instead of in the wrapper object.
    // Is avoiding the object creation worth the increase in code complexity?
    IProperties cprops = properties.clone();
    cprops.setMinimumFractionDigits(fractionDigits);
    cprops.setMaximumFractionDigits(fractionDigits);

    if (incrementDouble > 0.0) {
      cprops.setRoundingInterval(new BigDecimal(Double.toString(incrementDouble)));
      return Rounder.IntervalRounder.getInstance(cprops);
    } else {
      return Rounder.MagnitudeRounder.getInstance(cprops);
    }
  }
}
