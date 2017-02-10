// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class FormatQuantity3 extends FormatQuantityBCD {

  /**
   * The BCD of the 16 digits of the number represented by this object. Every 4 bits of the long map
   * to one digit. For example, the number "12345" in BCD is "0x12345".
   *
   * <p>Whenever bcd changes internally, {@link #compact()} must be called, except in special cases
   * like setting the digit to zero.
   */
  private byte[] bcd = new byte[100];

  @Override
  public int maxRepresentableDigits() {
    return Integer.MAX_VALUE;
  }

  public FormatQuantity3(long input) {
    readLongToBcd(input);
  }

  public FormatQuantity3(int input) {
    readIntToBcd(input);
  }

  public FormatQuantity3(double input) {
    readDoubleToBcd(input);
  }

  public FormatQuantity3(BigInteger input) {
    readBigIntegerToBcd(input);
  }

  public FormatQuantity3(BigDecimal input) {
    readBigDecimalToBcd(input);
  }

  public FormatQuantity3(FormatQuantity3 other) {
    copyFrom(other);
  }

  @Override
  protected void _copyBcdFrom(FormatQuantity _other) {
    FormatQuantity3 other = (FormatQuantity3) _other;
    System.arraycopy(other.bcd, 0, bcd, 0, bcd.length);
  }

  private void ensureCapacity(int capacity) {
    if (bcd.length >= capacity) return;
    byte[] bcd1 = new byte[capacity * 2];
    System.arraycopy(bcd, 0, bcd1, 0, bcd.length);
    bcd = bcd1;
  }

  @Override
  public byte getDigit(int magnitude) {
    return getDigitPos(magnitude - scale);
  }

  /**
   * Returns a single digit from the BCD list. No internal state is changed by calling this method.
   *
   * @param position The position of the digit to pop, counted in BCD units from the least
   *     significant digit. If the position is outside the range of the BCD byte array, zero is
   *     returned.
   * @return The digit at the specified location.
   */
  private byte getDigitPos(int position) {
    if (position < 0 || position > bcd.length) return 0;
    return bcd[position];
  }

  //////////////////////////////////
  ///// BCD ARITHMETIC METHODS /////
  //////////////////////////////////

  @Override
  protected void setToZero() {
    for (int i = 0; i < precision; i++) {
      bcd[i] = (byte) 0;
    }
    scale = 0;
    precision = 0;
  }

  private static final byte[] LONG_MIN_VALUE =
      new byte[] {8, 0, 8, 5, 7, 7, 4, 5, 8, 6, 3, 0, 2, 7, 3, 3, 2, 2, 9};

  /**
   * Sets the internal BCD state to represent the value in the given long.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readLongToBcd(long n) {
    setToZero();
    if (n == 0) {
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    if (n == Long.MIN_VALUE) {
      // Can't consume via the normal path.
      System.arraycopy(LONG_MIN_VALUE, 0, bcd, 0, LONG_MIN_VALUE.length);
      scale = 0;
      precision = LONG_MIN_VALUE.length;
      return;
    }

    int i = 0;
    for (; n != 0L; n /= 10L, i++) {
      bcd[i] = (byte) (n % 10);
    }
    scale = 0;
    precision = i;
    compact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given int.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readIntToBcd(int n) {
    setToZero();
    if (n == 0) {
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    int i = 0;
    for (; n != 0L; n /= 10L, i++) {
      bcd[i] = (byte) (n % 10);
    }
    scale = 0;
    precision = i;
    compact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigInteger.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readBigIntegerToBcd(BigInteger n) {
    setToZero();
    if (n.signum() == 0) {
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    if (n.bitLength() < 64) {
      readLongToBcd(n.longValueExact());
      return;
    }

    int i = 0;
    for (; n.signum() != 0; i++) {
      BigInteger[] temp = n.divideAndRemainder(BigInteger.TEN);
      ensureCapacity(i + 1);
      bcd[i] = temp[1].byteValue();
      n = temp[0];
    }
    scale = 0;
    precision = i;
    compact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigDecimal.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readBigDecimalToBcd(BigDecimal n) {
    setToZero();
    if (n.signum() == 0) {
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    int fracLength = n.scale();
    n = n.scaleByPowerOfTen(fracLength);
    readBigIntegerToBcd(n.toBigInteger());
    scale -= fracLength;
  }

  /**
   * Returns a long approximating the internal BCD. A long can only represent the integral part of
   * the number.
   *
   * @return A double representation of the internal BCD.
   */
  @Override
  protected long bcdToLong() {
    long result = 0L;
    for (int magnitude = scale + precision - 1; magnitude >= 0; magnitude--) {
      result = result * 10 + getDigitPos(magnitude - scale);
    }
    return result;
  }

  /**
   * This returns a long representing the fraction digits of the number, as required by PluralRules.
   * For example, if we represent the number "1.20" (including optional and required digits), then
   * this function returns "20" if includeTrailingZeros is true or "2" if false.
   */
  @Override
  protected long bcdToFractionLong(boolean includeTrailingZeros) {
    long result = 0L;
    int magnitude = -1;
    for (;
        (magnitude >= scale || (includeTrailingZeros && magnitude >= lReqPos))
            && magnitude >= lOptPos;
        magnitude--) {
      result = result * 10 + getDigitPos(magnitude - scale);
    }
    return result;
  }

  /**
   * Returns a double approximating the internal BCD. The double may not retain all of the
   * information encoded in the BCD if the BCD represents a number out of range of a double.
   *
   * @return A double representation of the internal BCD.
   */
  @Override
  protected double bcdToDouble() {
    long tempLong = 0L;
    int lostDigits = precision - Math.min(precision, 15);
    for (int shift = precision - 1; shift >= lostDigits; shift--) {
      tempLong = tempLong * 10 + getDigitPos(shift);
    }
    double result = tempLong;
    int _scale = scale + lostDigits;
    if (_scale >= 0) {
      int i = _scale;
      for (; i >= 9; i -= 9) result *= 1000000000;
      for (; i >= 3; i -= 3) result *= 1000;
      for (; i >= 1; i -= 1) result *= 10;
    } else {
      int i = _scale;
      for (; i <= -9; i += 9) result /= 1000000000;
      for (; i <= -3; i += 3) result /= 1000;
      for (; i <= -1; i += 1) result /= 10;
    }
    if (isNegative()) result = -result;
    return result;
  }

  /**
   * Returns a BigDecimal encoding the internal BCD value.
   *
   * @return A BigDecimal representation of the internal BCD.
   */
  @Override
  protected BigDecimal bcdToBigDecimal() {
    //    long tempLong = 0L;
    //    for (int shift = (precision - 1); shift >= 0; shift--) {
    //      tempLong = tempLong * 10 + getDigitPos(shift);
    //    }
    //    BigDecimal result = BigDecimal.valueOf(tempLong);
    //    result = result.scaleByPowerOfTen(scale);
    //    if (isNegative()) result = result.negate();
    //    return result;
    return new BigDecimal(toDumbString());
  }

  private String toDumbString() {
    StringBuilder sb = new StringBuilder();
    if (isNegative()) sb.append('-');
    if (precision == 0) {
      sb.append('0');
      return sb.toString();
    }
    for (int i = precision - 1; i >= 0; i--) {
      sb.append(getDigitPos(i));
    }
    if (scale != 0) {
      sb.append('E');
      sb.append(scale);
    }
    return sb.toString();
  }

  @Override
  protected void bcdRoundToMagnitude(int magnitude, RoundingMode roundingMode) {
    // The position in the BCD at which rounding will be performed; digits to the right of position
    // will be rounded away.
    int position = magnitude - scale;

    if (position <= 0) {
      // All digits are to the left of the rounding magnitude.
    } else if (precision == 0) {
      // No rounding for zero.
    } else {
      // Perform rounding logic.
      // "leading" = most significant digit to the right of rounding
      // "trailing" = least significant digit to the left of rounding
      byte leadingDigit = getDigitPos(position - 1);
      byte trailingDigit = getDigitPos(position);

      // Compute which section (lower, half, or upper) of the number we are in
      int section = RoundingUtils.SECTION_MIDPOINT;
      if (leadingDigit < 5) {
        section = RoundingUtils.SECTION_LOWER;
      } else if (leadingDigit > 5) {
        section = RoundingUtils.SECTION_UPPER;
      } else {
        for (int p = position - 2; p >= 0; p--) {
          if (getDigitPos(p) != 0) {
            section = RoundingUtils.SECTION_UPPER;
            break;
          }
        }
      }

      boolean roundDown =
          RoundingUtils.getRoundingDirection(
              (trailingDigit % 2) == 0, isNegative(), section, roundingMode.ordinal(), this);

      // Perform truncation
      if (position >= precision) {
        setToZero();
      } else {
        shiftRight(position);
      }
      scale = magnitude;

      // Bubble the result to the higher digits
      if (!roundDown) {
        if (trailingDigit == 9) {
          int bubblePos = 0;
          for (; getDigitPos(bubblePos) == 9; bubblePos++) {}
          // Note: the most digits BCD can have at this point is 15, so bubblePos <= 15
          shiftRight(bubblePos); // shift off the trailing 9s
        }
        assert getDigitPos(0) != 9;
        bcd[0] += 1; // the addition operation will apply to the trailing digit at position 0
        precision += 1; // in case an extra digit got added
      }
      compact();
    }
  }

  private void shiftRight(int numDigits) {
    int i = 0;
    for (; i < precision - numDigits; i++) {
      bcd[i] = bcd[i + numDigits];
    }
    for (; i < precision; i++) {
      bcd[i] = 0;
    }
    scale += numDigits;
    precision -= numDigits;
  }

  /**
   * Removes trailing zeros from the BCD (adjusting the scale as required) and then recomputes the
   * precision. The precision is the number of digits in the number up through the greatest nonzero
   * digit.
   *
   * <p>This method must always be called when bcd changes in order for assumptions to be correct in
   * methods like {@link #getMagnitude()}.
   */
  private void compact() {
    // Special handling for 0
    boolean isZero = true;
    for (int i = 0; i < precision; i++) {
      if (bcd[i] != 0) {
        isZero = false;
        break;
      }
    }
    if (isZero) {
      scale = 0;
      precision = 0;
      return;
    }

    // Compact the number (remove trailing zeros)
    int delta = 0;
    for (; bcd[delta] == 0; delta++) ;
    shiftRight(delta);

    // Compute precision
    int leading = precision - 1;
    for (; leading >= 0 && bcd[leading] == 0; leading--) ;
    precision = leading + 1;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 30; i >= 0; i--) {
      sb.append(bcd[i]);
    }
    return String.format(
        "<FormatQuantity3 %s:%d:%d:%s %s%s%d>",
        (lOptPos > 1000 ? "max" : String.valueOf(lOptPos)),
        lReqPos,
        rReqPos,
        (rOptPos < -1000 ? "min" : String.valueOf(rOptPos)),
        sb,
        "E",
        scale);
  }
}
