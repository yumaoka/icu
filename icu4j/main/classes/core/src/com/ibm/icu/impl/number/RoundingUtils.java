// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.RoundingMode;

import com.ibm.icu.math.MathContext;

/** @author sffc */
public class RoundingUtils {

  public static final int SECTION_LOWER = 0;
  public static final int SECTION_MIDPOINT = 1;
  public static final int SECTION_UPPER = 2;

  /**
   * Converts a rounding mode and metadata about the quantity being rounded to a boolean determining
   * whether the value should be rounded toward infinity or toward zero.
   *
   * <p>The parameters are of type int because benchmarks on an x86-64 processor against OpenJDK
   * showed that ints were demonstrably faster than enums in switch statements.
   *
   * @param isEven Whether the digit immediately before the rounding magnitude is even.
   * @param isNegative Whether the quantity is negative.
   * @param section Whether the part of the quantity to the right of the rounding magnitude is
   *     exactly halfway between two digits, whether it is in the lower part (closer to zero), or
   *     whether it is in the upper part (closer to infinity). See {@link #SECTION_LOWER}, {@link
   *     #SECTION_MIDPOINT}, and {@link #SECTION_UPPER}.
   * @param roundingMode The integer version of the {@link RoundingMode}, which you can get via
   *     {@link RoundingMode#ordinal}.
   * @param reference A reference object to be used when throwing an ArithmeticException.
   * @return true if the number should be rounded toward zero; false if it should be rounded toward
   *     infinity.
   */
  public static boolean getRoundingDirection(
      boolean isEven, boolean isNegative, int section, int roundingMode, Object reference) {
    switch (roundingMode) {
      case MathContext.ROUND_UP:
        return false;

      case MathContext.ROUND_DOWN:
        return true;

      case MathContext.ROUND_CEILING:
        return isNegative;

      case MathContext.ROUND_FLOOR:
        return !isNegative;

      case MathContext.ROUND_HALF_UP:
        switch (section) {
          case SECTION_MIDPOINT:
            return false;
          case SECTION_LOWER:
            return true;
          case SECTION_UPPER:
            return false;
        }
        break;

      case MathContext.ROUND_HALF_DOWN:
        switch (section) {
          case SECTION_MIDPOINT:
            return true;
          case SECTION_LOWER:
            return true;
          case SECTION_UPPER:
            return false;
        }
        break;

      case MathContext.ROUND_HALF_EVEN:
        switch (section) {
          case SECTION_MIDPOINT:
            return isEven;
          case SECTION_LOWER:
            return true;
          case SECTION_UPPER:
            return false;
        }
        break;
    }

    // Rounding mode UNNECESSARY
    throw new ArithmeticException("Rounding is required on " + reference.toString());
  }
}
