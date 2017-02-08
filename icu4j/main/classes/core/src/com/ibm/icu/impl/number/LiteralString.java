// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.text.ParseException;

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

    // Fail silently if string is malformed.
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
   * @throws ParseException
   */
  public static void unescape(
      CharSequence literalString,
      DecimalFormatSymbols symbols,
      String currency1,
      String currency2,
      String currency3,
      String minusSign,
      NumberStringBuilder sb1)
      throws ParseException {
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

    // Resolve final state
    switch (state) {
      case AFTER_QUOTE:
      case BASE:
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
      default:
        // TODO: Should this fail silently instead?
        throw new ParseException(
            "Ended in unexpected state while parsing literal: " + literalString,
            literalString.length());
    }
  }
}
