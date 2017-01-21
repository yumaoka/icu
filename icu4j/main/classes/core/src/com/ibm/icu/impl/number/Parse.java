// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Arrays;

import com.ibm.icu.impl.number.Parse.ParseMode;
import com.ibm.icu.impl.number.formatters.PaddingFormat;
import com.ibm.icu.impl.number.formatters.PositiveNegativeAffixFormat;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.UnicodeSet;

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
      extends PositiveNegativeAffixFormat.IProperties, PaddingFormat.IProperties {

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
  }

  /**
   * @see #parse(String, ParsePosition, ParseMode, boolean, boolean, IProperties,
   *     DecimalFormatSymbols)
   */
  private enum StateName {
    BEFORE_PREFIX,
    INSIDE_POS_PREFIX,
    INSIDE_NEG_PREFIX,
    AFTER_PREFIX,
    AFTER_INTEGER_DIGIT,
    AFTER_FRACTION_DIGIT,
    INSIDE_EXPONENT_SEPARATOR,
    AFTER_EXPONENT_SEPARATOR,
    AFTER_EXPONENT_DIGIT,
    BEFORE_SUFFIX,
    BEFORE_SUFFIX_SEEN_EXPONENT,
    INSIDE_POS_SUFFIX,
    INSIDE_NEG_SUFFIX,
    AFTER_SUFFIX
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

  /**
   * Holds a snapshot in time of a single parse path. This includes the digits seen so far, the
   * current state name, and other properties like the grouping separator used on this parse path,
   * details about the exponent and negative signs, etc.
   */
  private static class ParserStateItem {
    StateName name;
    byte[] digits = new byte[100];
    int scale;
    int numDigits;
    int offset;
    int groupingCp;
    int exponent;
    boolean sawNegative;
    boolean sawNegativeExponent;
    AffixStatus positiveAffixStatus;
    AffixStatus negativeAffixStatus;
    boolean usesLocaleSymbols;

    /**
     * Clears the instance so that it can be re-used.
     *
     * @return Myself, for chaining.
     */
    ParserStateItem clear() {
      name = StateName.BEFORE_PREFIX;
      Arrays.fill(digits, (byte) 0);
      scale = 0;
      numDigits = 0;
      offset = 0;
      groupingCp = -1;
      exponent = 0;
      sawNegative = false;
      sawNegativeExponent = false;
      positiveAffixStatus = AffixStatus.NOT_SEEN;
      negativeAffixStatus = AffixStatus.NOT_SEEN;
      usesLocaleSymbols = false;
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
      positiveAffixStatus = other.positiveAffixStatus;
      negativeAffixStatus = other.negativeAffixStatus;
      usesLocaleSymbols = other.usesLocaleSymbols;
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
      sb.append(" seenNegative:");
      sb.append(sawNegative ? 1 : 0);
      sb.append(sawNegativeExponent ? 1 : 0);
      sb.append(" affixStatus:");
      sb.append(positiveAffixStatus.ordinal());
      sb.append(negativeAffixStatus.ordinal());
      sb.append(" localeSyms:");
      sb.append(usesLocaleSymbols ? 1 : 0);
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

    int paddingCp;
    int groupingCp;
    int decimalCp;
    CharSequence exponentSeparator;
    CharSequence pp;
    CharSequence np;
    CharSequence ps;
    CharSequence ns;
    int[] digitCps = new int[10];

    ParseMode mode;
    boolean integerOnly;
    boolean ignoreExponent;

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
      paddingCp = -1;
      groupingCp = -1;
      decimalCp = -1;
      exponentSeparator = null;
      pp = null;
      np = null;
      ps = null;
      ns = null;
      Arrays.fill(digitCps, -1);
      mode = null;
      integerOnly = false;
      ignoreExponent = false;
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
  // TODO: Re-generate these sets from the database. They probably haven't been updated in a while.
  private static final UnicodeSet UNISET_WHITESPACE = new UnicodeSet("[[:whitespace:]]").freeze();

  private static final UnicodeSet UNISET_GROUPING =
      new UnicodeSet(
              0x0020, 0x0020, 0x002C, 0x002C, 0x060C, 0x060C, 0x066B, 0x066B, 0x3001, 0x3001,
              0xFE10, 0xFE11, 0xFE50, 0xFE51, 0xFF0C, 0xFF0C, 0xFF64, 0xFF64)
          .freeze();

  private static final UnicodeSet UNISET_DECIMAL =
      new UnicodeSet(
              0x002E, 0x002E, 0x2024, 0x2024, 0x3002, 0x3002, 0xFE12, 0xFE12, 0xFE52, 0xFE52,
              0xFF0E, 0xFF0E, 0xFF61, 0xFF61)
          .freeze();

  /**
   * @internal
   * @deprecated This API is ICU internal only.
   */
  @Deprecated
  public static final UnicodeSet UNISET_PLUS =
      new UnicodeSet(
              0x002B, 0x002B, 0x207A, 0x207A, 0x208A, 0x208A, 0x2795, 0x2795, 0xFB29, 0xFB29,
              0xFE62, 0xFE62, 0xFF0B, 0xFF0B)
          .freeze();

  /**
   * @internal
   * @deprecated This API is ICU internal only.
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
      String input, ParsePosition ppos, IProperties properties, DecimalFormatSymbols symbols)
      throws ParseException {

    // Set up the initial state
    ParserState state = threadLocalParseState.get().clear();
    // TODO: Many of these calls use only the first codepoint from a string that could contain
    // multiple codepoints.  Is that OK?
    if (properties.getPaddingString() != null
        && !UNISET_WHITESPACE.contains(properties.getPaddingString())) {
      // TODO: Should we accept treating whitespace like padding here?
      state.paddingCp = Character.codePointAt(properties.getPaddingString(), 0);
    }
    state.groupingCp = Character.codePointAt(symbols.getGroupingSeparatorString(), 0);
    state.decimalCp = Character.codePointAt(symbols.getDecimalSeparatorString(), 0);
    PNAffixGenerator.Result affixResult =
        PNAffixGenerator.getThreadLocalInstance().getModifiers(symbols, properties);
    state.pp = affixResult.positive.prefix;
    state.np = affixResult.negative.prefix;
    state.ps = affixResult.positive.suffix;
    state.ns = affixResult.negative.suffix;
    state.exponentSeparator = symbols.getExponentSeparator();
    @SuppressWarnings("deprecation")
    String[] digitStrings = symbols.getDigitStringsLocal();
    for (int i = 0; i < 10; i++) {
      state.digitCps[i] = Character.codePointAt(digitStrings[i], 0);
    }
    state.mode = properties.getParseMode();
    state.integerOnly = properties.getParseIntegerOnly();
    state.ignoreExponent = properties.getParseIgnoreExponent();
    ParserStateItem initialHolder = state.getNext().clear();
    initialHolder.name = StateName.BEFORE_PREFIX;

    // Start walking through the string, one codepoint at a time. Backtracking is not allowed. This
    // is to enforce linear runtime and prevent edge cases that could result in an infinite loop.
    int offset = 0;
    for (; offset < input.length(); ) {
      int cp = Character.codePointAt(input, offset);
      state.swap();
      for (int i = 0; i < state.prevLength; i++) {
        ParserStateItem item = state.prevItems[i];
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
            break;

          case INSIDE_POS_PREFIX:
            // Already read the first character of the positive prefix
            acceptStringOffset(cp, StringType.POS_PREFIX, state, item);
            break;

          case INSIDE_NEG_PREFIX:
            // Already read the first character of the negative prefix
            acceptStringOffset(cp, StringType.NEG_PREFIX, state, item);
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
            break;

          case AFTER_INTEGER_DIGIT:
            // Previous character was an integer digit (or grouping/whitespace)
            acceptGrouping(cp, state, item);
            acceptDigit(cp, DigitType.INTEGER, state, item);
            if (!state.integerOnly) {
              acceptDecimalPoint(cp, state, item);
            }
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
            }
            break;

          case AFTER_FRACTION_DIGIT:
            // We encountered a decimal point
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptDigit(cp, DigitType.FRACTION, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
            }
            break;

          case INSIDE_EXPONENT_SEPARATOR:
            // This case runs only when the exponent separator is longer than 1 code point
            acceptStringOffset(cp, StringType.EXPONENT_SEPARATOR, state, item);
            break;

          case AFTER_EXPONENT_SEPARATOR:
            acceptPlusSign(cp, state, item);
            acceptMinusSign(cp, DigitType.EXPONENT, state, item);
            acceptDigit(cp, DigitType.EXPONENT, state, item);

          case AFTER_EXPONENT_DIGIT:
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX_SEEN_EXPONENT, state, item);
            acceptDigit(cp, DigitType.EXPONENT, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            break;

          case BEFORE_SUFFIX:
            // Accept whitespace, suffixes, and exponent separators
            // Note: The only way to get here is to read whitespace or padding after a fraction
            if (state.mode == ParseMode.LENIENT) {
              acceptWhitespace(cp, StateName.BEFORE_SUFFIX, state, item);
            }
            acceptPadding(cp, StateName.BEFORE_SUFFIX, state, item);
            acceptString(cp, StringType.POS_SUFFIX, state, item);
            acceptString(cp, StringType.NEG_SUFFIX, state, item);
            if (!state.ignoreExponent) {
              acceptString(cp, StringType.EXPONENT_SEPARATOR, state, item);
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
            break;

          case INSIDE_POS_SUFFIX:
            // Already read the first character of the positive suffix
            acceptStringOffset(cp, StringType.POS_SUFFIX, state, item);
            break;

          case INSIDE_NEG_SUFFIX:
            // Already read the first character of the negative suffix
            acceptStringOffset(cp, StringType.NEG_SUFFIX, state, item);
            break;

          case AFTER_SUFFIX:
            // Do not accept any further characters.
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

        // If we get here, then this candidate is acceptable.
        // Use the earliest candidate in the list, or the first one that uses locale symbols for
        // decimal point and/or grouping separator.
        if (best == null) {
          best = item;
        } else if (!best.usesLocaleSymbols && item.usesLocaleSymbols) {
          best = item;
        }
      }

      if (best != null) {
        ppos.setIndex(offset);
        return best.toNumber();
      } else {
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
    StateName nextName = null;
    StateName doneName = null;
    switch (type) {
      case POS_PREFIX:
        str = state.pp;
        nextName = StateName.INSIDE_POS_PREFIX;
        doneName = StateName.AFTER_PREFIX;
        break;
      case NEG_PREFIX:
        str = state.np;
        nextName = StateName.INSIDE_NEG_PREFIX;
        doneName = StateName.AFTER_PREFIX;
        break;
      case POS_SUFFIX:
        str = state.ps;
        nextName = StateName.INSIDE_POS_SUFFIX;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case NEG_SUFFIX:
        str = state.ns;
        nextName = StateName.INSIDE_NEG_SUFFIX;
        doneName = StateName.AFTER_SUFFIX;
        break;
      case EXPONENT_SEPARATOR:
        str = state.exponentSeparator;
        nextName = StateName.INSIDE_EXPONENT_SEPARATOR;
        doneName = StateName.AFTER_EXPONENT_SEPARATOR;
        break;
    }
    if (str == null || str.length() == 0) return;

    if (cp == Character.codePointAt(str, 0)) {
      // Matches first character of prefix/suffix
      ParserStateItem next = state.getNext().copyFrom(item);
      if (str.length() == Character.charCount(cp)) {
        next.name = doneName;
      } else {
        next.name = nextName;
        next.offset = Character.charCount(cp);
      }

      if (type == StringType.NEG_PREFIX) next.sawNegative = true;
      if (type == StringType.NEG_SUFFIX) next.sawNegative = true;

      // Mark if we have seen a positive or negative prefix/suffix (needed for strict mode)
      if (type == StringType.POS_PREFIX) next.positiveAffixStatus = AffixStatus.SAW_PREFIX;
      if (type == StringType.NEG_PREFIX) next.negativeAffixStatus = AffixStatus.SAW_PREFIX;
      if (type == StringType.POS_SUFFIX) next.positiveAffixStatus = AffixStatus.SAW_SUFFIX;
      if (type == StringType.NEG_SUFFIX) next.negativeAffixStatus = AffixStatus.SAW_SUFFIX;
    }
  }

  /**
   * If <code>cp</code> is equal to the codepoint at the current offset in the string corresponding
   * to <code>type</code>, copies <code>item</code> to the new list in <code>state</code> and sets
   * its state name to a state determined by <code>type</code>.
   *
   * <p>This method should only be called in a state following {@link #acceptString}.
   *
   * @param cp The code point to check.
   * @param type The string type, which corresponds to one of the CharSequences stored inside the
   *     state object. Read the code for details.
   * @param state The state object to update.
   * @param item The old state leading into the code point.
   */
  private static void acceptStringOffset(
      int cp, StringType type, ParserState state, ParserStateItem item) {

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
    if (charSeqEmpty(str)) return;

    if (cp == Character.codePointAt(str, item.offset)) {
      // Matches current character of prefix/suffix
      ParserStateItem next = state.getNext().copyFrom(item);
      if (str.length() == Character.charCount(cp) + item.offset) {
        next.name = doneName;
      } else {
        // Keep the same state and update the offset
        next.offset += Character.charCount(cp);
      }
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
      // First time seeing a grouping separator
      if (UNISET_GROUPING.contains(cp) || cp == state.groupingCp) {
        ParserStateItem next = state.getNext().copyFrom(item);
        next.groupingCp = cp;

        if (cp == state.groupingCp) {
          next.usesLocaleSymbols = true;
        }
      }
    } else {
      // Have already seen a grouping separator
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
    if (UNISET_DECIMAL.contains(cp) || cp == state.decimalCp) {
      // Code point is a decimal separator
      ParserStateItem next = state.getNext().copyFrom(item);
      next.name = StateName.AFTER_FRACTION_DIGIT;

      if (cp == state.decimalCp) {
        next.usesLocaleSymbols = true;
      }
    }
  }

  /** Utility method to check for CharSequence length with null safety. */
  private static boolean charSeqEmpty(CharSequence str) {
    return str == null || str.length() == 0;
  }
}
