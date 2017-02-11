// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.Operand;

/**
 * Represents numbers and digit display properties using Binary Coded Decimal (BCD).
 *
 * @implements {@link FormatQuantity}
 */
public abstract class FormatQuantityBCD implements FormatQuantity {

  /**
   * The power of ten corresponding to the least significant digit in the BCD. For example, if this
   * object represents the number "3.14", the BCD will be "0x314" and the scale will be -2.
   *
   * <p>Note that in {@link java.math.BigDecimal}, the scale is defined differently: the number of
   * digits after the decimal place, which is the negative of our definition of scale.
   */
  protected int scale;

  /**
   * The number of digits in the BCD. For example, "1007" has BCD "0x1007" and precision 4. The
   * maximum precision is 16 since a long can hold only 16 digits.
   *
   * <p>This value must be re-calculated whenever the value in bcd changes by using {@link
   * #computePrecisionAndCompact()}.
   */
  protected int precision;

  /**
   * A bitmask of properties relating to the number represented by this object.
   *
   * @see #NEGATIVE_FLAG
   * @see #INFINITY_FLAG
   * @see #NAN_FLAG
   */
  protected int flags;

  protected static final int NEGATIVE_FLAG = 1;
  protected static final int INFINITY_FLAG = 2;
  protected static final int NAN_FLAG = 4;

  // Four positions: left optional '(', left required '[', right required ']', right optional ')'.
  // These four positions determine which digits are displayed in the output string.  They do NOT
  // affect rounding.  These positions are internal-only and can be specified only by the public
  // endpoints like setFractionLength, setIntegerLength, and setSignificantDigits, among others.
  //
  //   * Digits between lReqPos and rReqPos are in the "required zone" and are always displayed.
  //   * Digits between lOptPos and rOptPos but outside the required zone are in the "optional zone"
  //     and are displayed unless they are trailing off the left or right edge of the number and
  //     have a numerical value of zero.  In order to be "trailing", the digits need to be beyond
  //     the decimal point in their respective directions.
  //   * Digits outside of the "optional zone" are never displayed.
  //
  // See the table below for illustrative examples.
  //
  // +---------+---------+---------+---------+------------+------------------------+--------------+
  // | lOptPos | lReqPos | rReqPos | rOptPos |   number   |        positions       | en-US string |
  // +---------+---------+---------+---------+------------+------------------------+--------------+
  // |    5    |    2    |   -1    |   -5    |   1234.567 |     ( 12[34.5]67  )    |   1,234.567  |
  // |    3    |    2    |   -1    |   -5    |   1234.567 |      1(2[34.5]67  )    |     234.567  |
  // |    3    |    2    |   -1    |   -2    |   1234.567 |      1(2[34.5]6)7      |     234.56   |
  // |    6    |    4    |    2    |   -5    | 123456789. |  123(45[67]89.     )   | 456,789.     |
  // |    6    |    4    |    2    |    1    | 123456789. |     123(45[67]8)9.     | 456,780.     |
  // |   -1    |   -1    |   -3    |   -4    | 0.123456   |     0.1([23]4)56       |        .0234 |
  // |    6    |    4    |   -2    |   -2    |     12.3   |     (  [  12.3 ])      |    0012.30   |
  // +---------+---------+---------+---------+------------+------------------------+--------------+
  //
  protected int lOptPos = Integer.MAX_VALUE;
  protected int lReqPos = 0;
  protected int rReqPos = 0;
  protected int rOptPos = Integer.MIN_VALUE;

  @Override
  public void copyFrom(FormatQuantity _other) {
    copyBcdFrom(_other);
    FormatQuantityBCD other = (FormatQuantityBCD) _other;
    lOptPos = other.lOptPos;
    lReqPos = other.lReqPos;
    rReqPos = other.rReqPos;
    rOptPos = other.rOptPos;
    scale = other.scale;
    precision = other.precision;
    flags = other.flags;
  }

  @Override
  public void setIntegerFractionLength(int minInt, int maxInt, int minFrac, int maxFrac) {
    // Graceful failures for bogus input
    minInt = Math.max(0, minInt);
    maxInt = Math.max(0, maxInt);
    minFrac = Math.max(0, minFrac);
    maxFrac = Math.max(0, maxFrac);

    // The minima must be less than or equal to the maxima
    if (maxInt < minInt) {
      minInt = maxInt;
    }
    if (maxFrac < minFrac) {
      minFrac = maxFrac;
    }

    // Displaying neither integer nor fraction digits is not allowed
    if (maxInt == 0 && maxFrac == 0) {
      maxInt = Integer.MAX_VALUE;
      maxFrac = Integer.MAX_VALUE;
    }

    // Save values into internal state
    // Negation is safe for minFrac/maxFrac because -Integer.MAX_VALUE > Integer.MIN_VALUE
    lOptPos = maxInt;
    lReqPos = minInt;
    rReqPos = -minFrac;
    rOptPos = -maxFrac;
  }

  @Override
  public long getPositionFingerprint() {
    long fingerprint = 0;
    fingerprint ^= lOptPos;
    fingerprint ^= (lReqPos << 16);
    fingerprint ^= (rReqPos << 32);
    fingerprint ^= (rOptPos << 48);
    return fingerprint;
  }

  @Override
  public void roundToInterval(BigDecimal roundingInterval, RoundingMode roundingMode) {
    // TODO: Avoid converting back and forth to BigDecimal.
    BigDecimal temp = bcdToBigDecimal();
    temp = temp.divide(roundingInterval, 0, roundingMode).multiply(roundingInterval);
    setToBigDecimal(temp);
  }

  @Override
  public void multiplyBy(BigDecimal multiplicand) {
    // TODO: Perform the multiplication in BCD space.
    // FIXME: This can push the FormatQuantity2 out of range.
    BigDecimal temp = bcdToBigDecimal();
    temp = temp.multiply(multiplicand);
    flags = 0;
    setToBigDecimal(temp);
  }

  @Override
  public int getMagnitude() throws ArithmeticException {
    if (precision == 0) {
      throw new ArithmeticException("Magnitude is not well-defined for zero");
    } else {
      return scale + precision - 1;
    }
  }

  @Override
  public void adjustMagnitude(int delta) {
    scale += delta;
  }

  @Override
  public StandardPlural getStandardPlural(PluralRules rules) {
    if (rules == null) {
      // Fail gracefully if the user didn't provide a PluralRules
      return StandardPlural.OTHER;
    } else {
      @SuppressWarnings("deprecation")
      String ruleString = rules.select(this);
      return StandardPlural.orOtherFromString(ruleString);
    }
  }

  @Override
  public double getPluralOperand(Operand operand) {
    switch (operand) {
      case i:
        return toLong();
      case f:
        return toFractionLong(true);
      case t:
        return toFractionLong(false);
      case v:
        return fractionCount();
      case w:
        return fractionCountWithoutTrailingZeros();
      default:
        return Math.abs(toDouble());
    }
  }

  @Override
  public int getUpperDisplayMagnitude() {
    int magnitude = scale + precision;
    int result = (lReqPos > magnitude) ? lReqPos : (lOptPos < magnitude) ? lOptPos : magnitude;
    return result - 1;
  }

  @Override
  public int getLowerDisplayMagnitude() {
    int magnitude = scale;
    int result = (rReqPos < magnitude) ? rReqPos : (rOptPos > magnitude) ? rOptPos : magnitude;
    return result;
  }

  @Override
  public byte getDigit(int magnitude) {
    return getDigitPos(magnitude - scale);
  }

  private int fractionCount() {
    return -getLowerDisplayMagnitude();
  }

  private int fractionCountWithoutTrailingZeros() {
    return Math.max(-scale, 0);
  }

  @Override
  public boolean isNegative() {
    return (flags & NEGATIVE_FLAG) != 0;
  }

  @Override
  public boolean isInfinite() {
    return (flags & INFINITY_FLAG) != 0;
  }

  @Override
  public boolean isNaN() {
    return (flags & NAN_FLAG) != 0;
  }

  @Override
  public boolean isZero() {
    return precision == 0;
  }

  @Override
  public FormatQuantity clone() {
    if (this instanceof FormatQuantity2) {
      return new FormatQuantity2((FormatQuantity2) this);
    } else if (this instanceof FormatQuantity3) {
      return new FormatQuantity3((FormatQuantity3) this);
    } else if (this instanceof FormatQuantity4) {
      return new FormatQuantity4((FormatQuantity4) this);
    } else {
      throw new IllegalArgumentException("Don't know how to clone " + this.getClass());
    }
  }

  protected void setToInt(int n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    if (n == Integer.MIN_VALUE) {
      readLongToBcd(-(long) n);
    } else {
      readIntToBcd(n);
    }

    compact();
  }

  protected void setToLong(long n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    if (n == Long.MIN_VALUE) {
      readBigIntegerToBcd(BigInteger.valueOf(n).negate());
    } else if (n <= Integer.MAX_VALUE) {
      readIntToBcd((int) n);
    } else {
      readLongToBcd(n);
    }

    compact();
  }

  protected void setToBigInteger(BigInteger n) {
    if (n.signum() == 0) {
      setToZero();
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    if (n.bitLength() < 32) {
      readIntToBcd(n.intValueExact());
    } else if (n.bitLength() < 64) {
      readLongToBcd(n.longValueExact());
    } else {
      readBigIntegerToBcd(n);
    }

    compact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given double.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  protected void setToDouble(double n) {
    if (Double.compare(n, 0.0) < 0) {
      // Double.compare() handles +0.0 vs -0.0
      flags |= NEGATIVE_FLAG;
      n = -n;
    }
    if (n == 0) {
      setToZero();
      return;
    } else if (Double.isNaN(n)) {
      flags |= NAN_FLAG;
      return;
    } else if (Double.isInfinite(n)) {
      flags |= INFINITY_FLAG;
      return;
    }

    long ieeeBits = Double.doubleToLongBits(n);
    int exponent = (int) ((ieeeBits & 0x7ff0000000000000L) >> 52) - 0x3ff;
    int fracLength = (int) ((52 - exponent) / 3.32192809489);
    if (fracLength >= 0) {
      int i = fracLength;
      for (; i >= 9; i -= 9) n *= 1000000000;
      for (; i >= 3; i -= 3) n *= 1000;
      for (; i >= 1; i -= 1) n *= 10;
    } else {
      int i = fracLength;
      for (; i <= -9; i += 9) n /= 1000000000;
      for (; i <= -3; i += 3) n /= 1000;
      for (; i <= -1; i += 1) n /= 10;
    }
    setToLong(Math.round(n));
    scale -= fracLength;
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigDecimal.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  protected void setToBigDecimal(BigDecimal n) {
    setToZero();
    if (n.signum() == 0) {
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    int fracLength = n.scale();
    n = n.scaleByPowerOfTen(fracLength);
    setToBigInteger(n.toBigInteger());
    scale -= fracLength;
  }

  /**
   * Returns a long approximating the internal BCD. A long can only represent the integral part of
   * the number.
   *
   * @return A double representation of the internal BCD.
   */
  protected long toLong() {
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
  protected long toFractionLong(boolean includeTrailingZeros) {
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
  public double toDouble() {
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

  @Override
  public BigDecimal toBigDecimal() {
    return bcdToBigDecimal();
  }

  @Override
  public void roundToMagnitude(int magnitude, RoundingMode roundingMode) {
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
          // Note: in the long implementation, the most digits BCD can have at this point is 15,
          // so bubblePos <= 15 and getDigitPos(bubblePos) is safe.
          for (; getDigitPos(bubblePos) == 9; bubblePos++) {}
          shiftRight(bubblePos); // shift off the trailing 9s
        }
        byte digit0 = getDigitPos(0);
        assert digit0 != 9;
        setDigitPos(0, (byte) (digit0 + 1));
        precision += 1; // in case an extra digit got added
      }
      compact();
    }
  }

  /**
   * Returns a single digit from the BCD list. No internal state is changed by calling this method.
   *
   * @param position The position of the digit to pop, counted in BCD units from the least
   *     significant digit. If outside the range supported by the implementation, zero is returned.
   * @return The digit at the specified location.
   */
  protected abstract byte getDigitPos(int position);

  /**
   * Sets the digit in the BCD list. This method only sets the digit; it is the caller's
   * responsibility to call {@link #compact} after setting the digit.
   *
   * @param position The position of the digit to pop, counted in BCD units from the least
   *     significant digit. If outside the range supported by the implementation, an AssertionError
   *     is thrown.
   * @param value The digit to set at the specified location.
   */
  protected abstract void setDigitPos(int position, byte value);

  protected abstract void shiftRight(int numDigits);

  protected abstract void setToZero();

  /**
   * Sets the internal BCD state to represent the value in the given int. The int is guaranteed to
   * be either positive. The internal state is guaranteed to be empty when this method is called.
   *
   * @param n The value to consume.
   */
  protected abstract void readIntToBcd(int input);

  /**
   * Sets the internal BCD state to represent the value in the given long. The long is guaranteed to
   * be either positive. The internal state is guaranteed to be empty when this method is called.
   *
   * @param n The value to consume.
   */
  protected abstract void readLongToBcd(long input);

  /**
   * Sets the internal BCD state to represent the value in the given BigInteger. The BigInteger is
   * guaranteed to be positive, and it is guaranteed to be larger than Long.MAX_VALUE. The internal
   * state is guaranteed to be empty when this method is called.
   *
   * @param n The value to consume.
   */
  protected abstract void readBigIntegerToBcd(BigInteger input);

  /**
   * Returns a BigDecimal encoding the internal BCD value.
   *
   * @return A BigDecimal representation of the internal BCD.
   */
  protected abstract BigDecimal bcdToBigDecimal();

  protected abstract void copyBcdFrom(FormatQuantity _other);

  /**
   * Removes trailing zeros from the BCD (adjusting the scale as required) and then computes the
   * precision. The precision is the number of digits in the number up through the greatest nonzero
   * digit.
   *
   * <p>This method must always be called when bcd changes in order for assumptions to be correct in
   * methods like {@link #fractionCount()}.
   */
  protected abstract void compact();
}
