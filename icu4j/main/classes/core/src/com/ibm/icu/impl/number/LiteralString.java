// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.NumberFormat.Field;

/**
 * Interpolates locale data into a literal string. Examples of literal strings include the prefix
 * and affix strings associated with a decimal format pattern.
 *
 * <table>
 * <tr><th>Literal String</th><th>Example Formatted String</th></tr>
 * <tr><td>abc</td><td>abc</td></tr>
 * <tr><td>ab-</td><td>ab−</td></tr>
 * <tr><td>ab'-'</td><td>ab-</td></tr>
 * <tr><td>ab''</td><td>ab'</td></tr>
 * </table>
 */
public class LiteralString {
  enum State {
    BASE,
    FIRST_QUOTE,
    INSIDE_QUOTE,
    AFTER_QUOTE,
    FIRST_CURR,
    SECOND_CURR,
    THIRD_CURR,
    REWIND_TO_BASE
  }

  /**
   * Checks whether the specified literal string has any unquoted currency symbols ("¤").
   *
   * @param literalString
   * @return True if the literal has at least one unquoted currency symbol; false otherwise.
   */
  public static boolean hasCurrencySymbols(CharSequence literalString) {
    if (literalString == null) return false;
    int offset = 0;
    State state = State.BASE;
    for (; offset < literalString.length(); ) {
      int cp = Character.codePointAt(literalString, offset);
      switch (state) {
        case BASE:
          if (cp == '¤') {
            return true;
          } else if (cp == '\'') {
            state = State.INSIDE_QUOTE;
          }
          break;
        case INSIDE_QUOTE:
          if (cp == '\'') {
            state = State.BASE;
          }
          break;
        default:
          throw new AssertionError();
      }
      offset += Character.charCount(cp);
    }
    return false;
  }

  /**
   * Estimates the number of code points present in an unescaped version of the literal string (one
   * that would be returned by {@link #unescape}), assuming that all interpolated symbols consume
   * one code point. Used for computing padding width.
   *
   * @param literalString The original string whose width will be estimated.
   * @return The length of the unescaped string.
   */
  public static int unescapedLength(CharSequence literalString) {
    if (literalString == null) return 0;
    State state = State.BASE;
    int offset = 0;
    int length = 0;
    for (; offset < literalString.length(); ) {
      int cp = Character.codePointAt(literalString, offset);

      switch (state) {
        case BASE:
          if (cp == '\'') {
            // First quote
            state = State.FIRST_QUOTE;
          } else {
            // Unquoted symbol
            length++;
          }
          break;
        case FIRST_QUOTE:
          if (cp == '\'') {
            // Repeated quote
            length++;
            state = State.BASE;
          } else {
            // Quoted code point
            length++;
            state = State.INSIDE_QUOTE;
          }
          break;
        case INSIDE_QUOTE:
          if (cp == '\'') {
            // End of quoted sequence
            state = State.AFTER_QUOTE;
          } else {
            // Quoted code point
            length++;
          }
          break;
        case AFTER_QUOTE:
          if (cp == '\'') {
            // Double quote inside of quoted sequence
            length++;
            state = State.INSIDE_QUOTE;
          } else {
            // Unquoted symbol
            length++;
          }
          break;
        default:
          throw new AssertionError();
      }

      offset += Character.charCount(cp);
    }

    switch (state) {
      case FIRST_QUOTE:
        // Treat a terminating start-quote as a quote
        length++;
        break;
      default:
        break;
    }

    return length;
  }

  /**
   * Executes the unescape state machine. Replaces the unquoted characters "-", "%", and "‰" with
   * their localized equivalents. Replaces "¤", "¤¤", and "¤¤¤" with the three argument strings.
   *
   * @param literalString The original string to be unescaped.
   * @param symbols An instance of {@link DecimalFormatSymbols} for the locale of interest.
   * @param currency1 The string to replace "¤".
   * @param currency2 The string to replace "¤¤".
   * @param currency3 The string to replace "¤¤¤".
   * @param minusSign The string to replace "-". If null, symbols.getMinusSignString() is used.
   * @param sb1 The {@link NumberStringBuilder} to which the result will be appended.
   */
  public static void unescape(
      CharSequence literalString,
      DecimalFormatSymbols symbols,
      String currency1,
      String currency2,
      String currency3,
      String minusSign,
      NumberStringBuilder sb1) {
    if (literalString == null) return;
    if (minusSign == null) minusSign = symbols.getMinusSignString();
    State state = State.BASE;
    int offset = 0;
    for (; offset < literalString.length(); ) {
      int cp = Character.codePointAt(literalString, offset);

      String strToAppend = null;
      int cpToAppend = -1;
      Field fieldToAppend = null;

      switch (state) {
        case BASE:
          if (cp == '\'') {
            state = State.FIRST_QUOTE;
          } else if (cp == '-' && symbols != null) {
            strToAppend = minusSign;
            fieldToAppend = Field.SIGN;
          } else if (cp == '+' && symbols != null) {
            strToAppend = symbols.getPlusSignString();
            fieldToAppend = Field.SIGN;
          } else if (cp == '%' && symbols != null) {
            strToAppend = symbols.getPercentString();
            fieldToAppend = Field.PERCENT;
          } else if (cp == '‰' && symbols != null) {
            strToAppend = symbols.getPerMillString();
            fieldToAppend = Field.PERMILLE;
          } else if (cp == '¤' && symbols != null) {
            state = State.FIRST_CURR;
          } else {
            cpToAppend = cp;
          }
          break;
        case FIRST_QUOTE:
          if (cp == '\'') {
            cpToAppend = '\'';
            state = State.BASE;
          } else {
            cpToAppend = cp;
            state = State.INSIDE_QUOTE;
          }
          break;
        case INSIDE_QUOTE:
          if (cp == '\'') {
            state = State.AFTER_QUOTE;
          } else {
            cpToAppend = cp;
          }
          break;
        case AFTER_QUOTE:
          if (cp == '\'') {
            cpToAppend = '\'';
            state = State.INSIDE_QUOTE;
            break;
          } else {
            state = State.REWIND_TO_BASE;
            break;
          }
        case FIRST_CURR:
          if (cp == '¤') {
            state = State.SECOND_CURR;
          } else {
            strToAppend = currency1;
            fieldToAppend = Field.CURRENCY;
            state = State.REWIND_TO_BASE;
          }
          break;
        case SECOND_CURR:
          if (cp == '¤') {
            state = State.THIRD_CURR;
          } else {
            strToAppend = currency2;
            fieldToAppend = Field.CURRENCY;
            state = State.REWIND_TO_BASE;
          }
          break;
        case THIRD_CURR:
          strToAppend = currency3;
          state = State.REWIND_TO_BASE;
          break;
        default:
          throw new AssertionError();
      }

      if (strToAppend != null) {
        sb1.append(strToAppend, fieldToAppend);
      } else if (cpToAppend != -1) {
        sb1.appendCodePoint(cpToAppend, fieldToAppend);
      }

      // The state REWIND_TO_BASE means that we should look at the current character again
      if (state == State.REWIND_TO_BASE) {
        state = State.BASE;
      } else {
        offset += Character.charCount(cp);
      }
    }

    // End of string
    switch (state) {
      case BASE:
        // Last character was fully handled.
        break;
      case FIRST_QUOTE:
      case INSIDE_QUOTE:
        // For consistent behavior with the JDK and ICU 58, throw an exception here.
        throw new IllegalArgumentException("Unterminated quote: \"" + literalString + "\"");
      case AFTER_QUOTE:
        // Last character was a closing quote.
        break;
      case FIRST_CURR:
        sb1.append(currency1, Field.CURRENCY);
        break;
      case SECOND_CURR:
        sb1.append(currency2, Field.CURRENCY);
        break;
      case THIRD_CURR:
        sb1.append(currency3, Field.CURRENCY);
        break;
    }
  }

  private static final ThreadLocal<LiteralStringStepwiseParser> threadLocalStepParser =
      new ThreadLocal<LiteralStringStepwiseParser>() {
        @Override
        protected LiteralStringStepwiseParser initialValue() {
          return new LiteralStringStepwiseParser();
        }
      };

  public static void unescape2(
      CharSequence literalString,
      DecimalFormatSymbols symbols,
      String currency1,
      String currency2,
      String currency3,
      String minusSign,
      NumberStringBuilder output) {
    if (literalString == null || literalString.length() == 0) return;
    LiteralStringStepwiseParser parser = threadLocalStepParser.get();
    parser.reset(literalString);
    while (parser.step0()) {
      switch (parser.getType()) {
        case MINUS_SIGN:
          output.append(minusSign, Field.SIGN);
          break;
        case PLUS_SIGN:
          output.append(symbols.getPlusSignString(), Field.SIGN);
          break;
        case PERCENT:
          output.append(symbols.getPercentString(), Field.PERCENT);
          break;
        case PERMILLE:
          output.append(symbols.getPerMillString(), Field.PERMILLE);
          break;
        case CURRENCY_SINGLE:
          output.append(currency1, Field.CURRENCY);
          break;
        case CURRENCY_DOUBLE:
          output.append(currency2, Field.CURRENCY);
          break;
        case CURRENCY_TRIPLE:
          output.append(currency3, Field.CURRENCY);
          break;
        case NONE:
          output.appendCodePoint(parser.getCodePoint(), null);
          break;
      }
    }
  }

  public static void unescape3(
      CharSequence literalString,
      DecimalFormatSymbols symbols,
      String currency1,
      String currency2,
      String currency3,
      String minusSign,
      NumberStringBuilder output) {
    if (literalString == null || literalString.length() == 0) return;
    long tag = 0L;
    while (true) {
      tag = LiteralStringStepwiseParser.step(tag, literalString);
      if (tag == -1L) break;
      int typeOrCp = LiteralStringStepwiseParser.getTypeOrCp(tag);
      switch (typeOrCp) {
        case LiteralStringStepwiseParser.INT_TYPE_MINUS_SIGN:
          output.append(minusSign, Field.SIGN);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_PLUS_SIGN:
          output.append(symbols.getPlusSignString(), Field.SIGN);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_PERCENT:
          output.append(symbols.getPercentString(), Field.PERCENT);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_PERMILLE:
          output.append(symbols.getPerMillString(), Field.PERMILLE);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_CURRENCY_SINGLE:
          output.append(currency1, Field.CURRENCY);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_CURRENCY_DOUBLE:
          output.append(currency2, Field.CURRENCY);
          break;
        case LiteralStringStepwiseParser.INT_TYPE_CURRENCY_TRIPLE:
          output.append(currency3, Field.CURRENCY);
          break;
        default:
          output.appendCodePoint(typeOrCp, null);
          break;
      }
    }
  }

  /**
   * An enum that roughly corresponds to {@link Field}, but adds a distinction between the two types
   * of signs and three types of currencies.
   */
  enum SymbolType {
    NONE,
    MINUS_SIGN,
    PLUS_SIGN,
    PERCENT,
    PERMILLE,
    CURRENCY_SINGLE,
    CURRENCY_DOUBLE,
    CURRENCY_TRIPLE
  }

  static class LiteralStringStepwiseParser {
    static final int INT_STATE_BASE = 0;
    static final int INT_STATE_FIRST_QUOTE = 1;
    static final int INT_STATE_INSIDE_QUOTE = 2;
    static final int INT_STATE_AFTER_QUOTE = 3;
    static final int INT_STATE_FIRST_CURR = 4;
    static final int INT_STATE_SECOND_CURR = 5;
    static final int INT_TYPE_NONE = 0;
    static final int INT_TYPE_MINUS_SIGN = -1;
    static final int INT_TYPE_PLUS_SIGN = -2;
    static final int INT_TYPE_PERCENT = -3;
    static final int INT_TYPE_PERMILLE = -4;
    static final int INT_TYPE_CURRENCY_SINGLE = -5;
    static final int INT_TYPE_CURRENCY_DOUBLE = -6;
    static final int INT_TYPE_CURRENCY_TRIPLE = -7;

    // string, offset and state are used internally.
    private CharSequence string;
    private int offset;
    private State state;

    // type and cp are for external consumption.
    private SymbolType type;
    private int cp;

    public LiteralStringStepwiseParser() {
      reset("");
    }

    public LiteralStringStepwiseParser(CharSequence literalString) {
      reset(literalString);
    }

    public LiteralStringStepwiseParser reset(CharSequence literalString) {
      assert literalString != null;
      string = literalString;
      offset = 0;
      state = State.BASE;
      type = null;
      cp = -1;
      return this;
    }

    /**
     * Gets the type of the most recently consumed symbol. If null, call {@link #getCodePoint} to
     * get a code point instead.
     */
    public SymbolType getType() {
      return type;
    }

    /**
     * Gets the most recently consumed code point. Only valid if the result of {@link #getType} is
     * null.
     */
    public int getCodePoint() {
      return cp;
    }

    /**
     * Take another step through the pattern.
     *
     * @return Whether this step succeeded at consuming a character.
     */
    public boolean step0() {
      if (offset >= string.length()) return false;

      //      offset += 1;
      //      setResultCP('x');
      //      return true;

      outer:
      for (; offset < string.length(); ) {
        int cp = Character.codePointAt(string, offset);
        int count = Character.charCount(cp);

        switch (state) {
          case BASE:
            switch (cp) {
              case '\'':
                state = State.FIRST_QUOTE;
                offset += count;
                // continue to the next code point
                break;
              case '-':
                offset += count;
                setResultSymbol(SymbolType.MINUS_SIGN);
                break outer;
              case '+':
                offset += count;
                setResultSymbol(SymbolType.PLUS_SIGN);
                break outer;
              case '%':
                offset += count;
                setResultSymbol(SymbolType.PERCENT);
                break outer;
              case '‰':
                offset += count;
                setResultSymbol(SymbolType.PERMILLE);
                break outer;
              case '¤':
                state = State.FIRST_CURR;
                offset += count;
                // continue to the next code point
                break;
              default:
                offset += count;
                setResultCP(cp);
                break outer;
            }
            break;
          case FIRST_QUOTE:
            if (cp == '\'') {
              state = State.BASE;
              offset += count;
              setResultCP('\'');
              break outer;
            } else {
              state = State.INSIDE_QUOTE;
              offset += count;
              setResultCP(cp);
              break outer;
            }
          case INSIDE_QUOTE:
            if (cp == '\'') {
              state = State.AFTER_QUOTE;
              offset += count;
              // continue to the next code point
              break;
            } else {
              offset += count;
              setResultCP(cp);
              break outer;
            }
          case AFTER_QUOTE:
            if (cp == '\'') {
              state = State.INSIDE_QUOTE;
              offset += count;
              setResultCP('\'');
              break outer;
            } else {
              state = State.BASE;
              // re-evaluate this code point
              break;
            }
          case FIRST_CURR:
            if (cp == '¤') {
              state = State.SECOND_CURR;
              offset += count;
              // continue to the next code point
              break;
            } else {
              state = State.BASE;
              setResultSymbol(SymbolType.CURRENCY_SINGLE);
              break outer;
            }
          case SECOND_CURR:
            if (cp == '¤') {
              state = State.BASE;
              offset += count;
              setResultSymbol(SymbolType.CURRENCY_TRIPLE);
              break outer;
            } else {
              state = State.BASE;
              setResultSymbol(SymbolType.CURRENCY_DOUBLE);
              break outer;
            }
          default:
            throw new AssertionError();
        }
      }

      // End of string
      switch (state) {
        case BASE:
          // We shouldn't get here.
          throw new AssertionError();
        case FIRST_QUOTE:
        case INSIDE_QUOTE:
          // For consistent behavior with the JDK and ICU 58, throw an exception here.
          throw new IllegalArgumentException("Unterminated quote: \"" + string + "\"");
        case AFTER_QUOTE:
          // Last character was a closing quote.
          return false;
        case FIRST_CURR:
          setResultSymbol(SymbolType.CURRENCY_SINGLE);
          break;
        case SECOND_CURR:
          setResultSymbol(SymbolType.CURRENCY_DOUBLE);
          break;
      }

      return true;
    }

    /**
     * Take another step through the pattern.
     *
     * @return Whether this step succeeded at consuming a character.
     */
    public static long step(long tag, CharSequence string) {
      int offset = getOffset(tag);
      int state = getState(tag);
      for (; offset < string.length(); ) {
        int cp = Character.codePointAt(string, offset);
        int count = Character.charCount(cp);

        switch (state) {
          case INT_STATE_BASE:
            switch (cp) {
              case '\'':
                state = INT_STATE_FIRST_QUOTE;
                offset += count;
                // continue to the next code point
                break;
              case '-':
                return makeTag(offset + count, INT_TYPE_MINUS_SIGN, INT_STATE_BASE, 0);
              case '+':
                return makeTag(offset + count, INT_TYPE_PLUS_SIGN, INT_STATE_BASE, 0);
              case '%':
                return makeTag(offset + count, INT_TYPE_PERCENT, INT_STATE_BASE, 0);
              case '‰':
                return makeTag(offset + count, INT_TYPE_PERMILLE, INT_STATE_BASE, 0);
              case '¤':
                state = INT_STATE_FIRST_CURR;
                offset += count;
                // continue to the next code point
                break;
              default:
                return makeTag(offset + count, INT_TYPE_NONE, INT_STATE_BASE, cp);
            }
            break;
          case INT_STATE_FIRST_QUOTE:
            if (cp == '\'') {
              return makeTag(offset + count, INT_TYPE_NONE, INT_STATE_BASE, cp);
            } else {
              return makeTag(offset + count, INT_TYPE_NONE, INT_STATE_INSIDE_QUOTE, cp);
            }
          case INT_STATE_INSIDE_QUOTE:
            if (cp == '\'') {
              state = INT_STATE_AFTER_QUOTE;
              offset += count;
              // continue to the next code point
              break;
            } else {
              return makeTag(offset + count, INT_TYPE_NONE, INT_STATE_INSIDE_QUOTE, cp);
            }
          case INT_STATE_AFTER_QUOTE:
            if (cp == '\'') {
              return makeTag(offset + count, INT_TYPE_NONE, INT_STATE_INSIDE_QUOTE, cp);
            } else {
              state = INT_STATE_BASE;
              // re-evaluate this code point
              break;
            }
          case INT_STATE_FIRST_CURR:
            if (cp == '¤') {
              state = INT_STATE_SECOND_CURR;
              offset += count;
              // continue to the next code point
              break;
            } else {
              return makeTag(offset, INT_TYPE_CURRENCY_SINGLE, INT_STATE_BASE, 0);
            }
          case INT_STATE_SECOND_CURR:
            if (cp == '¤') {
              return makeTag(offset + count, INT_TYPE_CURRENCY_TRIPLE, INT_STATE_BASE, 0);
            } else {
              return makeTag(offset, INT_TYPE_CURRENCY_DOUBLE, INT_STATE_BASE, 0);
            }
          default:
            throw new AssertionError();
        }
      }
      // End of string
      switch (state) {
        case INT_STATE_BASE:
          // We shouldn't get here.
          throw new AssertionError();
        case INT_STATE_FIRST_QUOTE:
        case INT_STATE_INSIDE_QUOTE:
          // For consistent behavior with the JDK and ICU 58, throw an exception here.
          throw new IllegalArgumentException(
              "Unterminated quote in pattern affix: \"" + string + "\"");
        case INT_STATE_AFTER_QUOTE:
          // We shouldn't get here if hasNext() was followed.
          throw new AssertionError();
        case INT_STATE_FIRST_CURR:
          return makeTag(offset, INT_TYPE_CURRENCY_SINGLE, INT_STATE_BASE, 0);
        case INT_STATE_SECOND_CURR:
          return makeTag(offset, INT_TYPE_CURRENCY_DOUBLE, INT_STATE_BASE, 0);
        default:
          throw new AssertionError();
      }
    }

    private void setResultCP(int cp) {
      this.type = SymbolType.NONE;
      this.cp = cp;
    }

    private void setResultSymbol(SymbolType type) {
      this.type = type;
      this.cp = -1;
    }

    private static long makeTag(int offset, int type, int state, int cp) {
      long tag = 0L;
      tag |= offset;
      tag |= (-(long) type) << 32;
      tag |= ((long) state) << 36;
      tag |= ((long) cp) << 40;
      return tag;
    }

    static int getTypeOrCp(long tag) {
      int type = (int) ((tag >> 32) & 0xf);
      return (type == 0) ? (int) (tag >> 40) : -type;
    }

    static boolean hasNext(long tag, CharSequence string) {
      int state = getState(tag);
      int offset = getOffset(tag);
      // Special case: the last character in string is an end quote.
      if (state == INT_STATE_INSIDE_QUOTE
          && offset == string.length() - 1
          && string.charAt(offset) == '\'') {
        return false;
      } else if (state != INT_STATE_BASE) {
        return true;
      } else {
        return offset < string.length();
      }
    }

    private static int getOffset(long tag) {
      return (int) (tag & 0xffffffff);
    }

    private static int getState(long tag) {
      return (int) ((tag >> 36) & 0xf);
    }
  }
}
