// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.formatters;

import java.util.HashMap;
import java.util.Map;

import com.ibm.icu.impl.ICUData;
import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.impl.UResource;
import com.ibm.icu.impl.number.Format;
import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.Modifier;
import com.ibm.icu.impl.number.ModifierHolder;
import com.ibm.icu.impl.number.PNAffixGenerator;
import com.ibm.icu.impl.number.PatternString;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.impl.number.Rounder;
import com.ibm.icu.impl.number.rounders.SignificantDigitsRounder;
import com.ibm.icu.impl.number.rounders.SignificantDigitsRounder.SignificantDigitsMode;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberingSystem;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.UResourceBundle;

public class CompactDecimalFormat extends Format.BeforeFormat {
  public static interface IProperties
      extends RoundingFormat.IProperties, CurrencyFormat.ICurrencyProperties {

    static CompactStyle DEFAULT_COMPACT_STYLE = null;

    /** @see #setCompactStyle */
    public CompactStyle getCompactStyle();

    /**
     * Use compact decimal formatting with the specified {@link CompactStyle}. CompactStyle.SHORT
     * produces output like "10K" in locale <em>en-US</em>, whereas CompactStyle.LONG produces
     * output like "10 thousand" in that locale.
     *
     * @param compactStyle The style of prefixes/suffixes to append.
     * @return The property bag, for chaining.
     */
    public IProperties setCompactStyle(CompactStyle compactStyle);
  }

  public static boolean useCompactDecimalFormat(IProperties properties) {
    return properties.getCompactStyle() != IProperties.DEFAULT_COMPACT_STYLE;
  }

  static final int MAX_DIGITS = 15;

  // Properties
  private final CompactDecimalData data;
  private final Rounder rounder;
  private final CompactStyle style; // retained for exporting only

  public static CompactDecimalFormat getInstance(
      DecimalFormatSymbols symbols, IProperties properties) {
    return getInstance(symbols, getRounder(properties), properties);
  }

  public static CompactDecimalFormat getInstance(
      DecimalFormatSymbols symbols, Rounder rounder, IProperties properties) {
    return new CompactDecimalFormat(
        getData(symbols, properties), rounder, properties.getCompactStyle());
  }

  private static Rounder getRounder(IProperties properties) {
    // Use rounding settings if they were specified, or else use the default CDF rounder.
    Rounder rounder = RoundingFormat.getDefaultOrNull(properties);
    if (rounder == null) {
      rounder =
          SignificantDigitsRounder.getInstance(
              SignificantDigitsRounder.getThreadLocalProperties()
                  .setMinimumSignificantDigits(2)
                  .setMaximumFractionDigits(0)
                  .setSignificantDigitsMode(SignificantDigitsMode.ENSURE_MINIMUM_SIGNIFICANT));
    }
    return rounder;
  }

  protected static final ThreadLocal<Map<CompactDecimalFingerprint, CompactDecimalData>>
      threadLocalDataCache =
          new ThreadLocal<Map<CompactDecimalFingerprint, CompactDecimalData>>() {
            @Override
            protected Map<CompactDecimalFingerprint, CompactDecimalData> initialValue() {
              return new HashMap<>();
            }
          };

  private static CompactDecimalData getData(DecimalFormatSymbols symbols, IProperties properties) {
    // See if we already have a data object based on the fingerprint
    CompactDecimalFingerprint fingerprint = new CompactDecimalFingerprint(symbols, properties);
    CompactDecimalData data = threadLocalDataCache.get().get(fingerprint);
    if (data != null) return data;

    // Make data bundle object
    data = new CompactDecimalData();
    ULocale ulocale = symbols.getULocale();
    CompactDecimalDataSink sink = new CompactDecimalDataSink(data, symbols, properties);
    String nsName = NumberingSystem.getInstance(ulocale).getName();
    ICUResourceBundle r =
        (ICUResourceBundle) UResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, ulocale);
    r.getAllItemsWithFallback("NumberElements/" + nsName, sink);
    if (data.isEmpty() && !nsName.equals("latn")) {
      r.getAllItemsWithFallback("NumberElements/latn", sink);
    }
    if (sink.exception != null) {
      throw sink.exception;
    }
    threadLocalDataCache.get().put(fingerprint, data);
    return data;
  }

  private CompactDecimalFormat(CompactDecimalData data, Rounder rounder, CompactStyle style) {
    this.data = data;
    this.rounder = rounder;
    this.style = style;
  }

  @Override
  public void before(FormatQuantity input, ModifierHolder mods, PluralRules rules) {
    //    int multiplier = rounder.chooseMultiplierAndApply(input, data);
    //    StandardPlural plural = input.getStandardPlural(rules);
    //    boolean isNegative = input.isNegative();
    //    mods.add(data.getModifier(input.getMagnitude() - multiplier, plural, isNegative));
    apply(input, mods, rules, rounder, data);
  }

  @Override
  protected void before(FormatQuantity input, ModifierHolder mods) {
    throw new UnsupportedOperationException();
  }

  public static void apply(
      FormatQuantity input,
      ModifierHolder mods,
      PluralRules rules,
      DecimalFormatSymbols symbols,
      IProperties properties) {
    Rounder rounder = getRounder(properties);
    CompactDecimalData data = getData(symbols, properties);
    apply(input, mods, rules, rounder, data);
  }

  private static void apply(
      FormatQuantity input,
      ModifierHolder mods,
      PluralRules rules,
      Rounder rounder,
      CompactDecimalData data) {

    // Treat zero as if it had magnitude 0
    int magnitude;
    if (input.isZero()) {
      magnitude = 0;
      rounder.apply(input);
    } else {
      int multiplier = rounder.chooseMultiplierAndApply(input, data);
      magnitude = input.getMagnitude() - multiplier;
    }

    StandardPlural plural = input.getStandardPlural(rules);
    boolean isNegative = input.isNegative();
    mods.add(data.getModifier(magnitude, plural, isNegative));
  }

  @Override
  public void export(Properties properties) {
    properties.setCompactStyle(style);
    rounder.export(properties);
  }

  static class CompactDecimalData implements Rounder.MultiplierGenerator {
    final Modifier[] mods;
    final byte[] multipliers;
    boolean isEmpty;
    int largestMagnitude;

    CompactDecimalData() {
      mods = new Modifier[(MAX_DIGITS + 1) * StandardPlural.COUNT * 2];
      multipliers = new byte[MAX_DIGITS + 1];
      isEmpty = true;
      largestMagnitude = -1;
    }

    boolean isEmpty() {
      return isEmpty;
    }

    @Override
    public int getMultiplier(int magnitude) {
      if (magnitude < 0) {
        return 0;
      }
      if (magnitude > largestMagnitude) {
        magnitude = largestMagnitude;
      }
      return multipliers[magnitude];
    }

    CompactDecimalData setMultiplier(int magnitude, byte multiplier) {
      multipliers[magnitude] = multiplier;
      isEmpty = false;
      if (magnitude > largestMagnitude) largestMagnitude = magnitude;
      return this;
    }

    Modifier getModifier(int magnitude, StandardPlural plural, boolean isNegative) {
      // TODO: Should this fall back to the OTHER plural?
      if (magnitude < 0) {
        return null;
      }
      if (magnitude > largestMagnitude) {
        magnitude = largestMagnitude;
      }
      return mods[modIndex(magnitude, plural, isNegative)];
    }

    CompactDecimalData setModifiers(
        Modifier positive, Modifier negative, int magnitude, StandardPlural plural) {
      mods[modIndex(magnitude, plural, false)] = positive;
      mods[modIndex(magnitude, plural, true)] = negative;
      isEmpty = false;
      if (magnitude > largestMagnitude) largestMagnitude = magnitude;
      return this;
    }

    private static final int modIndex(int magnitude, StandardPlural plural, boolean isNegative) {
      return magnitude * StandardPlural.COUNT * 2 + plural.ordinal() * 2 + (isNegative ? 1 : 0);
    }
  }

  // Should this be public or internal?
  static enum CompactType {
    DECIMAL,
    CURRENCY
  }

  static class CompactDecimalFingerprint {
    // TODO: Add more stuff to the fingerprint, like the symbols used by PNAffixGenerator
    final CompactStyle compactStyle;
    final CompactType compactType;
    final ULocale uloc;
    final String currencySymbol;

    CompactDecimalFingerprint(DecimalFormatSymbols symbols, IProperties properties) {
      if (properties.getCurrency() != CurrencyFormat.ICurrencyProperties.DEFAULT_CURRENCY) {
        compactType = CompactType.CURRENCY;
        currencySymbol = CurrencyFormat.getCurrencySymbol(symbols, properties);
      } else {
        compactType = CompactType.DECIMAL;
        currencySymbol = symbols.getCurrencySymbol(); // fallback; should remain unused
      }
      compactStyle = properties.getCompactStyle();
      uloc = symbols.getULocale();
    }

    @Override
    public boolean equals(Object _other) {
      if (_other == null) return false;
      if (this == _other) return true;
      CompactDecimalFingerprint other = (CompactDecimalFingerprint) _other;
      if (compactStyle != other.compactStyle) return false;
      if (compactType != other.compactType) return false;
      if (currencySymbol != other.currencySymbol) {
        // String comparison with null handling
        if (currencySymbol == null || other.currencySymbol == null) return false;
        if (!currencySymbol.equals(other.currencySymbol)) return false;
      }
      if (!uloc.equals(other.uloc)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int hashCode = 0;
      if (compactStyle != null) hashCode ^= compactStyle.hashCode();
      if (compactType != null) hashCode ^= compactType.hashCode();
      if (uloc != null) hashCode ^= uloc.hashCode();
      if (currencySymbol != null) hashCode ^= currencySymbol.hashCode();
      return hashCode;
    }
  }

  private static final class CompactDecimalDataSink extends UResource.Sink {

    final CompactDecimalData data;
    final DecimalFormatSymbols symbols;
    final CompactStyle compactStyle;
    final CompactType compactType;
    final String currencySymbol;
    final PNAffixGenerator pnag;
    IllegalArgumentException exception;

    /*
     * NumberElements{              <-- top (numbering system table)
     *  latn{                       <-- patternsTable (one per numbering system)
     *    patternsLong{             <-- formatsTable (one per pattern)
     *      decimalFormat{          <-- powersOfTenTable (one per format)
     *        1000{                 <-- pluralVariantsTable (one per power of ten)
     *          one{"0 thousand"}   <-- plural variant and template
     */

    public CompactDecimalDataSink(
        CompactDecimalData data, DecimalFormatSymbols symbols, IProperties properties) {
      this.data = data;
      this.symbols = symbols;

      // CompactDecimalFormat does not need to worry about the same constraints as non-compact
      // currency formatting needs to consider, like the currency rounding mode and the currency
      // long names with plural forms.

      if (properties.getCurrency() != CurrencyFormat.ICurrencyProperties.DEFAULT_CURRENCY) {
        compactType = CompactType.CURRENCY;
        currencySymbol = CurrencyFormat.getCurrencySymbol(symbols, properties);
      } else {
        compactType = CompactType.DECIMAL;
        currencySymbol = symbols.getCurrencySymbol(); // fallback; should remain unused
      }

      compactStyle = properties.getCompactStyle();
      pnag = PNAffixGenerator.getThreadLocalInstance();
    }

    @Override
    public void put(UResource.Key key, UResource.Value value, boolean isRoot) {
      if (!data.isEmpty()) {
        return;
      }

      UResource.Table patternsTable = value.getTable();
      for (int i1 = 0; patternsTable.getKeyAndValue(i1, key, value); ++i1) {
        if (key.contentEquals("patternsShort") && compactStyle == CompactStyle.SHORT) {
        } else if (key.contentEquals("patternsLong") && compactStyle == CompactStyle.LONG) {
        } else {
          continue;
        }

        // traverse into the table of formats
        UResource.Table formatsTable = value.getTable();
        for (int i2 = 0; formatsTable.getKeyAndValue(i2, key, value); ++i2) {
          if (key.contentEquals("decimalFormat") && compactType == CompactType.DECIMAL) {
          } else if (key.contentEquals("currencyFormat") && compactType == CompactType.CURRENCY) {
          } else {
            continue;
          }

          // traverse into the table of powers of ten
          UResource.Table powersOfTenTable = value.getTable();
          for (int i3 = 0; powersOfTenTable.getKeyAndValue(i3, key, value); ++i3) {
            try {

              // Assumes that the keys are always of the form "10000" where the magnitude is the
              // length of the key minus one
              byte magnitude = (byte) (key.length() - 1);

              // Silently ignore divisors that are too big.
              if (magnitude >= MAX_DIGITS) continue;

              byte multiplier = 0;
              boolean seenOther = false;

              // Iterate over the plural variants ("one", "other", etc)
              UResource.Table pluralVariantsTable = value.getTable();
              for (int i4 = 0; pluralVariantsTable.getKeyAndValue(i4, key, value); ++i4) {
                // Spec: Skip if the value is just '0'
                String patternString = value.toString();
                if (patternString.equals("0")) continue;

                StandardPlural plural = StandardPlural.fromString(key.toString());
                Properties properties = PatternString.parseToProperties(value.toString());

                // The magnitude multiplier is the difference between the magnitude and the number
                // of zeros in the pattern, getMinimumIntegerDigits.
                byte _multiplier = (byte) -(magnitude - properties.getMinimumIntegerDigits() + 1);
                if (multiplier > 0 && multiplier != _multiplier) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Different number of zeros for same power of ten in compact decimal format data for locale '%s', style '%s', type '%s'",
                          symbols.getULocale().toString(),
                          compactStyle.toString(),
                          compactType.toString()));
                }
                multiplier = _multiplier;

                seenOther = seenOther || (plural == StandardPlural.OTHER);

                PNAffixGenerator.Result result =
                    pnag.getModifiers(symbols, currencySymbol, properties);
                data.setModifiers(result.positive, result.negative, magnitude, plural);
              }

              data.setMultiplier(magnitude, multiplier);

            } catch (IllegalArgumentException e) {
              exception = e;
              continue;
            }
          }

          // We want only one table of compact decimal formats, so if we get here, stop consuming.
          // The data.isEmpty() check will prevent further bundles from being traversed.
          return;
        }
      }
    }
  }
}
