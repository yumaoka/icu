// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Arrays;

import com.ibm.icu.impl.TextTrieMap;
import com.ibm.icu.impl.number.Parse.ParseMode;
import com.ibm.icu.impl.number.formatters.CurrencyFormat;
import com.ibm.icu.impl.number.formatters.PaddingFormat;
import com.ibm.icu.impl.number.formatters.PositiveNegativeAffixFormat;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.DecimalFormatSymbols;
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
  public enum ParseMode {
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
          CurrencyFormat.ICurrencyProperties {

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

    boolean DEFAULT_PARSE_CURRENCY = false;

    /** @see #setParseCurrency */
    public boolean getParseCurrency();

    /**
     * Whether to parse currency codes and currency names in the string.
     *
     * <p>Due to the large number of possible currencies, enabling this option may impact the
     * runtime of the parse operation.
     *
     * @param parseCurrency true to parse arbitrary currency codes and currency names; false to
     *     disable. (Default is false)
     * @return The property bag, for chaining.
     */
    public IProperties setParseCurrency(boolean parseCurrency);
  }

  /**
   * @see #parse(String, ParsePosition, ParseMode, boolean, boolean, IProperties,
   *     DecimalFormatSymbols)
   */
  private enum StateName {
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
  }

  /** @see #acceptDigit */
  private enum DigitType {
    INTEGER,
    FRACTION,
    EXPONENT
  }

  /** @see #acceptString */
  private enum StringType {
    POS_PREFIX,
    NEG_PREFIX,
    POS_SUFFIX,
    NEG_SUFFIX,
    EXPONENT_SEPARATOR
  }

  /** @see #acceptString */
  private enum AffixStatus {
    NOT_SEEN,
    SAW_PREFIX,
    SAW_SUFFIX
  }

  // TODO: Re-generate these sets from the database. They probably haven't been updated in a while.
  private static final UnicodeSet UNISET_PERIOD_LIKE =
      new UnicodeSet("[.\u2024\u3002\uFE12\uFE52\uFF0E\uFF61]").freeze();
  private static final UnicodeSet UNISET_STRICT_PERIOD_LIKE =
      new UnicodeSet("[.\u2024\uFE52\uFF0E\uFF61]").freeze();
  private static final UnicodeSet UNISET_COMMA_LIKE =
      new UnicodeSet("[,\u060C\u066B\u3001\uFE10\uFE11\uFE50\uFE51\uFF0C\uFF64]").freeze();
  private static final UnicodeSet UNISET_STRICT_COMMA_LIKE =
      new UnicodeSet("[,\\u066B\\uFE10\\uFE50\\uFF0C]").freeze();
  private static final UnicodeSet UNISET_OTHER_GROUPING_SEPARATORS =
      new UnicodeSet("[\\ '\u00A0\u066C\u2000-\u200A\u2018\u2019\u202F\u205F\u3000\uFF07]")
          .freeze();

  private enum SeparatorType {
    COMMA_LIKE,
    PERIOD_LIKE,
    OTHER_GROUPING,
    UNKNOWN;

    static SeparatorType fromCp(int cp, boolean strict) {
      UnicodeSet commaLike = strict ? UNISET_COMMA_LIKE : UNISET_STRICT_COMMA_LIKE;
      if (commaLike.contains(cp)) return COMMA_LIKE;
      UnicodeSet periodLike = strict ? UNISET_PERIOD_LIKE : UNISET_STRICT_PERIOD_LIKE;
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
  private static class ParserStateItem {
    StateName name;
    int score;
    byte[] digits = new byte[100];
    int scale;
    int numDigits;
    int offset;
    int groupingCp;
    int exponent;
    boolean sawNegative;
    boolean sawNegativeExponent;
    boolean sawDecimal;
    AffixStatus positiveAffixStatus;
    AffixStatus negativeAffixStatus;
    public StringType stringType;
    public String isoCode;
    public StateName returnTo;
    public TextTrieMap<CurrencyStringInfo>.ParseState trieState;

    /**
     * Clears the instance so that it can be re-used.
     *
     * @return Myself, for chaining.
     */
    ParserStateItem clear() {
      name = StateName.BEFORE_PREFIX;
      score = 0;
      Arrays.fill(digits, (byte) 0);
      scale = 0;
      numDigits = 0;
      offset = 0;
      groupingCp = -1;
      exponent = 0;
      sawNegative = false;
      sawNegativeExponent = false;
      sawDecimal = false;
      positiveAffixStatus = AffixStatus.NOT_SEEN;
      negativeAffixStatus = AffixStatus.NOT_SEEN;
      stringType = null;
      isoCode = null;
      returnTo = null;
      trieState = null;
      return this;
    }

    /**
     * Sets the internal value of this instance equal to another instance.
     *
     * @param other The instance to copy from.
     * @return Myself, for chaining.
     */
    ParserStateItem copyFrom(ParserStateItem other) {
      name = other.name;
      score = other.score;
      // Using System.arraycopy() results in overall parsing runtime ~5% faster than a for loop
      // in benchmarks on an x86-64 with longs ranging between 1 and 1e12.
      System.arraycopy(other.digits, 0, digits, 0, digits.length);
      scale = other.scale;
      numDigits = other.numDigits;
      offset = other.offset;
      groupingCp = other.groupingCp;
      exponent = other.exponent;
      sawNegative = other.sawNegative;
      sawNegativeExponent = other.sawNegativeExponent;
      sawDecimal = other.sawDecimal;
      positiveAffixStatus = other.positiveAffixStatus;
      negativeAffixStatus = other.negativeAffixStatus;
      stringType = other.stringType;
      isoCode = other.isoCode;
      returnTo = other.returnTo;
      trieState = other.trieState;
      return this;
    }

    /**
     * Adds a digit to the internal representation of this instance.
     *
     * @param digit The digit that was read from the string.
     * @param type The type of digit, which determines how it is interpreted.
     * @throws ParseException If this instance can't fit any more digits.
     */
    void appendDigit(int digit, DigitType type) throws ParseException {
      if (type == DigitType.EXPONENT) {
        exponent = exponent * 10 + digit;
      } else {
        if (numDigits >= digits.length) {
          // TODO: Should we fail silently here instead of throwing?
          throw new ParseException("Too many digits", -1);
        }
        digits[numDigits] = (byte) digit;
        numDigits++;
        if (type == DigitType.FRACTION) scale--;
      }
    }

    /**
     * Converts the internal digits from this instance into a Number, preferring a Long, then a
     * BigInteger, then a BigDecimal.
     *
     * @return The Number. Never null.
     */
    Number toNumber() {
      if (scale == 0 && exponent == 0 && numDigits <= 18) {
        long result = 0;
        for (int i = 0; i < numDigits; i++) {
          result = result * 10 + digits[i];
        }
        if (sawNegative) {
          result *= -1;
        }
        return result;
      } else if (scale == 0 && exponent == 0) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < numDigits; i++) {
          result = result.multiply(BigInteger.TEN).add(BigInteger.valueOf(digits[i]));
        }
        if (sawNegative) {
          result = result.negate();
        }
        return result;
      } else {
        BigDecimal result = BigDecimal.ZERO;
        for (int i = 0; i < numDigits; i++) {
          result = result.scaleByPowerOfTen(1).add(BigDecimal.valueOf(digits[i]));
        }
        result = result.scaleByPowerOfTen(scale);
        result = result.scaleByPowerOfTen((sawNegativeExponent ? -1 : 1) * exponent);
        if (sawNegative) {
          result = result.negate();
        }
        return result;
      }
    }

    /**
     * Converts the internal digits to a number, and also associates the number with the parsed
     * currency.
     *
     * @return The CurrencyAmount. Never null.
     */
    public CurrencyAmount toCurrencyAmount(Currency fallback) {
      Number number = toNumber();
      // If no currency was found, use the fallback
      Currency currency = (isoCode == null) ? fallback : Currency.getInstance(isoCode);
      return new CurrencyAmount(number, currency);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("<ParserStateItem ");
      sb.append(name.name());
      sb.append(" ");
      for (int i = 0; i < numDigits; i++) {
        sb.append(digits[i]);
      }
      sb.append(" scale:");
      sb.append(scale);
      sb.append(" offset:");
      sb.append(offset);
      sb.append(" grouping:");
      sb.append(groupingCp == -1 ? new char[] {'?'} : Character.toChars(groupingCp));
      sb.append(" exponent:");
      sb.append(exponent);
      sb.append(" seen:");
      sb.append(sawNegative ? 1 : 0);
      sb.append(sawNegativeExponent ? 1 : 0);
      sb.append(sawDecimal ? 1 : 0);
      sb.append(" affixStatus:");
      sb.append(positiveAffixStatus.ordinal());
      sb.append(negativeAffixStatus.ordinal());
      sb.append(" score:");
      sb.append(score);
      sb.append(" currency:");
      sb.append(isoCode);
      sb.append(">");
      return sb.toString();
    }
  }

  /**
   * Holds an ordered list of {@link ParserStateItem} and other metadata about the string to be
   * parsed. There are two internal arrays of {@link ParserStateItem}, which are swapped back and
   * forth in order to avoid object creations. The items in one array can be populated at the same
   * time that items in the other array are being read from.
   */
  private static class ParserState {

    ParserStateItem[] items = new ParserStateItem[16];
    ParserStateItem[] prevItems = new ParserStateItem[16];
    int length;
    int prevLength;

    ULocale uLocale;
    int paddingCp;
    int groupingCp1;
    int groupingCp2;
    int decimalCp1;
    int decimalCp2;
    SeparatorType groupingType1;
    SeparatorType groupingType2;
    SeparatorType decimalType1;
    SeparatorType decimalType2;
    CharSequence exponentSeparator;
    CharSequence pp;
    CharSequence np;
    CharSequence ps;
    CharSequence ns;
    int[] digitCps = new int[10];

    ParseMode mode;
    boolean integerOnly;
    boolean ignoreExponent;
    boolean parseCurrency;

    ParserState() {
      for (int i = 0; i < items.length; i++) {
        items[i] = new ParserStateItem();
        prevItems[i] = new ParserStateItem();
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
      uLocale = null;
      paddingCp = -1;
      groupingCp1 = -1;
      groupingCp2 = -1;
      decimalCp1 = -1;
      decimalCp2 = -1;
      exponentSeparator = null;
      pp = null;
      np = null;
      ps = null;
      ns = null;
      Arrays.fill(digitCps, -1);
      mode = null;
      integerOnly = false;
      ignoreExponent = false;
      parseCurrency = false;
      return this;
    }

    /**
     * Swaps the internal arrays of {@link ParserStateItem}. Sets the length of the primary list to
     * zero, so that it can be appended to.
     */
    void swap() {
      ParserStateItem[] temp = prevItems;
      prevItems = items;
      items = temp;
      prevLength = length;
      length = 0;
    }

    /**
     * Swaps the internal arrays of {@link ParserStateItem}. Sets the length of the primary list to
     * the length of the previous list, so that it can be read from.
     */
    void swapBack() {
      ParserStateItem[] temp = prevItems;
      prevItems = items;
      items = temp;
      length = prevLength;
      prevLength = 0;
    }

    /**
     * Gets the next available {@link ParserStateItem} from the primary list for writing. This
     * method should be thought of like a list append method, except that there are no object
     * creations taking place.
     *
     * <p>It is the caller's responsibility to call either {@link ParserStateItem#clear} or {@link
     * ParserStateItem#copyFrom} on the returned object.
     *
     * @return A dirty {@link ParserStateItem}.
     */
    ParserStateItem getNext() {
      if (length >= items.length) {
        // TODO: What to do here? Expand the array?
        // This case is rare and would happen only with specially designed input.
        // For now, just overwrite the last entry.
        length = items.length - 1;
      }
      ParserStateItem item = items[length];
      length++;
      return item;
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

  // TODO: Include characters like U+200D ZERO-WIDTH JOINER in the whitespace set?
  private static final UnicodeSet UNISET_WHITESPACE = new UnicodeSet("[[:whitespace:]]").freeze();

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

  public static Number parse(String input, IProperties properties, DecimalFormatSymbols symbols)
      throws ParseException {
    ParsePosition ppos = threadLocalParsePosition.get();
    ppos.setIndex(0);
    return parse(input, ppos, properties, symbols);
  }

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
   * @throws ParseException If an error is encountered during parsing.
   */
  public static Number parse(
      CharSequence input, ParsePosition ppos, IProperties properties, DecimalFormatSymbols symbols)
      throws ParseException {
    ParserStateItem best = _parse(input, ppos, false, properties, symbols);
    return (best == null) ? null : best.toNumber();
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
    ParserStateItem best = _parse(input, ppos, true, properties, symbols);

    // TODO: Is this the correct way to select a fallback currency?
    Currency fallback = properties.getCurrency();
    if (fallback == null) {
      fallback = symbols.getCurrency();
    }
    if (fallback == null) {
      fallback = Currency.getInstance("XXX");
    }

    return (best == null) ? null : best.toCurrencyAmount(fallback);
  }

  private static ParserStateItem _parse(
      CharSequence input,
      ParsePosition ppos,
      boolean parseCurrency,
      IProperties properties,
      DecimalFormatSymbols symbols)
      throws ParseException {

    if (symbols == null) throw new IllegalArgumentException("symbols must not be null");

    // Set up the initial state
    ParserState state = threadLocalParseState.get().clear();
    state.uLocale = symbols.getULocale();
    state.mode = properties.getParseMode();
    // TODO: Many of these calls use only the first codepoint from a string that could contain
    // multiple codepoints.  Is that OK?
    if (properties.getPaddingString() != null
        && !UNISET_WHITESPACE.contains(properties.getPaddingString())) {
      // TODO: Should we accept treating whitespace like padding here?
      state.paddingCp = Character.codePointAt(properties.getPaddingString(), 0);
    }
    state.groupingCp1 = Character.codePointAt(symbols.getGroupingSeparatorString(), 0);
    state.groupingCp2 = Character.codePointAt(symbols.getMonetaryGroupingSeparatorString(), 0);
    state.decimalCp1 = Character.codePointAt(symbols.getDecimalSeparatorString(), 0);
    state.decimalCp2 = Character.codePointAt(symbols.getMonetaryDecimalSeparatorString(), 0);
    state.groupingType1 = SeparatorType.fromCp(state.groupingCp1, state.mode == ParseMode.STRICT);
    state.groupingType2 = SeparatorType.fromCp(state.groupingCp2, state.mode == ParseMode.STRICT);
    state.decimalType1 = SeparatorType.fromCp(state.decimalCp1, state.mode == ParseMode.STRICT);
    state.decimalType2 = SeparatorType.fromCp(state.decimalCp2, state.mode == ParseMode.STRICT);
    PNAffixGenerator.Result affixResult =
        PNAffixGenerator.getThreadLocalInstance().getModifiers(symbols, properties);
    state.pp = affixResult.positive.getPrefix();
    state.np = affixResult.negative.getPrefix();
    state.ps = affixResult.positive.getSuffix();
    state.ns = affixResult.negative.getSuffix();
    state.exponentSeparator = symbols.getExponentSeparator();
    @SuppressWarnings("deprecation")
    String[] digitStrings = symbols.getDigitStringsLocal();
    for (int i = 0; i < 10; i++) {
      state.digitCps[i] = Character.codePointAt(digitStrings[i], 0);
    }
    state.integerOnly = properties.getParseIntegerOnly();
    state.ignoreExponent = properties.getParseIgnoreExponent();
    state.parseCurrency = parseCurrency;
    ParserStateItem initialHolder = state.getNext().clear();
    initialHolder.name = StateName.BEFORE_PREFIX;

    // Start walking through the string, one codepoint at a time. Backtracking is not allowed. This
    // is to enforce linear runtime and prevent edge cases that could result in an infinite loop.
    int offset = ppos.getIndex();
    for (; offset < input.length(); ) {
      int cp = Character.codePointAt(input, offset);
      state.swap();
      for (int i = 0; i < state.prevLength; i++) {
        ParserStateItem item = state.prevItems[i];
        // NOTE: Uncomment the following line to view the step-by-step parse process.
        // System.out.println(":" + offset + " " + item);
        switch (item.name) {
          case BEFORE_PREFIX:
            // Beginning of string
            acceptWhitespace(cp, StateName.BEFORE_PREFIX, state, item);
            acceptPadding(cp, StateName.BEFORE_PREFIX, state, item);
            acceptString(cp, StringType.POS_PREFIX, state, item);
            acceptString(cp, StringType.NEG_PREFIX, state, item);
            acceptMinusSign(cp, DigitType.INTEGER, state, item);
            acceptDigit(cp, DigitType.INTEGER, state, item);
            if (!state.integerOnly) {
              acceptDecimalPoint(cp, state, item);
            }
            if (state.mode == ParseMode.LENIENT) {
              // Accept a plus sign in lenient mode even if it's not in the prefix string
              acceptPlusSign(cp, state, item);
            }
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_PREFIX, state, item);
            }
            break;

          case AFTER_PREFIX:
            // Prefix is consumed
            if (state.mode == ParseMode.LENIENT) {
              // Arbitrary whitespace in the middle of the string is not allowed in strict mode.
              acceptWhitespace(cp, StateName.AFTER_PREFIX, state, item);
            }
            acceptPadding(cp, StateName.AFTER_PREFIX, state, item);
            acceptDigit(cp, DigitType.INTEGER, state, item);
            if (!state.integerOnly) {
              acceptDecimalPoint(cp, state, item);
            }
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.AFTER_PREFIX, state, item);
            }
            break;

          case AFTER_INTEGER_DIGIT:
            // Previous character was an integer digit (or grouping/whitespace)
            acceptGrouping(cp, state, item);
            acceptDigit(cp, DigitType.INTEGER, state, item);
            if (!state.integerOnly) {
              acceptDecimalPoint(cp, state, item);
            }
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
            }
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case AFTER_FRACTION_DIGIT:
            // We encountered a decimal point
            acceptDigit(cp, DigitType.FRACTION, state, item);
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
            }
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case AFTER_EXPONENT_SEPARATOR:
            acceptPlusSign(cp, state, item);
            acceptMinusSign(cp, DigitType.EXPONENT, state, item);
            acceptDigit(cp, DigitType.EXPONENT, state, item);
            break;

          case AFTER_EXPONENT_DIGIT:
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            acceptDigit(cp, DigitType.EXPONENT, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            break;

          case BEFORE_SUFFIX:
            // Accept whitespace, suffixes, and exponent separators
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
            }
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            break;

          case BEFORE_SUFFIX_SEEN_EXPONENT:
            // Accept whitespace and suffixes but not exponent separators
            // Note: The only way to get here is to read whitespace or padding after an exponent
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            break;

          case AFTER_SUFFIX:
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.AFTER_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.AFTER_SUFFIX, state, item);
            if (state.parseCurrency) {
              acceptCurrency(cp, StateName.AFTER_SUFFIX, state, item);
            }
            // Otherwise, do not accept any more characters.
            break;

          case INSIDE_CURRENCY:
            // Already read the first code point of a currency
            acceptCurrencyOffset(cp, state, item);
            break;

          case INSIDE_STRING:
            // Already read the first code point of a string
            acceptStringOffset(cp, state, item);
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
      return null;
    } else {
      // Loop through the candidates.  "continue" skips a candidate as invalid.
      ParserStateItem best = null;
      for (int i = 0; i < state.length; i++) {
        ParserStateItem item = state.items[i];

        // Check that at least one digit was read.
        if (item.numDigits == 0) continue;

        if (state.mode == ParseMode.STRICT) {
          // Perform extra checks for strict mode.  We require that the affixes match.
          // Accept if any of the following checks are true.
          if ((item.positiveAffixStatus == AffixStatus.SAW_SUFFIX)
              || (item.negativeAffixStatus == AffixStatus.SAW_SUFFIX)
              || (item.positiveAffixStatus == AffixStatus.SAW_PREFIX && charSeqEmpty(state.ps))
              || (item.negativeAffixStatus == AffixStatus.SAW_PREFIX && charSeqEmpty(state.ns))
              || (item.positiveAffixStatus == AffixStatus.NOT_SEEN
                  && item.negativeAffixStatus == AffixStatus.NOT_SEEN
                  && (charSeqEmpty(state.pp) || charSeqEmpty(state.np)))) {
            // Affixes match
          } else {
            // Affixes don't match
            continue;
          }
        }

        // Optionally require that a decimal point be present.
        if (properties.getDecimalPatternMatchRequired() && !item.sawDecimal) {
          continue;
        }

        // If we get here, then this candidate is acceptable.
        // Use the earliest candidate in the list, or the first one that uses locale symbols for
        // decimal point and/or grouping separator.
        if (best == null) {
          best = item;
        } else if (item.score > best.score) {
          best = item;
        }
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
      int cp, StateName nextName, ParserState state, ParserStateItem item) {
    if (UNISET_WHITESPACE.contains(cp)) {
      ParserStateItem next = state.getNext().copyFrom(item);
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
  private static void acceptPadding(
      int cp, StateName nextName, ParserState state, ParserStateItem item) {
    if (cp == state.paddingCp) {
      ParserStateItem next = state.getNext().copyFrom(item);
      next.name = nextName;
    }
  }

  /**
   * If <code>cp</code> is equal to the first codepoint in the string corresponding to the parameter
   * <code>type</code>, copies <code>item</code> to the new list in <code>state</code> and sets its
   * state name to a state determined by <code>type</code>.
   *
   * @param cp The code point to check.
   * @param type The string type, which corresponds to one of the CharSequences stored inside the
   *     state object. Read the code for details.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptString(
      int cp, StringType type, ParserState state, ParserStateItem item) {
    assert type != null;

    // For strict mode, don't accept if this string is a suffix that doesn't match the prefix
    if (state.mode == ParseMode.STRICT) {
      if (type == StringType.POS_SUFFIX
          && item.positiveAffixStatus != AffixStatus.SAW_PREFIX
          && !charSeqEmpty(state.pp)) {
        return;
      }
      if (type == StringType.NEG_SUFFIX
          && item.negativeAffixStatus != AffixStatus.SAW_PREFIX
          && !charSeqEmpty(state.np)) {
        return;
      }
    }

    CharSequence str = null;
    StateName doneName = null;
    switch (type) {
      case POS_PREFIX:
        str = state.pp;
        doneName = StateName.AFTER_PREFIX;
        break;
      case NEG_PREFIX:
        str = state.np;
        doneName = StateName.AFTER_PREFIX;
        break;
      case POS_SUFFIX:
        str = state.ps;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case NEG_SUFFIX:
        str = state.ns;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case EXPONENT_SEPARATOR:
        str = state.exponentSeparator;
        doneName = StateName.AFTER_EXPONENT_SEPARATOR;
        break;
    }
    if (str == null || str.length() == 0) return;

    if (cp == Character.codePointAt(str, 0)) {
      // Matches first character of prefix/suffix

      ParserStateItem next = state.getNext().copyFrom(item);

      if (type == StringType.NEG_PREFIX) next.sawNegative = true;
      if (type == StringType.NEG_SUFFIX) next.sawNegative = true;

      // Mark if we have seen a positive or negative prefix/suffix (needed for strict mode)
      if (type == StringType.POS_PREFIX) next.positiveAffixStatus = AffixStatus.SAW_PREFIX;
      if (type == StringType.NEG_PREFIX) next.negativeAffixStatus = AffixStatus.SAW_PREFIX;
      if (type == StringType.POS_SUFFIX) next.positiveAffixStatus = AffixStatus.SAW_SUFFIX;
      if (type == StringType.NEG_SUFFIX) next.negativeAffixStatus = AffixStatus.SAW_SUFFIX;

      // State item to consume next code point of string.
      if (str.length() != Character.charCount(cp)) {
        ParserStateItem continuation = state.getNext().copyFrom(next);
        continuation.name = StateName.INSIDE_STRING;
        continuation.offset = Character.charCount(cp);
        continuation.stringType = type;
      } else {
        // The entire string was only one character. Give a 5-point reward for consuming it.
        next.score += 5;
      }

      // State item to return to normal parsing.
      // This intentionally allows for partial string prefixes.
      // FIXME: Discuss options with Andy and Mark before merge to trunk.
      // Case that won't work: affix of the form "A¤B" where B is not whitespace.
      next.name = doneName;
    }
  }

  /**
   * If <code>cp</code> is equal to the codepoint at the current offset in the string corresponding
   * to <code>item.stringType</code>, copies <code>item</code> to the new list in <code>state</code>
   * and sets its state name to a state determined by <code>type</code>.
   *
   * <p>This method should only be called in a state following {@link #acceptString}.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptStringOffset(int cp, ParserState state, ParserStateItem item) {

    CharSequence str = null;
    StateName doneName = null;
    switch (item.stringType) {
      case POS_PREFIX:
        str = state.pp;
        doneName = StateName.AFTER_PREFIX;
        break;
      case NEG_PREFIX:
        str = state.np;
        doneName = StateName.AFTER_PREFIX;
        break;
      case POS_SUFFIX:
        str = state.ps;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case NEG_SUFFIX:
        str = state.ns;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case EXPONENT_SEPARATOR:
        str = state.exponentSeparator;
        doneName = StateName.AFTER_EXPONENT_SEPARATOR;
        break;
    }
    if (charSeqEmpty(str)) return;

    if (cp == Character.codePointAt(str, item.offset)) {
      // Matches current character of prefix/suffix.
      // State item to return to normal parsing.
      // This intentionally allows for partial string prefixes.
      // FIXME: Discuss options with Andy and Mark before merge to trunk.
      // Case that won't work: affix of the form "A¤B" where B is not whitespace.
      ParserStateItem next = state.getNext().copyFrom(item);
      next.name = doneName;

      // State item to consume next code point of string.
      if (str.length() != Character.charCount(cp) + item.offset) {
        ParserStateItem continuation = state.getNext().copyFrom(item);
        continuation.offset += Character.charCount(cp);
      } else {
        // We consumed the entire string. Give a 5-point reward.
        next.score += 5;
      }
    }
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
      int cp, StateName nextName, ParserState state, ParserStateItem item) {
    acceptCurrencyHelper(
        Currency.openParseState(state.uLocale, cp, Currency.LONG_NAME), cp, nextName, state, item);
    acceptCurrencyHelper(
        Currency.openParseState(state.uLocale, cp, Currency.SYMBOL_NAME),
        cp,
        nextName,
        state,
        item);
  }

  private static void acceptCurrencyHelper(
      TextTrieMap<Currency.CurrencyStringInfo>.ParseState trieState,
      int cp,
      StateName nextName,
      ParserState state,
      ParserStateItem item) {
    if (trieState == null) return;
    if (trieState.getCurrentMatches() != null) {
      // Match on first code point
      ParserStateItem next = state.getNext().copyFrom(item);
      // TODO: What should happen with multiple currency matches?
      next.isoCode = trieState.getCurrentMatches().next().getISOCode();
      next.name = nextName;
    }
    if (!trieState.atEnd()) {
      // Prepare for matches on future code points
      ParserStateItem next = state.getNext().copyFrom(item);
      next.name = StateName.INSIDE_CURRENCY;
      next.returnTo = nextName;
      next.trieState = trieState;
    }
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
  private static void acceptCurrencyOffset(int cp, ParserState state, ParserStateItem item) {
    item.trieState.accept(cp);
    if (item.trieState.getCurrentMatches() != null) {
      ParserStateItem next = state.getNext().copyFrom(item);
      // TODO: What should happen with multiple currency matches?
      next.isoCode = item.trieState.getCurrentMatches().next().getISOCode();
      next.name = item.returnTo;
    }
    if (!item.trieState.atEnd()) {
      state.getNext().copyFrom(item);
    }
  }

  /**
   * If <code>cp</code> is a digit character (as determined by either {@link UCharacter#digit} or
   * {@link ParserState#digitCps}), copies <code>item</code> to the new list in <code>state</code>
   * and sets its state name to one determined by <code>type</code>. Also copies the digit into a
   * field in the new item determined by <code>type</code>.
   *
   * @param cp The code point to check.
   * @param type The digit type, which determines the next state and the field into which to insert
   *     the digit.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptDigit(int cp, DigitType type, ParserState state, ParserStateItem item)
      throws ParseException {
    int digit = -1;

    // Check the Unicode digit character property
    digit = UCharacter.digit(cp, 10);

    // Check custom digits from DecimalFormatSymbols
    // This is done after checking the Unicode digit property because of performance.  Benchmarks
    // on an x86-64 machine show a ~10% overall performance drop for if this branch is evaluated
    // before the UCharacter.digit() branch.
    if (digit < 0) {
      for (int i = 0; i < 10; i++) {
        if (cp == state.digitCps[i]) {
          digit = i;
          break;
        }
      }
    }

    if (digit >= 0) {
      // Code point is a number
      ParserStateItem next = state.getNext().copyFrom(item);
      switch (type) {
        case INTEGER:
          next.name = StateName.AFTER_INTEGER_DIGIT;
          break;
        case FRACTION:
          next.name = StateName.AFTER_FRACTION_DIGIT;
          break;
        case EXPONENT:
          next.name = StateName.AFTER_EXPONENT_DIGIT;
          break;
      }
      next.appendDigit(digit, type);
    }
  }

  /**
   * If <code>cp</code> is a plus sign (as determined by the unicode set {@link #UNISET_PLUS}),
   * copies <code>item</code> to the new list in <code>state</code>. Loops back to the same state
   * name.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptPlusSign(int cp, ParserState state, ParserStateItem item) {
    if (UNISET_PLUS.contains(cp)) {
      // Code point is a plus sign
      // Loop back to the same state
      state.getNext().copyFrom(item);
    }
  }

  /**
   * If <code>cp</code> is a minus sign (as determined by the unicode set {@link #UNISET_MINUS}),
   * copies <code>item</code> to the new list in <code>state</code>. Loops back to the same state
   * name. Also sets the {@link ParserStateItem#sawNegative} or {@link
   * ParserStateItem#sawNegativeExponent} flag based on <code>type</code>.
   *
   * @param cp The code point to check.
   * @param type The digit type, which determines which negative flag to set.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptMinusSign(
      int cp, DigitType type, ParserState state, ParserStateItem item) {
    if (UNISET_MINUS.contains(cp)) {
      // Code point is a minus sign
      // Loop back to the same state and set negative flags
      ParserStateItem next = state.getNext().copyFrom(item);
      if (type == DigitType.INTEGER) {
        next.sawNegative = true;
      } else {
        assert type == DigitType.EXPONENT;
        next.sawNegativeExponent = true;
      }
    }
  }

  /**
   * If <code>cp</code> is a grouping separator (as determined by the unicode set {@link
   * #UNISET_GROUPING}), copies <code>item</code> to the new list in <code>state</code> and loops
   * back to the same state. Also accepts if <code>cp</code> is the locale-specific grouping
   * separator in {@link ParserState#groupingCp}, in which case the {@link
   * ParserStateItem#usesLocaleSymbols} flag is also set.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptGrouping(int cp, ParserState state, ParserStateItem item) {
    // Do not accept mixed grouping separators in the same string.
    if (item.groupingCp == -1) {
      // First time seeing a grouping separator.
      SeparatorType cpType = SeparatorType.fromCp(cp, state.mode == ParseMode.STRICT);

      // Accept separators in PLUS_LIKE or MINUS_LIKE according to the locale.
      // Always accept separators in OTHER_GROUPING (this includes whitespace).
      // Accept separators in UNKNOWN only if they exactly match the locale.
      if (cpType != SeparatorType.OTHER_GROUPING
          && cpType != state.groupingType1
          && cpType != state.groupingType2) {
        return;
      }
      if (cpType == SeparatorType.UNKNOWN && cp != state.groupingCp1 && cp != state.groupingCp2) {
        return;
      }

      // A match was found.
      ParserStateItem next = state.getNext().copyFrom(item);
      next.groupingCp = cp;

      // If this path uses the custom symbols, give a 5-point reward for priority in the selection
      // process if there are multiple possible parse paths.
      if (cpType == state.groupingType1 || cpType == state.groupingType2) {
        next.score += 5;
      }
    } else {
      // Have already seen a grouping separator.
      if (cp == item.groupingCp) {
        state.getNext().copyFrom(item);
      }
    }
  }

  /**
   * If <code>cp</code> is a decimal (as determined by the unicode set {@link #UNISET_DECIMAL}),
   * copies <code>item</code> to the new list in <code>state</code> and goes to {@link
   * StateName#AFTER_FRACTION_DIGIT}. Also accepts if <code>cp</code> is the locale-specific decimal
   * point in {@link ParserState#decimalCp}, in which case the {@link
   * ParserStateItem#usesLocaleSymbols} flag is also set.
   *
   * @param cp The code point to check.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptDecimalPoint(int cp, ParserState state, ParserStateItem item) {
    if (cp == item.groupingCp) {
      // Don't accept a decimal point that is the same as the grouping separator
      return;
    }

    SeparatorType cpType = SeparatorType.fromCp(cp, state.mode == ParseMode.STRICT);

    // Accept separators in PLUS_LIKE or MINUS_LIKE according to the locale.
    // Accept separators in OTHER_GROUPING or UNKNOWN only if they exactly match the locale.
    if (cpType != state.decimalType1 && cpType != state.decimalType2) {
      // No match.
      return;
    }
    if ((cpType == SeparatorType.OTHER_GROUPING || cpType == SeparatorType.UNKNOWN)
        && cp != state.decimalCp1
        && cp != state.decimalCp2) {
      // No match.
      return;
    }

    // A match was found.
    ParserStateItem next = state.getNext().copyFrom(item);
    next.name = StateName.AFTER_FRACTION_DIGIT;

    // If this path uses the custom symbols, give a 5-point reward for priority in the selection
    // process if there are multiple possible parse paths.
    if (cpType == state.decimalType1 || cpType == state.decimalType2) {
      next.score += 5;
    }
  }

  /** Utility method to check for CharSequence length with null safety. */
  private static boolean charSeqEmpty(CharSequence str) {
    return str == null || str.length() == 0;
  }
}
