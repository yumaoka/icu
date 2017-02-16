// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.impl.TextTrieMap;
import com.ibm.icu.impl.number.Parse.ParseMode;
import com.ibm.icu.impl.number.formatters.BigDecimalMultiplier;
import com.ibm.icu.impl.number.formatters.CurrencyFormat;
import com.ibm.icu.impl.number.formatters.MagnitudeMultiplier;
import com.ibm.icu.impl.number.formatters.PaddingFormat;
import com.ibm.icu.impl.number.formatters.PositiveDecimalFormat;
import com.ibm.icu.impl.number.formatters.PositiveNegativeAffixFormat;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.CurrencyPluralInfo;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.Currency.CurrencyStringInfo;
import com.ibm.icu.util.CurrencyAmount;
import com.ibm.icu.util.ULocale;

/**
 * A parser designed to convert an arbitrary human-generated string to its best representation as a
 * number: a long, a BigInteger, or a BigDecimal.
 *
 * <p>The parser may traverse multiple parse paths in the same strings if there is ambiguity. For
 * example, the string "12,345.67" has two main interpretations: it could be "12.345" in a locale
 * that uses '.' as the grouping separator, or it could be "12345.67" in a locale that uses ',' as
 * the grouping separator. Since the second option has a longer parse path (consumes more of the
 * input string), the parser will accept the second option.
 */
public class Parse {

  /** Controls the set of rules for parsing a string. */
  public static enum ParseMode {
    /**
     * In Lenient mode, the parser attempts to recover from typographical errors. This includes
     * ignoring arbitrary whitespace in the middle of the string to be parsed. Most users should use
     * Lenient mode.
     */
    LENIENT,

    /**
     * In Strict mode, arbitrary whitespace is not allowed in the middle of the string, and the
     * prefix must match the suffix.
     */
    STRICT
  }

  /** The set of properties required for {@link Parse}. Accepts a {@link Properties} object. */
  public static interface IProperties
      extends PositiveNegativeAffixFormat.IProperties,
          PaddingFormat.IProperties,
          CurrencyFormat.ICurrencyProperties,
          BigDecimalMultiplier.IProperties,
          MagnitudeMultiplier.IProperties,
          PositiveDecimalFormat.IProperties {

    boolean DEFAULT_PARSE_INTEGER_ONLY = false;

    /** @see #setParseIntegerOnly */
    public boolean getParseIntegerOnly();

    /**
     * Whether to ignore the fractional part of numbers. For example, parses "123.4" to "123"
     * instead of "123.4".
     *
     * @param parseIntegerOnly true to parse integers only; false to parse integers with their
     *     fraction parts
     * @return The property bag, for chaining.
     */
    public IProperties setParseIntegerOnly(boolean parseIntegerOnly);

    boolean DEFAULT_PARSE_IGNORE_EXPONENT = false;

    /** @see #setParseIgnoreExponent */
    public boolean getParseIgnoreExponent();

    /**
     * Whether to ignore the exponential part of numbers. For example, parses "123E4" to "123"
     * instead of "1230000".
     *
     * @param parseIgnoreExponent true to ignore exponents; false to parse them.
     * @return The property bag, for chaining.
     */
    public IProperties setParseIgnoreExponent(boolean parseIgnoreExponent);

    boolean DEFAULT_DECIMAL_PATTERN_MATCH_REQUIRED = false;

    /** @see #setDecimalPatternMatchRequired */
    public boolean getDecimalPatternMatchRequired();

    /**
     * Whether to require that a decimal point be present. If a decimal point is not present, the
     * parse will not succeed: null will be returned from <code>parse()</code>, and an error index
     * will be set in the {@link ParsePosition}.
     *
     * @param decimalPatternMatchRequired true to set an error if decimal is not present
     * @return The property bag, for chaining.
     */
    public IProperties setDecimalPatternMatchRequired(boolean decimalPatternMatchRequired);

    ParseMode DEFAULT_PARSE_MODE = ParseMode.LENIENT;

    /** @see #setParseMode */
    public ParseMode getParseMode();

    /**
     * Controls certain rules for how strict this parser is when reading strings. See {@link
     * ParseMode#LENIENT} and {@link ParseMode#STRICT}.
     *
     * @param parseMode Either {@link ParseMode#LENIENT} or {@link ParseMode#STRICT}.
     * @return The property bag, for chaining.
     */
    public IProperties setParseMode(ParseMode parseMode);

    //    boolean DEFAULT_PARSE_CURRENCY = false;
    //
    //    /** @see #setParseCurrency */
    //    public boolean getParseCurrency();
    //
    //    /**
    //     * Whether to parse currency codes and currency names in the string.
    //     *
    //     * <p>Due to the large number of possible currencies, enabling this option may impact the
    //     * runtime of the parse operation.
    //     *
    //     * @param parseCurrency true to parse arbitrary currency codes and currency names; false to
    //     *     disable. (Default is false)
    //     * @return The property bag, for chaining.
    //     */
    //    public IProperties setParseCurrency(boolean parseCurrency);

    boolean DEFAULT_PARSE_TO_BIG_DECIMAL = false;

    /** @see #setParseToBigDecimal */
    public boolean getParseToBigDecimal();

    /**
     * Whether to always return a BigDecimal from {@link Parse#parse} and all other parse methods.
     * By default, a Long or a BigInteger are returned when possible.
     *
     * @param parseToBigDecimal true to always return a BigDecimal; false to return a Long or a
     *     BigInteger when possible.
     * @return The property bag, for chaining.
     */
    public IProperties setParseToBigDecimal(boolean parseToBigDecimal);

    boolean DEFAULT_PARSE_CASE_SENSITIVE = false;

    /** @see #setParseCaseSensitive */
    public boolean getParseCaseSensitive();

    /**
     * Whether to require cases to match when parsing strings; default is false. Case sensitivity
     * applies to prefixes, suffixes, the exponent separator, the symbol "NaN", and the infinity
     * symbol. Grouping separators, decimal separators, and padding are always case-sensitive.
     * Currencies are always case-insensitive.
     *
     * @param parseCaseSensitive true to be case-sensitive when parsing; false to allow any case.
     * @return The property bag, for chaining.
     */
    public IProperties setParseCaseSensitive(boolean parseCaseSensitive);
  }

  /**
   * @see #parse(String, ParsePosition, ParseMode, boolean, boolean, IProperties,
   *     DecimalFormatSymbols)
   */
  private static enum StateName {
    BEFORE_PREFIX,
    AFTER_PREFIX,
    AFTER_INTEGER_DIGIT,
    AFTER_FRACTION_DIGIT,
    AFTER_EXPONENT_SEPARATOR,
    AFTER_EXPONENT_DIGIT,
    BEFORE_SUFFIX,
    BEFORE_SUFFIX_SEEN_EXPONENT,
    AFTER_SUFFIX,
    INSIDE_CURRENCY,
    INSIDE_STRING,
    INSIDE_AFFIX_PATTERN;
  }

  // TODO: Does this set make sense for the whitespace characters?
  private static final UnicodeSet UNISET_WHITESPACE =
      new UnicodeSet("[[:whitespace:][\\u2000-\\u200D]]").freeze();

  // BiDi characters are skipped over and ignored at any point in the string, even in strict mode.
  private static final UnicodeSet UNISET_BIDI =
      new UnicodeSet("[[\\u200E\\u200F\\u061C]]").freeze();

  // TODO: Re-generate these sets from the database. They probably haven't been updated in a while.
  private static final UnicodeSet UNISET_PERIOD_LIKE =
      new UnicodeSet("[.\\u2024\\u3002\\uFE12\\uFE52\\uFF0E\\uFF61]").freeze();
  private static final UnicodeSet UNISET_STRICT_PERIOD_LIKE =
      new UnicodeSet("[.\\u2024\\uFE52\\uFF0E\\uFF61]").freeze();
  private static final UnicodeSet UNISET_COMMA_LIKE =
      new UnicodeSet("[,\\u060C\\u066B\\u3001\\uFE10\\uFE11\\uFE50\\uFE51\\uFF0C\\uFF64]").freeze();
  private static final UnicodeSet UNISET_STRICT_COMMA_LIKE =
      new UnicodeSet("[,\\u066B\\uFE10\\uFE50\\uFF0C]").freeze();
  private static final UnicodeSet UNISET_OTHER_GROUPING_SEPARATORS =
      new UnicodeSet(
              "[\\ '\\u00A0\\u066C\\u2000-\\u200A\\u2018\\u2019\\u202F\\u205F\\u3000\\uFF07]")
          .freeze();

  private enum SeparatorType {
    COMMA_LIKE,
    PERIOD_LIKE,
    OTHER_GROUPING,
    UNKNOWN;

    static SeparatorType fromCp(int cp, boolean strict) {
      UnicodeSet commaLike = strict ? UNISET_STRICT_COMMA_LIKE : UNISET_COMMA_LIKE;
      if (commaLike.contains(cp)) return COMMA_LIKE;
      UnicodeSet periodLike = strict ? UNISET_STRICT_PERIOD_LIKE : UNISET_PERIOD_LIKE;
      if (periodLike.contains(cp)) return PERIOD_LIKE;
      UnicodeSet other = UNISET_OTHER_GROUPING_SEPARATORS;
      if (other.contains(cp)) return OTHER_GROUPING;
      return UNKNOWN;
    }
  }

  /**
   * Holds a snapshot in time of a single parse path. This includes the digits seen so far, the
   * current state name, and other properties like the grouping separator used on this parse path,
   * details about the exponent and negative signs, etc.
   */
  private static class StateItem {
    // Parser state:
    StateName name;
    int score;

    // Numerical value:
    FormatQuantity4 fq = new FormatQuantity4();
    int numDigits;
    int trailingZeros;
    int exponent;

    // Other items that we've seen:
    int groupingCp;
    long groupingWidths;
    String isoCode;
    boolean sawNegative;
    boolean sawNegativeExponent;
    boolean sawDecimal;
    boolean sawCurrency;
    boolean sawNaN;
    boolean sawInfinity;
    AffixHolder affix;
    boolean sawPrefix;
    boolean sawSuffix;

    // Data for intermediate parsing steps:
    StateName returnTo1;
    StateName returnTo2;
    // For string literals:
    CharSequence currentString;
    int currentOffset;
    // For affix patterns:
    CharSequence currentAffixPattern;
    long currentStepwiseParserTag;
    // For currency:
    TextTrieMap<CurrencyStringInfo>.ParseState currentTrieState;

    /**
     * Clears the instance so that it can be re-used.
     *
     * @return Myself, for chaining.
     */
    StateItem clear() {
      // Parser state:
      name = StateName.BEFORE_PREFIX;
      score = 0;

      // Numerical value:
      fq.clear();
      numDigits = 0;
      trailingZeros = 0;
      exponent = 0;

      // Other items we've seen:
      groupingCp = -1;
      groupingWidths = 0L;
      isoCode = null;
      sawNegative = false;
      sawNegativeExponent = false;
      sawDecimal = false;
      sawCurrency = false;
      sawNaN = false;
      sawInfinity = false;
      affix = null;
      sawPrefix = false;
      sawSuffix = false;

      // Data for intermediate parsing steps:
      returnTo1 = null;
      returnTo2 = null;
      currentString = null;
      currentOffset = 0;
      currentAffixPattern = null;
      currentStepwiseParserTag = 0L;
      currentTrieState = null;

      return this;
    }

    /**
     * Sets the internal value of this instance equal to another instance.
     *
     * @param other The instance to copy from.
     * @return Myself, for chaining.
     */
    StateItem copyFrom(StateItem other) {
      // Parser state:
      name = other.name;
      score = other.score;

      // Numerical value:
      fq.copyFrom(other.fq);
      numDigits = other.numDigits;
      trailingZeros = other.trailingZeros;
      exponent = other.exponent;

      // Other items we've seen:
      groupingCp = other.groupingCp;
      groupingWidths = other.groupingWidths;
      isoCode = other.isoCode;
      sawNegative = other.sawNegative;
      sawNegativeExponent = other.sawNegativeExponent;
      sawDecimal = other.sawDecimal;
      sawCurrency = other.sawCurrency;
      sawNaN = other.sawNaN;
      sawInfinity = other.sawInfinity;
      affix = other.affix;
      sawPrefix = other.sawPrefix;
      sawSuffix = other.sawSuffix;

      // Data for intermediate parsing steps:
      returnTo1 = other.returnTo1;
      returnTo2 = other.returnTo2;
      currentString = other.currentString;
      currentOffset = other.currentOffset;
      currentAffixPattern = other.currentAffixPattern;
      currentStepwiseParserTag = other.currentStepwiseParserTag;
      currentTrieState = other.currentTrieState;

      return this;
    }

    /**
     * Adds a digit to the internal representation of this instance.
     *
     * @param digit The digit that was read from the string.
     * @param fraction Whether the digit occured after the decimal point.
     */
    void appendDigit(byte digit, boolean fraction) {
      numDigits++;
      if (fraction && digit == 0) {
        trailingZeros++;
      } else if (fraction) {
        fq.appendDigit(digit, trailingZeros, false);
        trailingZeros = 0;
      } else {
        fq.appendDigit(digit, 0, true);
      }
    }

    void appendExponent(int digit) {
      exponent = exponent * 10 + digit;
    }

    /** @return Whether or not this item contains a valid number. */
    public boolean hasNumber() {
      return numDigits > 0 || sawNaN || sawInfinity;
    }

    /**
     * Converts the internal digits from this instance into a Number, preferring a Long, then a
     * BigInteger, then a BigDecimal. A Double is used for NaN, infinity, and -0.0.
     *
     * @return The Number. Never null.
     */
    Number toNumber(IProperties properties) {
      // Check for NaN, infinity, and -0.0
      if (sawNaN) {
        return Double.NaN;
      }
      if (sawInfinity) {
        if (sawNegative) {
          return Double.NEGATIVE_INFINITY;
        } else {
          return Double.POSITIVE_INFINITY;
        }
      }
      if (fq.isZero() && sawNegative) {
        return -0.0;
      }

      // Multipliers must be applied in reverse.
      BigDecimal multiplier = properties.getMultiplier();
      if (properties.getMagnitudeMultiplier() != 0) {
        if (multiplier == null) multiplier = BigDecimal.ONE;
        multiplier = multiplier.scaleByPowerOfTen(properties.getMagnitudeMultiplier());
      }
      boolean forceBigDecimal = properties.getParseToBigDecimal();
      int delta = (sawNegativeExponent ? -1 : 1) * exponent;

      // Construct the output number.
      BigDecimal result = fq.toBigDecimal();
      if (sawNegative) result = result.negate();
      result = result.scaleByPowerOfTen(delta);
      if (multiplier != null) result = result.divide(multiplier);
      result = result.stripTrailingZeros();
      if (forceBigDecimal || result.scale() > 0) {
        return result;
      } else if (-result.scale() + result.precision() <= 18) {
        return result.longValueExact();
      } else {
        return result.toBigIntegerExact();
      }
    }

    /**
     * Converts the internal digits to a number, and also associates the number with the parsed
     * currency.
     *
     * @return The CurrencyAmount. Never null.
     */
    public CurrencyAmount toCurrencyAmount(IProperties properties) {
      assert isoCode != null;
      Number number = toNumber(properties);
      Currency currency = Currency.getInstance(isoCode);
      return new CurrencyAmount(number, currency);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("<ParserStateItem ");
      sb.append(name.name());
      if (name == StateName.INSIDE_STRING) {
        sb.append("{");
        sb.append(currentString);
        sb.append(":");
        sb.append(currentOffset);
        sb.append("}");
      }
      if (name == StateName.INSIDE_AFFIX_PATTERN) {
        sb.append("{");
        sb.append(currentAffixPattern);
        sb.append(":");
        sb.append(AffixPatternUtils.getOffset(currentStepwiseParserTag));
        sb.append("}");
      }
      sb.append(" ");
      sb.append(fq.toBigDecimal());
      sb.append(" grouping:");
      sb.append(groupingCp == -1 ? new char[] {'?'} : Character.toChars(groupingCp));
      sb.append(" widths:");
      sb.append(Long.toHexString(groupingWidths));
      sb.append(" seen:");
      sb.append(sawNegative ? 1 : 0);
      sb.append(sawNegativeExponent ? 1 : 0);
      sb.append(sawDecimal ? 1 : 0);
      sb.append(sawNaN ? 1 : 0);
      sb.append(sawInfinity ? 1 : 0);
      sb.append(sawPrefix ? 1 : 0);
      sb.append(sawSuffix ? 1 : 0);
      sb.append(" score:");
      sb.append(score);
      sb.append(" affix:");
      sb.append(affix);
      sb.append(" currency:");
      sb.append(isoCode);
      sb.append(">");
      return sb.toString();
    }
  }

  /**
   * Holds an ordered list of {@link StateItem} and other metadata about the string to be parsed.
   * There are two internal arrays of {@link StateItem}, which are swapped back and forth in order
   * to avoid object creations. The items in one array can be populated at the same time that items
   * in the other array are being read from.
   */
  private static class ParserState {

    // Basic ParserStateItem lists:
    StateItem[] items = new StateItem[16];
    StateItem[] prevItems = new StateItem[16];
    int length;
    int prevLength;

    // Properties and Symbols memory:
    IProperties properties;
    DecimalFormatSymbols symbols;
    ParseMode mode;
    boolean caseSensitive;

    // Other pre-computed fields:
    int decimalCp1;
    int decimalCp2;
    int groupingCp1;
    int groupingCp2;
    SeparatorType decimalType1;
    SeparatorType decimalType2;
    SeparatorType groupingType1;
    SeparatorType groupingType2;
    Set<AffixHolder> affixHolders = new HashSet<AffixHolder>();

    ParserState() {
      for (int i = 0; i < items.length; i++) {
        items[i] = new StateItem();
        prevItems[i] = new StateItem();
      }
    }

    /**
     * Clears the internal state in order to prepare for parsing a new string.
     *
     * @return Myself, for chaining.
     */
    ParserState clear() {
      length = 0;
      prevLength = 0;
      affixHolders.clear();
      return this;
    }

    /**
     * Swaps the internal arrays of {@link StateItem}. Sets the length of the primary list to zero,
     * so that it can be appended to.
     */
    void swap() {
      StateItem[] temp = prevItems;
      prevItems = items;
      items = temp;
      prevLength = length;
      length = 0;
    }

    /**
     * Swaps the internal arrays of {@link StateItem}. Sets the length of the primary list to the
     * length of the previous list, so that it can be read from.
     */
    void swapBack() {
      StateItem[] temp = prevItems;
      prevItems = items;
      items = temp;
      length = prevLength;
      prevLength = 0;
    }

    /**
     * Gets the next available {@link StateItem} from the primary list for writing. This method
     * should be thought of like a list append method, except that there are no object creations
     * taking place.
     *
     * <p>It is the caller's responsibility to call either {@link StateItem#clear} or {@link
     * StateItem#copyFrom} on the returned object.
     *
     * @return A dirty {@link StateItem}.
     */
    StateItem getNext() {
      if (length >= items.length) {
        // TODO: What to do here? Expand the array?
        // This case is rare and would happen only with specially designed input.
        // For now, just overwrite the last entry.
        length = items.length - 1;
      }
      StateItem item = items[length];
      length++;
      return item;
    }

    /** @return The index of the last inserted StateItem via a call to {@link #getNext}. */
    public int lastInsertedIndex() {
      assert length > 0;
      return length - 1;
    }

    /**
     * Gets a {@link StateItem} from the primary list. Assumes that the item has already been added
     * via a call to {@link #getNext}.
     *
     * @param i The index of the item to get.
     * @return The item.
     */
    public StateItem getItem(int i) {
      assert i >= 0 && i < length;
      return items[i];
    }
  }

  private static class AffixHolder {
    final String p; // prefix
    final String s; // suffix
    final boolean strings;
    final boolean negative;

    static final AffixHolder EMPTY_POSITIVE = new AffixHolder("", "", true, false);
    static final AffixHolder EMPTY_NEGATIVE = new AffixHolder("", "", true, true);
    static final AffixHolder DEFAULT_POSITIVE = new AffixHolder("+", "", false, false);
    static final AffixHolder DEFAULT_NEGATIVE = new AffixHolder("-", "", false, true);

    static void addToState(ParserState state, IProperties properties) {
      AffixHolder pp = fromPropertiesPositivePattern(properties);
      AffixHolder np = fromPropertiesNegativePattern(properties);
      AffixHolder ps = fromPropertiesPositiveString(properties);
      AffixHolder ns = fromPropertiesNegativeString(properties);
      if (pp == null && ps == null) {
        if (properties.getAlwaysShowPlusSign()) {
          state.affixHolders.add(DEFAULT_POSITIVE);
        } else {
          state.affixHolders.add(EMPTY_POSITIVE);
        }
      } else {
        if (pp != null) state.affixHolders.add(pp);
        if (ps != null) state.affixHolders.add(ps);
      }
      if (np == null && ns == null) {
        state.affixHolders.add(DEFAULT_NEGATIVE);
      } else {
        if (np != null) state.affixHolders.add(np);
        if (ns != null) state.affixHolders.add(ns);
      }
    }

    static AffixHolder fromPropertiesPositivePattern(IProperties properties) {
      CharSequence ppp = properties.getPositivePrefixPattern();
      CharSequence psp = properties.getPositiveSuffixPattern();
      return getInstance(ppp, psp, false, false);
    }

    static AffixHolder fromPropertiesNegativePattern(IProperties properties) {
      CharSequence npp = properties.getNegativePrefixPattern();
      CharSequence nsp = properties.getNegativeSuffixPattern();
      return getInstance(npp, nsp, false, true);
    }

    static AffixHolder fromPropertiesPositiveString(IProperties properties) {
      CharSequence pp = properties.getPositivePrefix();
      CharSequence ps = properties.getPositiveSuffix();
      return getInstance(pp, ps, true, false);
    }

    static AffixHolder fromPropertiesNegativeString(IProperties properties) {
      CharSequence np = properties.getNegativePrefix();
      CharSequence ns = properties.getNegativeSuffix();
      return getInstance(np, ns, true, true);
    }

    static AffixHolder getInstance(
        CharSequence p, CharSequence s, boolean strings, boolean negative) {
      if (p == null && s == null) return null;
      if (p == null) p = "";
      if (s == null) s = "";
      if (p.length() == 0 && s.length() == 0) return negative ? EMPTY_NEGATIVE : EMPTY_POSITIVE;
      return new AffixHolder(p.toString(), s.toString(), strings, negative);
    }

    AffixHolder(String pp, String sp, boolean strings, boolean negative) {
      this.p = pp;
      this.s = sp;
      this.strings = strings;
      this.negative = negative;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (this == other) return true;
      if (!(other instanceof AffixHolder)) return false;
      AffixHolder _other = (AffixHolder) other;
      if (!p.equals(_other.p)) return false;
      if (!s.equals(_other.s)) return false;
      if (strings != _other.strings) return false;
      if (negative != _other.negative) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return p.hashCode() ^ s.hashCode();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append(p);
      sb.append("|");
      sb.append(s);
      sb.append("|");
      sb.append(strings ? 'S' : 'P');
      sb.append("}");
      return sb.toString();
    }
  }

  /**
   * A class that holds information about all currency affix patterns for the locale. This allows
   * the parser to accept currencies in any format that are valid for the locale.
   */
  private static class CurrencyAffixPatterns {
    private final Set<AffixHolder> set = new HashSet<AffixHolder>();

    private static final ConcurrentHashMap<ULocale, CurrencyAffixPatterns> currencyAffixPatterns =
        new ConcurrentHashMap<ULocale, CurrencyAffixPatterns>();

    static void addToState(ULocale uloc, ParserState state) {
      if (!currencyAffixPatterns.contains(uloc)) {
        // There can be multiple threads computing the same CurrencyAffixPatterns simultaneously,
        // but that scenario is harmless.
        CurrencyAffixPatterns value = new CurrencyAffixPatterns(uloc);
        currencyAffixPatterns.put(uloc, value);
      }
      CurrencyAffixPatterns instance = currencyAffixPatterns.get(uloc);
      state.affixHolders.addAll(instance.set);
    }

    private CurrencyAffixPatterns(ULocale uloc) {
      // Get the basic currency pattern.
      String pattern = NumberFormat.getPattern(uloc, NumberFormat.CURRENCYSTYLE);
      addPattern(pattern);

      // Get the currency plural patterns.
      // TODO: Update this after CurrencyPluralInfo is replaced.
      CurrencyPluralInfo pluralInfo = CurrencyPluralInfo.getInstance(uloc);
      for (StandardPlural plural : StandardPlural.VALUES) {
        pattern = pluralInfo.getCurrencyPluralPattern(plural.getKeyword());
        addPattern(pattern);
      }
    }

    private static final ThreadLocal<Properties> threadLocalProperties =
        new ThreadLocal<Properties>() {
          @Override
          protected Properties initialValue() {
            return new Properties();
          }
        };

    private void addPattern(String pattern) {
      Properties properties = threadLocalProperties.get();
      try {
        PatternString.parseToExistingProperties(pattern, properties);
      } catch (IllegalArgumentException e) {
        // This should only happen if there is a bug in CLDR data. Fail silently.
      }
      set.add(AffixHolder.fromPropertiesPositivePattern(properties));
      set.add(AffixHolder.fromPropertiesNegativePattern(properties));
    }
  }

  protected static final ThreadLocal<ParserState> threadLocalParseState =
      new ThreadLocal<ParserState>() {
        @Override
        protected ParserState initialValue() {
          return new ParserState();
        }
      };

  protected static final ThreadLocal<ParsePosition> threadLocalParsePosition =
      new ThreadLocal<ParsePosition>() {
        @Override
        protected ParsePosition initialValue() {
          return new ParsePosition(0);
        }
      };

  /**
   * @internal
   * @deprecated This API is ICU internal only. TODO: Remove this set from ScientificNumberFormat.
   */
  @Deprecated
  public static final UnicodeSet UNISET_PLUS =
      new UnicodeSet(
              0x002B, 0x002B, 0x207A, 0x207A, 0x208A, 0x208A, 0x2795, 0x2795, 0xFB29, 0xFB29,
              0xFE62, 0xFE62, 0xFF0B, 0xFF0B)
          .freeze();

  /**
   * @internal
   * @deprecated This API is ICU internal only. TODO: Remove this set from ScientificNumberFormat.
   */
  @Deprecated
  public static final UnicodeSet UNISET_MINUS =
      new UnicodeSet(
              0x002D, 0x002D, 0x207B, 0x207B, 0x208B, 0x208B, 0x2212, 0x2212, 0x2796, 0x2796,
              0xFE63, 0xFE63, 0xFF0D, 0xFF0D)
          .freeze();

  public static Number parse(String input, IProperties properties, DecimalFormatSymbols symbols) {
    ParsePosition ppos = threadLocalParsePosition.get();
    ppos.setIndex(0);
    return parse(input, ppos, properties, symbols);
  }

  // TODO: DELETE ME once debugging is finished
  public static volatile boolean DEBUGGING = false;

  /**
   * Implements an iterative parser that maintains a lists of possible states at each code point in
   * the string. At each code point in the string, the list of possible states is updated based on
   * the states coming from the previous code point. The parser stops when it reaches the end of the
   * string or when there are no possible parse paths remaining in the string.
   *
   * <p>TODO: This API is not fully flushed out. Right now this is internal-only.
   *
   * @param input The string to parse.
   * @param ppos A {@link ParsePosition} to hold the index at which parsing stopped.
   * @param properties A property bag, used only for determining the prefix/suffix strings and the
   *     padding character.
   * @param symbols A {@link DecimalFormatSymbols} object, used for determining locale-specific
   *     symbols for grouping/decimal separators, digit strings, and prefix/suffix substitutions.
   * @return A Number matching the parser's best interpretation of the string.
   */
  public static Number parse(
      CharSequence input,
      ParsePosition ppos,
      IProperties properties,
      DecimalFormatSymbols symbols) {
    StateItem best = _parse(input, ppos, false, properties, symbols);
    return (best == null) ? null : best.toNumber(properties);
  }

  public static CurrencyAmount parseCurrency(
      String input, IProperties properties, DecimalFormatSymbols symbols) throws ParseException {
    ParsePosition ppos = threadLocalParsePosition.get();
    ppos.setIndex(0);
    return parseCurrency(input, ppos, properties, symbols);
  }

  public static CurrencyAmount parseCurrency(
      CharSequence input, ParsePosition ppos, IProperties properties, DecimalFormatSymbols symbols)
      throws ParseException {
    StateItem best = _parse(input, ppos, true, properties, symbols);
    return (best == null) ? null : best.toCurrencyAmount(properties);
  }

  private static StateItem _parse(
      CharSequence input,
      ParsePosition ppos,
      boolean parseCurrency,
      IProperties properties,
      DecimalFormatSymbols symbols) {

    if (input == null || ppos == null || properties == null || symbols == null) {
      throw new IllegalArgumentException("All arguments are required for parse.");
    }

    ParseMode mode = properties.getParseMode();
    boolean integerOnly = properties.getParseIntegerOnly();
    boolean ignoreExponent = properties.getParseIgnoreExponent();

    // Set up the initial state
    ParserState state = threadLocalParseState.get().clear();
    state.properties = properties;
    state.symbols = symbols;
    state.mode = mode;
    state.caseSensitive = properties.getParseCaseSensitive();
    state.decimalCp1 = Character.codePointAt(symbols.getDecimalSeparatorString(), 0);
    state.decimalCp2 = Character.codePointAt(symbols.getMonetaryDecimalSeparatorString(), 0);
    state.groupingCp1 = Character.codePointAt(symbols.getGroupingSeparatorString(), 0);
    state.groupingCp2 = Character.codePointAt(symbols.getMonetaryGroupingSeparatorString(), 0);
    state.decimalType1 = SeparatorType.fromCp(state.decimalCp1, mode == ParseMode.STRICT);
    state.decimalType2 = SeparatorType.fromCp(state.decimalCp1, mode == ParseMode.STRICT);
    state.groupingType1 = SeparatorType.fromCp(state.groupingCp1, mode == ParseMode.STRICT);
    state.groupingType2 = SeparatorType.fromCp(state.groupingCp1, mode == ParseMode.STRICT);
    StateItem initialStateItem = state.getNext().clear();
    initialStateItem.name = StateName.BEFORE_PREFIX;

    AffixHolder.addToState(state, properties);
    if (parseCurrency) {
      CurrencyAffixPatterns.addToState(symbols.getULocale(), state);
    }

    if (DEBUGGING) {
      System.out.println("Parsing: " + input);
      System.out.println(properties);
      System.out.println(state.affixHolders);
    }

    // Start walking through the string, one codepoint at a time. Backtracking is not allowed. This
    // is to enforce linear runtime and prevent edge cases that could result in an infinite loop.
    int offset = ppos.getIndex();
    for (; offset < input.length(); ) {
      int cp = Character.codePointAt(input, offset);
      state.swap();
      for (int i = 0; i < state.prevLength; i++) {
        StateItem item = state.prevItems[i];
        if (DEBUGGING) {
          System.out.println(":" + offset + " " + item);
        }
        switch (item.name) {
          case BEFORE_PREFIX:
            // Beginning of string
            acceptBidi(cp, StateName.BEFORE_PREFIX, state, item);
            acceptWhitespace(cp, StateName.BEFORE_PREFIX, state, item);
            acceptPadding(cp, StateName.BEFORE_PREFIX, state, item);
            acceptNan(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptInfinity(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptPrefix(cp, StateName.AFTER_PREFIX, state, item);
            acceptIntegerDigit(cp, StateName.AFTER_INTEGER_DIGIT, state, item);
            if (!integerOnly) {
              acceptDecimalPoint(cp, StateName.AFTER_FRACTION_DIGIT, state, item);
            }
            if (mode == ParseMode.LENIENT) {
              acceptMinusOrPlusSign(cp, StateName.BEFORE_PREFIX, state, item, false);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_PREFIX, state, item);
            }
            break;

          case AFTER_PREFIX:
            // Prefix is consumed
            acceptBidi(cp, StateName.AFTER_PREFIX, state, item);
            acceptPadding(cp, StateName.AFTER_PREFIX, state, item);
            acceptNan(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptInfinity(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptIntegerDigit(cp, StateName.AFTER_INTEGER_DIGIT, state, item);
            if (!integerOnly) {
              acceptDecimalPoint(cp, StateName.AFTER_FRACTION_DIGIT, state, item);
            }
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.AFTER_PREFIX, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.AFTER_PREFIX, state, item);
            }
            break;

          case AFTER_INTEGER_DIGIT:
            // Previous character was an integer digit (or grouping/whitespace)
            acceptBidi(cp, StateName.AFTER_INTEGER_DIGIT, state, item);
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptSuffix(cp, StateName.AFTER_SUFFIX, state, item);
            acceptGrouping(cp, StateName.AFTER_INTEGER_DIGIT, state, item);
            acceptIntegerDigit(cp, StateName.AFTER_INTEGER_DIGIT, state, item);
            if (!integerOnly) {
              acceptDecimalPoint(cp, StateName.AFTER_FRACTION_DIGIT, state, item);
            }
            if (!ignoreExponent) {
              acceptExponentSeparator(cp, StateName.AFTER_EXPONENT_SEPARATOR, state, item);
            }
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case AFTER_FRACTION_DIGIT:
            // We encountered a decimal point
            acceptBidi(cp, StateName.AFTER_FRACTION_DIGIT, state, item);
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptSuffix(cp, StateName.AFTER_SUFFIX, state, item);
            acceptFractionDigit(cp, StateName.AFTER_FRACTION_DIGIT, state, item);
            if (!ignoreExponent) {
              acceptExponentSeparator(cp, StateName.AFTER_EXPONENT_SEPARATOR, state, item);
            }
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case AFTER_EXPONENT_SEPARATOR:
            acceptBidi(cp, StateName.AFTER_EXPONENT_SEPARATOR, state, item);
            acceptMinusOrPlusSign(cp, StateName.AFTER_EXPONENT_SEPARATOR, state, item, true);
            acceptExponentDigit(cp, StateName.AFTER_EXPONENT_DIGIT, state, item);
            break;

          case AFTER_EXPONENT_DIGIT:
            acceptBidi(cp, StateName.AFTER_EXPONENT_DIGIT, state, item);
            acceptPadding(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            acceptExponentDigit(cp, StateName.AFTER_EXPONENT_DIGIT, state, item);
            acceptSuffix(cp, StateName.AFTER_SUFFIX, state, item);
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            break;

          case BEFORE_SUFFIX:
            // Accept whitespace, suffixes, and exponent separators
            acceptBidi(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptSuffix(cp, StateName.AFTER_SUFFIX, state, item);
            if (!ignoreExponent) {
              acceptExponentSeparator(cp, StateName.AFTER_EXPONENT_SEPARATOR, state, item);
            }
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case BEFORE_SUFFIX_SEEN_EXPONENT:
            // Accept whitespace and suffixes but not exponent separators
            acceptBidi(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            acceptPadding(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            acceptSuffix(cp, StateName.AFTER_SUFFIX, state, item);
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            break;

          case AFTER_SUFFIX:
            acceptBidi(cp, StateName.AFTER_SUFFIX, state, item);
            acceptPadding(cp, StateName.AFTER_SUFFIX, state, item);
            if (mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.AFTER_SUFFIX, state, item);
            }
            if (parseCurrency && mode == ParseMode.LENIENT) {
              acceptCurrency(cp, StateName.AFTER_SUFFIX, state, item);
            }
            // Otherwise, do not accept any more characters.
            break;

          case INSIDE_CURRENCY:
            acceptCurrencyOffset(cp, state, item);
            break;

          case INSIDE_STRING:
            acceptStringOffset(cp, state, item);
            // Accept arbitrary bidi and whitespace (if lenient) in the middle of strings.
            if (state.length == 0) {
              acceptBidi(cp, StateName.INSIDE_STRING, state, item);
              if (mode == ParseMode.LENIENT) {
                acceptWhitespace(cp, StateName.INSIDE_STRING, state, item);
              }
            }
            break;

          case INSIDE_AFFIX_PATTERN:
            acceptAffixPatternOffset(cp, state, item);
            // Accept arbitrary bidi and whitespace (if lenient) in the middle of affixes.
            if (state.length == 0) {
              acceptBidi(cp, StateName.INSIDE_AFFIX_PATTERN, state, item);
              if (mode == ParseMode.LENIENT) {
                acceptWhitespace(cp, StateName.INSIDE_AFFIX_PATTERN, state, item);
              }
            }
            break;
        }
      }

      if (state.length == 0) {
        // No parse paths continue past this point. We have found the longest parsable string
        // from the input. Restore previous state without the offset and break.
        state.swapBack();
        break;
      }

      offset += Character.charCount(cp);
    }

    // Post-processing
    if (state.length == 0) {
      if (DEBUGGING) {
        System.out.println("No matches found");
        System.out.println("- - - - - - - - - -");
      }
      return null;
    } else {
      boolean hasEmptyAffix =
          state.affixHolders.contains(AffixHolder.EMPTY_POSITIVE)
              || state.affixHolders.contains(AffixHolder.EMPTY_NEGATIVE);

      // Loop through the candidates.  "continue" skips a candidate as invalid.
      StateItem best = null;
      outer:
      for (int i = 0; i < state.length; i++) {
        StateItem item = state.items[i];

        if (DEBUGGING) {
          System.out.println(":end " + item);
        }

        // Check that at least one digit was read.
        if (!item.hasNumber()) continue;

        if (mode == ParseMode.STRICT) {
          // Perform extra checks for strict mode.
          // We require that the affixes match.
          boolean sawPrefix = item.sawPrefix || (item.affix != null && item.affix.p.isEmpty());
          boolean sawSuffix = item.sawSuffix || (item.affix != null && item.affix.s.isEmpty());
          if (sawPrefix && sawSuffix) {
            // OK
          } else if (!sawPrefix && !sawSuffix && hasEmptyAffix) {
            // OK
          } else {
            // Has a prefix or suffix that doesn't match
            continue;
          }

          // Check that grouping sizes are valid.
          int grouping1 = properties.getGroupingSize();
          int grouping2 = properties.getSecondaryGroupingSize();
          grouping1 = grouping1 > 0 ? grouping1 : grouping2;
          grouping2 = grouping2 > 0 ? grouping2 : grouping1;
          int groupingMin = properties.getMinimumGroupingDigits();
          int numGroupingRegions = 16 - Long.numberOfLeadingZeros(item.groupingWidths) / 4;
          if (grouping1 < 0) {
            // OK (no grouping data available)
          } else if (numGroupingRegions <= 1) {
            // OK (no grouping digits)
          } else if ((item.groupingWidths & 0xf) != grouping1) {
            // First grouping size is invalid
            continue;
          } else if (numGroupingRegions == 2
              && groupingMin > 0
              && ((item.groupingWidths >>> 4) & 0xf) < groupingMin) {
            // String like "1,234" with groupingMin == 2
            continue;
          } else if (((item.groupingWidths >>> ((numGroupingRegions - 1) * 4)) & 0xf) > grouping2) {
            // String like "1234,567" where the highest grouping is too large
            continue;
          } else {
            for (int j = 1; j < numGroupingRegions - 1; j++) {
              if (((item.groupingWidths >>> (j * 4)) & 0xf) != grouping2) {
                // A grouping size somewhere in the middle is invalid
                continue outer;
              }
            }
          }
        }

        // Optionally require that a decimal point be present.
        if (properties.getDecimalPatternMatchRequired() && !item.sawDecimal) {
          continue;
        }

        // When parsing currencies, require that a currency symbol was found.
        if (parseCurrency && !item.sawCurrency) {
          continue;
        }

        // If we get here, then this candidate is acceptable.
        // Use the earliest candidate in the list, or the one with the highest score.
        if (best == null) {
          best = item;
        } else if (item.score > best.score) {
          best = item;
        }
      }

      if (DEBUGGING) {
        System.out.println("- - - - - - - - - -");
      }

      if (best != null) {
        ppos.setIndex(offset);
        return best;
      } else {
        ppos.setErrorIndex(offset);
        return null;
      }
    }
  }

  /**
   * If <code>cp</code> is whitespace (as determined by the unicode set {@link #UNISET_WHITESPACE}),
   * copies <code>item</code> to the new list in <code>state</code> and sets its state name to
   * <code>nextName</code>.
   *
   * @param cp The code point to check.
   * @param nextName The new state name if the check passes.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptWhitespace(
      int cp, StateName nextName, ParserState state, StateItem item) {
    if (UNISET_WHITESPACE.contains(cp)) {
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    }
  }

  /**
   * If <code>cp</code> is a bidi control character (as determined by the unicode set {@link
   * #UNISET_BIDI}), copies <code>item</code> to the new list in <code>state</code> and sets its
   * state name to <code>nextName</code>.
   *
   * @param cp The code point to check.
   * @param nextName The new state name if the check passes.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptBidi(int cp, StateName nextName, ParserState state, StateItem item) {
    if (UNISET_BIDI.contains(cp)) {
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    }
  }

  /**
   * If <code>cp</code> is a padding character (as determined by {@link ParserState#paddingCp}),
   * copies <code>item</code> to the new list in <code>state</code> and sets its state name to
   * <code>nextName</code>.
   *
   * @param cp The code point to check.
   * @param nextName The new state name if the check passes.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptPadding(int cp, StateName nextName, ParserState state, StateItem item) {
    CharSequence padding = state.properties.getPaddingString();
    if (padding == null || padding.length() == 0) return;
    int referenceCp = Character.codePointAt(padding, 0);
    if (cp == referenceCp) {
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    }
  }

  private static void acceptIntegerDigit(
      int cp, StateName nextName, ParserState state, StateItem item) {
    byte digit = acceptDigitHelper(cp, nextName, state, item);
    if (digit >= 0) {
      StateItem next = state.getItem(state.lastInsertedIndex());
      next.appendDigit(digit, false);
      if ((next.groupingWidths & 0xf) < 15) {
        next.groupingWidths++;
      }
    }
  }

  private static void acceptFractionDigit(
      int cp, StateName nextName, ParserState state, StateItem item) {
    byte digit = acceptDigitHelper(cp, nextName, state, item);
    if (digit >= 0) {
      state.getItem(state.lastInsertedIndex()).appendDigit(digit, true);
    }
  }

  private static void acceptExponentDigit(
      int cp, StateName nextName, ParserState state, StateItem item) {
    byte digit = acceptDigitHelper(cp, nextName, state, item);
    if (digit >= 0) {
      state.getItem(state.lastInsertedIndex()).appendExponent(digit);
    }
  }

  /**
   * If <code>cp</code> is a digit character (as determined by either {@link UCharacter#digit} or
   * {@link ParserState#digitCps}), copies <code>item</code> to the new list in <code>state</code>
   * and sets its state name to one determined by <code>type</code>. Also copies the digit into a
   * field in the new item determined by <code>type</code>.
   *
   * <p>This function guarantees that it will add no more than one {@link StateItem} to the {@link
   * ParserState}. This means that {@link ParserState#lastInsertedIndex()} can be called to access
   * the {@link StateItem} that was inserted.
   *
   * @param cp The code point to check.
   * @param nextName The state to set if a digit is accepted.
   * @param type The digit type, which determines the next state and the field into which to insert
   *     the digit.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   * @return The digit that was accepted, or -1 if no digit was accepted.
   */
  private static byte acceptDigitHelper(
      int cp, StateName nextName, ParserState state, StateItem item) {
    // Check the Unicode digit character property
    byte digit = (byte) UCharacter.digit(cp, 10);
    if (digit >= 0) {
      // Code point is a number
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    } else {
      // Check custom digits.
      if (digit < 0) {
        for (byte d = 0; d < 10; d++) {
          String digitString = state.symbols.getDigitStringsLocal()[d];
          long added = acceptString(cp, nextName, null, state, item, digitString, 0);
          if (added != 0) {
            digit = d;
            break;
          }
        }
      }
    }
    return digit;
  }

  /**
   * If <code>cp</code> is a sign (as determined by the unicode sets {@link #UNISET_PLUS} and {@link
   * #UNISET_MINUS}), copies <code>item</code> to the new list in <code>state</code>. Loops back to
   * the same state name.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptMinusOrPlusSign(
      int cp, StateName nextName, ParserState state, StateItem item, boolean exponent) {
    if (UNISET_PLUS.contains(cp)) {
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    } else if (UNISET_MINUS.contains(cp)) {
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
      if (exponent) {
        next.sawNegativeExponent = true;
      } else {
        next.sawNegative = true;
      }
    }
  }

  /**
   * If <code>cp</code> is a grouping separator (as determined by the unicode set {@link
   * #UNISET_GROUPING}), copies <code>item</code> to the new list in <code>state</code> and loops
   * back to the same state. Also accepts if <code>cp</code> is the locale-specific grouping
   * separator in {@link ParserState#groupingCp}, in which case the {@link
   * StateItem#usesLocaleSymbols} flag is also set.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptGrouping(
      int cp, StateName nextName, ParserState state, StateItem item) {
    // Do not accept mixed grouping separators in the same string.
    if (item.groupingCp == -1) {
      // First time seeing a grouping separator.
      SeparatorType cpType = SeparatorType.fromCp(cp, state.mode == ParseMode.STRICT);

      // Always accept if exactly the same as the locale symbol.
      // Otherwise, reject if UNKNOWN or in the same class as the decimal separator.
      if (cp != state.groupingCp1 && cp != state.groupingCp2) {
        if (cpType == SeparatorType.UNKNOWN) {
          return;
        }
        if (cpType == SeparatorType.COMMA_LIKE
            && (state.decimalType1 == SeparatorType.COMMA_LIKE
                || state.decimalType2 == SeparatorType.COMMA_LIKE)) {
          return;
        }
        if (cpType == SeparatorType.PERIOD_LIKE
            && (state.decimalType1 == SeparatorType.PERIOD_LIKE
                || state.decimalType2 == SeparatorType.PERIOD_LIKE)) {
          return;
        }
      }

      // A match was found.
      StateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
      next.groupingCp = cp;
      next.groupingWidths <<= 4;
    } else {
      // Have already seen a grouping separator.
      if (cp == item.groupingCp) {
        StateItem next = state.getNext().copyFrom(item);
        next.name = nextName;
        next.groupingWidths <<= 4;
      }
    }
  }

  /**
   * If <code>cp</code> is a decimal (as determined by the unicode set {@link #UNISET_DECIMAL}),
   * copies <code>item</code> to the new list in <code>state</code> and goes to {@link
   * StateName#AFTER_FRACTION_DIGIT}. Also accepts if <code>cp</code> is the locale-specific decimal
   * point in {@link ParserState#decimalCp}, in which case the {@link StateItem#usesLocaleSymbols}
   * flag is also set.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptDecimalPoint(
      int cp, StateName nextName, ParserState state, StateItem item) {
    if (cp == item.groupingCp) {
      // Don't accept a decimal point that is the same as the grouping separator
      return;
    }

    SeparatorType cpType = SeparatorType.fromCp(cp, state.mode == ParseMode.STRICT);

    // Always accept if exactly the same as the locale symbol.
    // Otherwise, reject if UNKNOWN, OTHER, the same class as the decimal separator.
    if (cp != state.decimalCp1 && cp != state.decimalCp2) {
      if (cpType == SeparatorType.UNKNOWN || cpType == SeparatorType.OTHER_GROUPING) {
        return;
      }
      if (cpType == SeparatorType.COMMA_LIKE
          && (state.groupingType1 == SeparatorType.COMMA_LIKE
              || state.groupingType2 == SeparatorType.COMMA_LIKE)) {
        return;
      }
      if (cpType == SeparatorType.PERIOD_LIKE
          && (state.groupingType1 == SeparatorType.PERIOD_LIKE
              || state.groupingType2 == SeparatorType.PERIOD_LIKE)) {
        return;
      }
    }

    // A match was found.
    StateItem next = state.getNext().copyFrom(item);
    next.name = nextName;
  }

  private static void acceptNan(int cp, StateName nextName, ParserState state, StateItem item) {
    CharSequence nan = state.symbols.getNaN();
    long added = acceptString(cp, nextName, null, state, item, nan, 0);

    // Set state in the items that were added by the function call
    for (int i = 0; (1L << i) <= added; i++) {
      if (((1L << i) & added) != 0) {
        state.getItem(i).sawNaN = true;
      }
    }
  }

  private static void acceptInfinity(
      int cp, StateName nextName, ParserState state, StateItem item) {
    CharSequence inf = state.symbols.getInfinity();
    long added = acceptString(cp, nextName, null, state, item, inf, 0);

    // Set state in the items that were added by the function call
    for (int i = 0; (1L << i) <= added; i++) {
      if (((1L << i) & added) != 0) {
        state.getItem(i).sawInfinity = true;
      }
    }
  }

  private static void acceptExponentSeparator(
      int cp, StateName nextName, ParserState state, StateItem item) {
    CharSequence exp = state.symbols.getExponentSeparator();
    acceptString(cp, nextName, null, state, item, exp, 0);
  }

  private static void acceptPrefix(int cp, StateName nextName, ParserState state, StateItem item) {
    for (AffixHolder holder : state.affixHolders) {
      acceptAffixHolder(cp, nextName, state, item, holder, true);
    }
  }

  private static void acceptSuffix(int cp, StateName nextName, ParserState state, StateItem item) {
    if (item.affix != null) {
      acceptAffixHolder(cp, nextName, state, item, item.affix, false);
    } else {
      for (AffixHolder holder : state.affixHolders) {
        acceptAffixHolder(cp, nextName, state, item, holder, false);
      }
    }
  }

  private static void acceptAffixHolder(
      int cp,
      StateName nextName,
      ParserState state,
      StateItem item,
      AffixHolder holder,
      boolean prefix) {
    if (holder == null) return;
    String str = prefix ? holder.p : holder.s;
    if (holder.strings) {
      long added = acceptString(cp, nextName, null, state, item, str, 0);
      // At most one item can be added upon consuming a string.
      if (added != 0) {
        int i = state.lastInsertedIndex();
        // The following four lines are duplicated above; not enough for their own function.
        state.getItem(i).affix = holder;
        if (prefix) state.getItem(i).sawPrefix = true;
        else state.getItem(i).sawSuffix = true;
        if (holder.negative) state.getItem(i).sawNegative = true;
      }
    } else {
      long added = acceptAffixPattern(cp, nextName, state, item, str, 0);
      // Multiple items can be added upon consuming an affix pattern.
      for (int i = 0; (1L << i) <= added; i++) {
        if (((1L << i) & added) != 0) {
          // The following four lines are duplicated above; not enough for their own function.
          state.getItem(i).affix = holder;
          if (prefix) state.getItem(i).sawPrefix = true;
          else state.getItem(i).sawSuffix = true;
          if (holder.negative) state.getItem(i).sawNegative = true;
        }
      }
    }
  }

  private static void acceptStringOffset(int cp, ParserState state, StateItem item) {
    acceptString(
        cp, item.returnTo1, item.returnTo2, state, item, item.currentString, item.currentOffset);
  }

  /**
   * Accepts a code point if the code point is compatible with the string at the given offset.
   *
   * <p>This method will add no more than one {@link StateItem} to the {@link ParserState}, which
   * means that at most one bit will be set in the return value, corresponding to the return value
   * of {@link ParserState#lastInsertedIndex()}.
   *
   * @param cp The current code point, which will be checked for a match to the string.
   * @param returnTo1 The state to return to after reaching the end of the string.
   * @param returnTo2 The state to save in <code>returnTo1</code> after reaching the end of the
   *     string. Set to null if returning to the main state loop.
   * @param state The current {@link ParserState}
   * @param item The current {@link StateItem}
   * @param str The string against which to check for a match.
   * @param offset The number of chars into the string. Initial value should be 0.
   * @return A bitmask where the bits correspond to the items that were added. Set to 0L if no items
   *     were added.
   */
  private static long acceptString(
      int cp,
      StateName returnTo1,
      StateName returnTo2,
      ParserState state,
      StateItem item,
      CharSequence str,
      int offset) {
    if (str == null || str.length() == 0) return 0L;
    int referenceCp = Character.codePointAt(str, offset);
    int count = Character.charCount(referenceCp);
    boolean equals = codePointEquals(cp, referenceCp, state);

    // Optionally skip over BiDi characters in the string.
    // If in lenient mode, optionally skip over whitespace characters in the string.
    // This can be greedy because no information is being lost.
    while (!equals
        && offset + count < str.length()
        && (UNISET_BIDI.contains(referenceCp)
            || (state.mode == ParseMode.LENIENT && UNISET_WHITESPACE.contains(referenceCp)))) {
      offset += count;
      referenceCp = Character.codePointAt(str, offset);
      count = Character.charCount(referenceCp);
      equals = codePointEquals(cp, referenceCp, state);
    }

    if (equals) {
      // Matches first code point of the string
      StateItem next = state.getNext().copyFrom(item);

      // Check if the string has any more interesting characters.
      // It is okay to ignore bidi/whitespace because they will be accepted in the main loop.
      boolean hasAdditionalMaterial = false;
      for (int j = offset + count; j < str.length(); ) {
        int futureCp = Character.codePointAt(str, 0);
        if (!UNISET_BIDI.contains(futureCp)
            && (state.mode != ParseMode.LENIENT || !UNISET_WHITESPACE.contains(futureCp))) {
          hasAdditionalMaterial = true;
          break;
        }
        j += Character.charCount(futureCp);
      }

      if (hasAdditionalMaterial) {
        // String has more code points.
        next.name = StateName.INSIDE_STRING;
        next.returnTo1 = returnTo1;
        next.returnTo2 = returnTo2;
        next.currentString = str;
        next.currentOffset = offset + count;
      } else {
        // We've reached the end of the string.
        next.name = returnTo1;
        next.returnTo1 = returnTo2;
        next.returnTo2 = null;
      }
      return 1L << state.lastInsertedIndex();
    }
    return 0L;
  }

  private static void acceptAffixPatternOffset(int cp, ParserState state, StateItem item) {
    acceptAffixPattern(
        cp, item.returnTo1, state, item, item.currentAffixPattern, item.currentStepwiseParserTag);
  }

  /**
   * Accepts a code point if the code point is compatible with the affix pattern at the offset
   * encoded in the tag argument.
   *
   * @param cp The current code point, which will be checked for a match to the string.
   * @param returnTo The state to return to after reaching the end of the string.
   * @param state The current {@link ParserState}
   * @param item The current {@link StateItem}
   * @param str The string containing the affix pattern.
   * @param tag The current state of the stepwise parser. Initial value should be 0L.
   * @return A bitmask where the bits correspond to the items that were added. Set to 0L if no items
   *     were added.
   */
  private static long acceptAffixPattern(
      int cp, StateName returnTo, ParserState state, StateItem item, CharSequence str, long tag) {
    if (str == null || str.length() == 0) return 0L;
    tag = AffixPatternUtils.nextToken(tag, str);
    int typeOrCp = AffixPatternUtils.getTypeOrCp(tag);
    boolean hasNext = AffixPatternUtils.hasNext(tag, str);

    // Optionally skip over BiDi characters in the string.
    // If in lenient mode, optionally skip over whitespace characters in the string.
    // This can be greedy because no information is being lost.
    while (typeOrCp >= 0
        && cp != typeOrCp
        && hasNext
        && (UNISET_BIDI.contains(typeOrCp)
            || (state.mode == ParseMode.LENIENT && UNISET_WHITESPACE.contains(typeOrCp)))) {
      tag = AffixPatternUtils.nextToken(tag, str);
      typeOrCp = AffixPatternUtils.getTypeOrCp(tag);
      hasNext = AffixPatternUtils.hasNext(tag, str);
    }

    // Convert from the returned tag to a code point, string, or currency to check
    int resolvedCp = -1;
    CharSequence resolvedStr = null;
    boolean resolvedCurrency = false;
    if (typeOrCp < 0) {
      // Symbol
      switch (typeOrCp) {
        case AffixPatternUtils.TYPE_MINUS_SIGN:
          resolvedStr = state.symbols.getMinusSignString();
          break;
        case AffixPatternUtils.TYPE_PLUS_SIGN:
          resolvedStr = state.symbols.getPlusSignString();
          break;
        case AffixPatternUtils.TYPE_PERCENT:
          resolvedStr = state.symbols.getPercentString();
          break;
        case AffixPatternUtils.TYPE_PERMILLE:
          resolvedStr = state.symbols.getPerMillString();
          break;
        case AffixPatternUtils.TYPE_CURRENCY_SINGLE:
        case AffixPatternUtils.TYPE_CURRENCY_DOUBLE:
        case AffixPatternUtils.TYPE_CURRENCY_TRIPLE:
          resolvedCurrency = true;
          break;
        default:
          throw new AssertionError();
      }
    } else {
      resolvedCp = typeOrCp;
    }

    long addedNormal = 0L;
    long addedCurrencyNeeded = 0L;
    if (resolvedCp >= 0) {
      // Code point
      if (!codePointEquals(cp, resolvedCp, state)) return 0L;
      StateItem next = state.getNext().copyFrom(item);
      if (hasNext) {
        // Additional tokens in affix string.
        next.name = StateName.INSIDE_AFFIX_PATTERN;
        next.returnTo1 = returnTo;
      } else {
        // Reached last token in affix string.
        next.name = returnTo;
        next.returnTo1 = null;
      }
      addedNormal |= 1L << state.lastInsertedIndex();
    }
    if (resolvedStr != null) {
      // String symbol
      if (hasNext) {
        addedNormal |=
            acceptString(cp, StateName.INSIDE_AFFIX_PATTERN, returnTo, state, item, resolvedStr, 0);
      } else {
        addedNormal |= acceptString(cp, returnTo, null, state, item, resolvedStr, 0);
      }
    }
    if (resolvedCurrency) {
      // Currency
      if (item.sawCurrency) return 0L;
      CharSequence str1 = state.symbols.getCurrencySymbol();
      CharSequence str2 = state.symbols.getInternationalCurrencySymbol();
      ULocale uloc = state.symbols.getULocale();
      TextTrieMap<Currency.CurrencyStringInfo>.ParseState trie1 =
          Currency.openParseState(uloc, cp, Currency.LONG_NAME);
      TextTrieMap<Currency.CurrencyStringInfo>.ParseState trie2 =
          Currency.openParseState(uloc, cp, Currency.SYMBOL_NAME);
      if (hasNext) {
        // Accept from local currency information
        addedCurrencyNeeded |=
            acceptString(cp, StateName.INSIDE_AFFIX_PATTERN, returnTo, state, item, str1, 0);
        addedCurrencyNeeded |=
            acceptString(cp, StateName.INSIDE_AFFIX_PATTERN, returnTo, state, item, str2, 0);
        // Accept from CLDR currency data
        addedNormal |=
            acceptCurrencyHelper(cp, StateName.INSIDE_AFFIX_PATTERN, returnTo, state, item, trie1);
        addedNormal |=
            acceptCurrencyHelper(cp, StateName.INSIDE_AFFIX_PATTERN, returnTo, state, item, trie2);
      } else {
        // Accept from local currency information
        addedCurrencyNeeded |= acceptString(cp, returnTo, null, state, item, str1, 0);
        addedCurrencyNeeded |= acceptString(cp, returnTo, null, state, item, str2, 0);
        // Accept from CLDR currency data
        addedNormal |= acceptCurrencyHelper(cp, returnTo, null, state, item, trie1);
        addedNormal |= acceptCurrencyHelper(cp, returnTo, null, state, item, trie2);
      }
    }

    // Set state in the items that were added by the function calls
    long added = addedNormal | addedCurrencyNeeded;
    for (int i = 0; (1L << i) <= added; i++) {
      if (((1L << i) & addedNormal) != 0) {
        state.getItem(i).currentAffixPattern = str;
        state.getItem(i).currentStepwiseParserTag = tag;
      }
      if (((1L << i) & addedCurrencyNeeded) != 0) {
        // Save the currency from symbols.
        state.getItem(i).sawCurrency = true;
        state.getItem(i).isoCode = state.symbols.getCurrency().getCurrencyCode();
      }
    }
    return added;
  }

  /**
   * This method can add up to four items to the new list in <code>state</code>.
   *
   * <p>If <code>cp</code> is equal to any known ISO code or long name, copies <code>item</code> to
   * the new list in <code>state</code> and sets its ISO code to the corresponding currency.
   *
   * <p>If <code>cp</code> is the first code point of any ISO code or long name having more them one
   * code point in length, copies <code>item</code> to the new list in <code>state</code> along with
   * an instance of {@link TextTrieMap.ParseState} for tracking the following code points.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptCurrency(
      int cp, StateName nextName, ParserState state, StateItem item) {
    if (item.sawCurrency) return;
    ULocale uloc = state.symbols.getULocale();
    TextTrieMap<Currency.CurrencyStringInfo>.ParseState trie1 =
        Currency.openParseState(uloc, cp, Currency.LONG_NAME);
    TextTrieMap<Currency.CurrencyStringInfo>.ParseState trie2 =
        Currency.openParseState(uloc, cp, Currency.SYMBOL_NAME);
    acceptCurrencyHelper(cp, nextName, null, state, item, trie1);
    acceptCurrencyHelper(cp, nextName, null, state, item, trie2);
  }

  /**
   * If <code>cp</code> is the next code point of any currency, copies <code>item</code> to the new
   * list in <code>state</code> along with an instance of {@link TextTrieMap.ParseState} for
   * tracking the following code points.
   *
   * <p>This method should only be called in a state following {@link #acceptCurrency}.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptCurrencyOffset(int cp, ParserState state, StateItem item) {
    acceptCurrencyHelper(cp, item.returnTo1, item.returnTo2, state, item, item.currentTrieState);
  }

  private static long acceptCurrencyHelper(
      int cp,
      StateName returnTo1,
      StateName returnTo2,
      ParserState state,
      StateItem item,
      TextTrieMap<Currency.CurrencyStringInfo>.ParseState trieState) {
    if (trieState == null) return 0L;
    trieState.accept(cp);
    long added = 0L;
    if (trieState.getCurrentMatches() != null) {
      // Match on first code point
      // TODO: What should happen with multiple currency matches?
      StateItem next = state.getNext().copyFrom(item);
      next.name = returnTo1;
      next.returnTo1 = returnTo2;
      next.returnTo2 = null;
      next.sawCurrency = true;
      next.isoCode = trieState.getCurrentMatches().next().getISOCode();
      added |= 1L << state.lastInsertedIndex();
    }
    if (!trieState.atEnd()) {
      // Prepare for matches on future code points
      StateItem next = state.getNext().copyFrom(item);
      next.name = StateName.INSIDE_CURRENCY;
      next.returnTo1 = returnTo1;
      next.returnTo2 = returnTo2;
      next.currentTrieState = trieState;
      added |= 1L << state.lastInsertedIndex();
    }
    return added;
  }

  /**
   * Checks whether the two given code points are equal after applying case mapping as requested in
   * the ParserState.
   *
   * @see #acceptString
   * @see #acceptAffixPattern
   */
  private static boolean codePointEquals(int cp1, int cp2, ParserState state) {
    if (!state.caseSensitive) {
      cp1 = UCharacter.foldCase(cp1, true);
      cp2 = UCharacter.foldCase(cp2, true);
    }
    return cp1 == cp2;
  }
}
