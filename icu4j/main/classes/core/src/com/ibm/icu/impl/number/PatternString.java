// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;

import com.ibm.icu.impl.number.formatters.PaddingFormat.PaddingLocation;
import com.ibm.icu.text.DecimalFormatSymbols;

/**
 * Handles parsing and creation of the compact pattern string representation of a decimal format.
 */
public class PatternString {

  /**
   * Parses a pattern string into a new property bag.
   *
   * @param pattern The pattern string, like "#,##0.00"
   * @return A property bag object.
   * @throws IllegalArgumentException If there is a syntax error in the pattern string.
   */
  public static Properties parseToProperties(String pattern) {
    Properties properties = new Properties();
    LdmlDecimalPatternParser.parse(pattern, properties);
    return properties;
  }

  /**
   * Parses a pattern string into an existing property bag. All properties that can be encoded into
   * a pattern string will be overwritten with either their default value or with the value coming
   * from the pattern string. Properties that cannot be encoded into a pattern string, such as
   * rounding mode, are not modified.
   *
   * @param pattern The pattern string, like "#,##0.00"
   * @param properties The property bag object to overwrite.
   * @throws IllegalArgumentException If there was a syntax error in the pattern string.
   */
  public static void parseToExistingProperties(String pattern, Properties properties) {
    LdmlDecimalPatternParser.parse(pattern, properties);
  }

  /**
   * Creates a pattern string from a property bag.
   *
   * <p>Since pattern strings support only a subset of the functionality available in a property
   * bag, a new property bag created from the string returned by this function may not be the same
   * as the original property bag.
   *
   * @param properties The property bag to serialize.
   * @return A pattern string approximately serializing the property bag.
   */
  public static String propertiesToString(Properties properties) {
    StringBuilder sb = new StringBuilder();

    // Convenience references
    // The Math.min() calls prevent DoS
    int dosMax = 100;
    int groupingSize = Math.min(properties.getSecondaryGroupingSize(), dosMax);
    int firstGroupingSize = Math.min(properties.getGroupingSize(), dosMax);
    int paddingWidth = Math.min(properties.getPaddingWidth(), dosMax);
    PaddingLocation paddingLocation = properties.getPaddingLocation();
    CharSequence paddingString = properties.getPaddingString();
    int minInt = Math.max(Math.min(properties.getMinimumIntegerDigits(), dosMax), 0);
    int maxInt = Math.min(properties.getMaximumIntegerDigits(), dosMax);
    int minFrac = Math.max(Math.min(properties.getMinimumFractionDigits(), dosMax), 0);
    int maxFrac = Math.min(properties.getMaximumFractionDigits(), dosMax);
    int minSig = Math.min(properties.getMinimumSignificantDigits(), dosMax);
    int maxSig = Math.min(properties.getMaximumSignificantDigits(), dosMax);
    boolean alwaysShowDecimal = properties.getAlwaysShowDecimal();
    int exponentDigits = Math.min(properties.getExponentDigits(), dosMax);
    boolean exponentShowPlusSign = properties.getExponentShowPlusSign();
    CharSequence pp = properties.getPositivePrefix();
    CharSequence ppp = properties.getPositivePrefixPattern();
    CharSequence ps = properties.getPositiveSuffix();
    CharSequence psp = properties.getPositiveSuffixPattern();
    CharSequence np = properties.getNegativePrefix();
    CharSequence npp = properties.getNegativePrefixPattern();
    CharSequence ns = properties.getNegativeSuffix();
    CharSequence nsp = properties.getNegativeSuffixPattern();

    // Prefixes
    if (ppp != null) sb.append(ppp);
    escape(pp, sb);
    int afterPrefixPos = sb.length();

    // Figure out the grouping sizes.
    int grouping1, grouping2;
    if (groupingSize != Math.min(dosMax, Properties.DEFAULT_SECONDARY_GROUPING_SIZE)
        && firstGroupingSize != Math.min(dosMax, Properties.DEFAULT_GROUPING_SIZE)
        && groupingSize != firstGroupingSize) {
      grouping1 = groupingSize;
      grouping2 = firstGroupingSize;
    } else if (groupingSize != Math.min(dosMax, Properties.DEFAULT_SECONDARY_GROUPING_SIZE)) {
      grouping1 = 0;
      grouping2 = groupingSize;
    } else if (firstGroupingSize != Math.min(dosMax, Properties.DEFAULT_GROUPING_SIZE)) {
      grouping1 = 0;
      grouping2 = firstGroupingSize;
    } else {
      grouping1 = 0;
      grouping2 = 0;
    }
    int groupingLength = grouping1 + grouping2 + 1;

    // Figure out the digits we need to put in the pattern.
    BigDecimal roundingInterval = properties.getRoundingInterval();
    StringBuilder digitsString = new StringBuilder();
    int digitsStringScale = 0;
    if (maxSig != Math.min(dosMax, Properties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS)) {
      // Significant Digits.
      while (digitsString.length() < minSig) {
        digitsString.append('@');
      }
      while (digitsString.length() < maxSig) {
        digitsString.append('#');
      }
    } else if (roundingInterval != Properties.DEFAULT_ROUNDING_INTERVAL) {
      // Rounding Interval.
      digitsStringScale = -roundingInterval.scale();
      // TODO: Check for DoS here?
      String str = roundingInterval.scaleByPowerOfTen(roundingInterval.scale()).toPlainString();
      if (str.charAt(0) == '\'') {
        // TODO: Unsupported operation exception or fail silently?
        digitsString.append(str, 1, str.length());
      } else {
        digitsString.append(str);
      }
    }
    while (digitsString.length() + digitsStringScale < minInt) {
      digitsString.insert(0, '0');
    }
    while (-digitsStringScale < minFrac) {
      digitsString.append('0');
      digitsStringScale--;
    }

    // Write the digits to the string builder
    int m0 = Math.max(groupingLength, digitsString.length() + digitsStringScale);
    m0 = (maxInt != dosMax) ? Math.max(maxInt, m0) - 1 : m0 - 1;
    int mN = (maxFrac != dosMax) ? Math.min(-maxFrac, digitsStringScale) : digitsStringScale;
    for (int magnitude = m0; magnitude >= mN; magnitude--) {
      int di = digitsString.length() + digitsStringScale - magnitude - 1;
      if (di < 0 || di >= digitsString.length()) {
        sb.append('#');
      } else {
        sb.append(digitsString.charAt(di));
      }
      if (magnitude > 0 && magnitude == grouping1 + grouping2) {
        sb.append(',');
      } else if (magnitude > 0 && magnitude == grouping2) {
        sb.append(',');
      } else if (magnitude == 0 && (alwaysShowDecimal || mN < 0)) {
        sb.append('.');
      }
    }

    // Exponential notation
    if (exponentDigits != Math.min(dosMax, Properties.DEFAULT_EXPONENT_DIGITS)) {
      sb.append('E');
      if (exponentShowPlusSign) {
        sb.append('+');
      }
      for (int i = 0; i < exponentDigits; i++) {
        sb.append('0');
      }
    }

    // Suffixes
    int beforeSuffixPos = sb.length();
    if (psp != null) sb.append(psp);
    escape(ps, sb);

    // Resolve Padding
    if (paddingWidth != Properties.DEFAULT_PADDING_WIDTH) {
      while (paddingWidth - sb.length() > 0) {
        sb.insert(afterPrefixPos, '#');
        beforeSuffixPos++;
      }
      int addedLength;
      switch (paddingLocation) {
        case BEFORE_PREFIX:
          addedLength = escape(paddingString, sb, 0);
          sb.insert(0, '*');
          afterPrefixPos += addedLength + 1;
          beforeSuffixPos += addedLength + 1;
          break;
        case AFTER_PREFIX:
          addedLength = escape(paddingString, sb, afterPrefixPos);
          sb.insert(afterPrefixPos, '*');
          afterPrefixPos += addedLength + 1;
          beforeSuffixPos += addedLength + 1;
          break;
        case BEFORE_SUFFIX:
          escape(paddingString, sb, beforeSuffixPos);
          sb.insert(beforeSuffixPos, '*');
          break;
        case AFTER_SUFFIX:
          sb.append('*');
          escape(paddingString, sb);
          break;
      }
    }

    // Negative affixes
    if (np != null || npp != null || ns != null || nsp != null) {
      sb.append(';');
      if (npp != null) sb.append(npp);
      escape(np, sb);
      // Copy the positive digit format into the negative.
      // This is optional; the pattern is the same as if '#' were appended here instead.
      sb.append(sb, afterPrefixPos, beforeSuffixPos);
      if (nsp != null) sb.append(nsp);
      escape(ns, sb);
    }

    return sb.toString();
  }

  /** @return The number of chars inserted. */
  private static int escape(CharSequence input, StringBuilder sb) {
    if (input == null) return 0;
    int length = input.length();
    if (length == 0) return 0;
    int startLength = sb.length();
    if (length > 1) sb.append('\'');
    for (int i = 0; i < length; i++) {
      char ch = input.charAt(i);
      if (ch == '\'') {
        sb.append("''");
      } else {
        sb.append(ch);
      }
    }
    if (length > 1) sb.append('\'');
    return sb.length() - startLength;
  }

  /** @return The number of chars inserted. */
  private static int escape(CharSequence input, StringBuilder sb, int insertIndex) {
    // Although this triggers a new object creation, it reduces the number of calls to insert (and
    // therefore System.arraycopy).
    StringBuilder temp = new StringBuilder();
    int length = escape(input, temp);
    sb.insert(insertIndex, temp);
    return length;
  }

  /**
   * Converts a pattern between standard notation and localized notation. Localized notation means
   * that instead of using generic placeholders in the pattern, you use the corresponding
   * locale-specific characters instead.
   *
   * @param input The pattern to convert.
   * @param symbols The symbols corresponding to the localized pattern.
   * @param toLocalized true to convert from standard to localized notation; false to convert from
   *     localized to standard notation.
   * @return The pattern expressed in the other notation.
   * @deprecated ICU 59 This method is provided for backwards compatibility and should not be used
   *     in any new code.
   */
  @Deprecated
  public static String convertLocalized(
      CharSequence input, DecimalFormatSymbols symbols, boolean toLocalized) {
    if (input == null) return null;

    // Construct a table of code points to be converted between localized and standard.
    int[][] table = new int[6][2];
    int standIdx = toLocalized ? 0 : 1;
    int localIdx = toLocalized ? 1 : 0;
    table[0][standIdx] = '%';
    table[0][localIdx] = Character.codePointAt(symbols.getPercentString(), 0);
    table[1][standIdx] = '‰';
    table[1][localIdx] = Character.codePointAt(symbols.getPerMillString(), 0);
    table[2][standIdx] = '.';
    table[2][localIdx] = Character.codePointAt(symbols.getDecimalSeparatorString(), 0);
    table[3][standIdx] = ',';
    table[3][localIdx] = Character.codePointAt(symbols.getGroupingSeparatorString(), 0);
    table[4][standIdx] = '-';
    table[4][localIdx] = Character.codePointAt(symbols.getMinusSignString(), 0);
    table[5][standIdx] = '+';
    table[5][localIdx] = Character.codePointAt(symbols.getPlusSignString(), 0);

    // Iterate through the string and convert
    int offset = 0;
    boolean insideQuote = false;
    StringBuilder result = new StringBuilder();
    for (; offset < input.length(); ) {
      int cp = Character.codePointAt(input, offset);
      int cpToAppend = cp;
      if (insideQuote) {
        if (cp == '\'') {
          insideQuote = false;
        }
      } else {
        if (cp == '\'') {
          insideQuote = true;
        } else {
          for (int i = 0; i < table.length; i++) {
            if (table[i][0] == cp) {
              cpToAppend = table[i][1];
              break;
            }
          }
        }
      }
      result.appendCodePoint(cpToAppend);
      offset += Character.charCount(cp);
    }
    return result.toString();
  }

  /** Implements a recursive descent parser for decimal format patterns. */
  static class LdmlDecimalPatternParser {

    /**
     * An internal, intermediate data structure used for storing parse results before they are
     * finalized into a DecimalFormatPattern.Builder.
     */
    private static class PatternParseResult {
      SubpatternParseResult positive = new SubpatternParseResult();
      SubpatternParseResult negative = null;

      /** Finalizes the temporary data stored in the PatternParseResult to the Builder. */
      void saveToProperties(Properties properties) {
        // Translate from PatternState to Properties.
        // Note that most data from "negative" is ignored per the specification of DecimalFormat.

        // Grouping settings
        if (positive.groupingSizes[1] != -1) {
          properties.setGroupingSize(positive.groupingSizes[0]);
        } else {
          properties.setGroupingSize(Properties.DEFAULT_GROUPING_SIZE);
        }
        if (positive.groupingSizes[2] != -1) {
          properties.setSecondaryGroupingSize(positive.groupingSizes[1]);
        } else {
          properties.setSecondaryGroupingSize(Properties.DEFAULT_SECONDARY_GROUPING_SIZE);
        }

        // Rounding settings
        // Don't set basic rounding when there is a currency sign; defer to CurrencyUsage
        if (positive.minimumSignificantDigits > 0) {
          if (!positive.hasCurrencySign) {
            properties.setMinimumFractionDigits(Properties.DEFAULT_MINIMUM_FRACTION_DIGITS);
            properties.setMaximumFractionDigits(Properties.DEFAULT_MAXIMUM_FRACTION_DIGITS);
            properties.setRoundingInterval(Properties.DEFAULT_ROUNDING_INTERVAL);
          }
          properties.setMinimumSignificantDigits(positive.minimumSignificantDigits);
          properties.setMaximumSignificantDigits(positive.maximumSignificantDigits);
        } else if (!positive.rounding.isZero()) {
          if (!positive.hasCurrencySign) {
            properties.setMinimumFractionDigits(positive.minimumFractionDigits);
            properties.setMaximumFractionDigits(positive.maximumFractionDigits);
            properties.setRoundingInterval(positive.rounding.toBigDecimal());
          }
          properties.setMinimumSignificantDigits(Properties.DEFAULT_MINIMUM_SIGNIFICANT_DIGITS);
          properties.setMaximumSignificantDigits(Properties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS);
        } else {
          if (!positive.hasCurrencySign) {
            properties.setMinimumFractionDigits(positive.minimumFractionDigits);
            properties.setMaximumFractionDigits(positive.maximumFractionDigits);
            properties.setRoundingInterval(Properties.DEFAULT_ROUNDING_INTERVAL);
          }
          properties.setMinimumSignificantDigits(Properties.DEFAULT_MINIMUM_SIGNIFICANT_DIGITS);
          properties.setMaximumSignificantDigits(Properties.DEFAULT_MAXIMUM_SIGNIFICANT_DIGITS);
        }

        // Backwards compatibility:
        // If the pattern starts with '.' or if it doesn't have '.' (and isn't sigdigit notation),
        // then minInt can be zero. Otherwise, minInt needs to be at least 1.
        if ((!positive.hasDecimal && positive.minimumSignificantDigits == 0)
            || (positive.hasDecimal && positive.totalIntegerDigits == 0)) {
          properties.setMinimumIntegerDigits(positive.minimumIntegerDigits);
        } else {
          properties.setMinimumIntegerDigits(Math.max(1, positive.minimumIntegerDigits));
        }

        // If the pattern ends with a '.' then force the decimal point.
        if (positive.hasDecimal && positive.maximumFractionDigits == 0) {
          properties.setAlwaysShowDecimal(true);
        } else {
          properties.setAlwaysShowDecimal(false);
        }

        // Scientific notation settings
        if (positive.exponentDigits > 0) {
          properties.setExponentShowPlusSign(positive.exponentShowPlusSign);
          properties.setExponentDigits(positive.exponentDigits);
          if (positive.minimumSignificantDigits == 0) {
            // patterns without '@' can define max integer digits, used for engineering notation
            properties.setMaximumIntegerDigits(positive.totalIntegerDigits);
          } else {
            // patterns with '@' cannot define max integer digits
            properties.setMaximumIntegerDigits(Properties.DEFAULT_MAXIMUM_INTEGER_DIGITS);
          }
        } else {
          properties.setExponentShowPlusSign(Properties.DEFAULT_EXPONENT_SHOW_PLUS_SIGN);
          properties.setExponentDigits(Properties.DEFAULT_EXPONENT_DIGITS);
          properties.setMaximumIntegerDigits(Properties.DEFAULT_MAXIMUM_INTEGER_DIGITS);
        }

        // Padding settings
        if (positive.padding.length() > 0) {
          // The width of the positive prefix and suffix templates are included in the padding
          int paddingWidth =
              positive.paddingWidth
                  + LiteralString.unescapedLength(positive.prefix)
                  + LiteralString.unescapedLength(positive.suffix);
          properties.setPaddingWidth(paddingWidth);
          properties.setPaddingString(positive.padding.toString());
          assert positive.paddingLocation != null;
          properties.setPaddingLocation(positive.paddingLocation);
        } else {
          properties.setPaddingWidth(Properties.DEFAULT_PADDING_WIDTH);
          properties.setPaddingString(Properties.DEFAULT_PADDING_STRING);
          properties.setPaddingLocation(Properties.DEFAULT_PADDING_LOCATION);
        }

        // Set the affixes
        // Always call the setter, even if the prefixes are empty, especially in the case of the
        // negative prefix pattern, to prevent default values from overriding the pattern.
        properties.setPositivePrefixPattern(positive.prefix);
        properties.setPositiveSuffixPattern(positive.suffix);
        if (negative != null) {
          properties.setNegativePrefixPattern(negative.prefix);
          properties.setNegativeSuffixPattern(negative.suffix);
        } else {
          properties.setNegativePrefixPattern(null);
          properties.setNegativeSuffixPattern(null);
        }

        // Set the magnitude multiplier
        if (positive.hasPercentSign) {
          properties.setMagnitudeMultiplier(2);
        } else if (positive.hasPerMilleSign) {
          properties.setMagnitudeMultiplier(3);
        } else {
          properties.setMagnitudeMultiplier(Properties.DEFAULT_MAGNITUDE_MULTIPLIER);
        }
      }
    }

    private static class SubpatternParseResult {
      int[] groupingSizes = new int[] {0, -1, -1};
      int minimumIntegerDigits = 0;
      int totalIntegerDigits = 0;
      int minimumFractionDigits = 0;
      int maximumFractionDigits = 0;
      int minimumSignificantDigits = 0;
      int maximumSignificantDigits = 0;
      boolean hasDecimal = false;
      int paddingWidth = 0;
      PaddingLocation paddingLocation = null;
      FormatQuantity4 rounding = new FormatQuantity4();
      boolean exponentShowPlusSign = false;
      int exponentDigits = 0;
      boolean hasPercentSign = false;
      boolean hasPerMilleSign = false;
      boolean hasCurrencySign = false;

      StringBuilder padding = new StringBuilder();
      StringBuilder prefix = new StringBuilder();
      StringBuilder suffix = new StringBuilder();
    }

    /** An internal class used for tracking the cursor during parsing of a pattern string. */
    private static class ParserState {
      final String pattern;
      int offset;

      ParserState(String pattern) {
        this.pattern = pattern;
        this.offset = 0;
      }

      int peek() {
        if (offset == pattern.length()) {
          return -1;
        } else {
          return pattern.codePointAt(offset);
        }
      }

      int next() {
        int codePoint = peek();
        offset += Character.charCount(codePoint);
        return codePoint;
      }

      IllegalArgumentException toParseException(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unexpected character in decimal format pattern: ");
        sb.append(message);
        sb.append(": ");
        if (peek() == -1) {
          sb.append("EOL");
        } else {
          sb.append("'");
          sb.append(Character.toChars(peek()));
          sb.append("'");
        }
        return new IllegalArgumentException(sb.toString());
      }
    }

    static void parse(String pattern, Properties properties) {
      if (pattern.isEmpty()) return;
      // TODO: Use whitespace characters from PatternProps
      ParserState state = new ParserState(pattern);
      PatternParseResult result = new PatternParseResult();
      consumePattern(state, result);
      result.saveToProperties(properties);
    }

    private static void consumePattern(ParserState state, PatternParseResult result) {
      // pattern := subpattern (';' subpattern)?
      consumeSubpattern(state, result.positive);
      if (state.peek() == ';') {
        state.next(); // consume the ';'
        result.negative = new SubpatternParseResult();
        consumeSubpattern(state, result.negative);
      }
      if (state.peek() != -1) {
        throw state.toParseException("pattern");
      }
    }

    private static void consumeSubpattern(ParserState state, SubpatternParseResult result) {
      // subpattern := literals? number exponent? literals?
      consumePadding(state, result, PaddingLocation.BEFORE_PREFIX);
      consumeAffix(state, result, result.prefix);
      consumePadding(state, result, PaddingLocation.AFTER_PREFIX);
      consumeFormat(state, result);
      consumeExponent(state, result);
      consumePadding(state, result, PaddingLocation.BEFORE_SUFFIX);
      consumeAffix(state, result, result.suffix);
      consumePadding(state, result, PaddingLocation.AFTER_SUFFIX);
    }

    private static void consumePadding(
        ParserState state, SubpatternParseResult result, PaddingLocation paddingLocation) {
      if (state.peek() != '*') {
        return;
      }
      result.paddingLocation = paddingLocation;
      state.next(); // consume the '*'
      consumeLiteral(state, result.padding);
    }

    private static void consumeAffix(
        ParserState state, SubpatternParseResult result, StringBuilder destination) {
      // literals := { literal }
      while (true) {
        switch (state.peek()) {
          case '#':
          case '@':
          case ';':
          case '*':
          case '.':
          case ',':
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
          case -1:
            // Characters that cannot appear unquoted in a literal
            return;

          case '%':
            result.hasPercentSign = true;
            break;

          case '‰':
            result.hasPerMilleSign = true;
            break;

          case '¤':
            result.hasCurrencySign = true;
            break;
        }
        consumeLiteral(state, destination);
      }
    }

    private static void consumeLiteral(ParserState state, StringBuilder destination) {
      if (state.peek() == -1) {
        throw state.toParseException("expected unquoted literal but found end of string");
      } else if (state.peek() == '\'') {
        destination.appendCodePoint(state.next()); // consume the starting quote
        while (state.peek() != '\'') {
          if (state.peek() == -1) {
            throw state.toParseException("expected quoted literal but found end of string");
          } else {
            destination.appendCodePoint(state.next()); // consume a quoted character
          }
        }
        destination.appendCodePoint(state.next()); // consume the ending quote
      } else {
        // consume a non-quoted literal character
        destination.appendCodePoint(state.next());
      }
    }

    private static void consumeFormat(ParserState state, SubpatternParseResult result) {
      consumeIntegerFormat(state, result);
      if (state.peek() == '.') {
        state.next(); // consume the decimal point
        result.hasDecimal = true;
        result.paddingWidth += 1;
        consumeFractionFormat(state, result);
      }
    }

    private static void consumeIntegerFormat(ParserState state, SubpatternParseResult result) {
      boolean seenSignificantDigitMarker = false;

      while (true) {
        switch (state.peek()) {
          case ',':
            result.paddingWidth += 1;
            result.groupingSizes[2] = result.groupingSizes[1];
            result.groupingSizes[1] = result.groupingSizes[0];
            result.groupingSizes[0] = 0;
            break;

          case '#':
            result.paddingWidth += 1;
            result.groupingSizes[0] += 1;
            result.totalIntegerDigits += (seenSignificantDigitMarker ? 0 : 1);
            // no change to result.minimumIntegerDigits
            // no change to result.minimumSignificantDigits
            result.maximumSignificantDigits += (seenSignificantDigitMarker ? 1 : 0);
            result.rounding.appendDigit((byte) 0, 0, true);
            break;

          case '@':
            seenSignificantDigitMarker = true;
            result.paddingWidth += 1;
            result.groupingSizes[0] += 1;
            result.totalIntegerDigits += 1;
            // no change to result.minimumIntegerDigits
            result.minimumSignificantDigits += 1;
            result.maximumSignificantDigits += 1;
            result.rounding.appendDigit((byte) 0, 0, true);
            break;

          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            // TODO: Crash here if we've seen the significant digit marker? See NumberFormatTestCases.txt
            result.paddingWidth += 1;
            result.groupingSizes[0] += 1;
            result.totalIntegerDigits += 1;
            result.minimumIntegerDigits += 1;
            // no change to result.minimumSignificantDigits
            result.maximumSignificantDigits += (seenSignificantDigitMarker ? 1 : 0);
            result.rounding.appendDigit((byte) (state.peek() - '0'), 0, true);
            break;

          default:
            return;
        }
        state.next(); // consume the symbol
      }
    }

    private static void consumeFractionFormat(ParserState state, SubpatternParseResult result) {
      int zeroCounter = 0;
      while (true) {
        switch (state.peek()) {
          case '#':
            result.paddingWidth += 1;
            // no change to result.minimumFractionDigits
            result.maximumFractionDigits += 1;
            zeroCounter++;
            break;

          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            result.paddingWidth += 1;
            result.minimumFractionDigits += 1;
            result.maximumFractionDigits += 1;
            if (state.peek() == '0') {
              zeroCounter++;
            } else {
              result.rounding.appendDigit((byte) (state.peek() - '0'), zeroCounter, false);
              zeroCounter = 0;
            }
            break;

          default:
            return;
        }
        state.next(); // consume the symbol
      }
    }

    private static void consumeExponent(ParserState state, SubpatternParseResult result) {
      if (state.peek() != 'E') {
        return;
      }
      state.next(); // consume the E
      result.paddingWidth++;
      if (state.peek() == '+') {
        state.next(); // consume the +
        result.exponentShowPlusSign = true;
        result.paddingWidth++;
      }
      while (state.peek() == '0') {
        state.next(); // consume the 0
        result.exponentDigits += 1;
        result.paddingWidth++;
      }
    }
  }
}
