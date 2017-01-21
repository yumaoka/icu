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
public class FormatQuantity2 implements FormatQuantity {

  /**
   * The BCD of the 16 digits of the number represented by this object. Every 4 bits of the long map
   * to one digit. For example, the number "12345" in BCD is "0x12345".
   *
   * <p>Whenever bcd changes internally, {@link #computePrecisionAndCompact()} must be called,
   * except in special cases like setting the digit to zero.
   */
  private long bcd;

  /**
   * The power of ten corresponding to the least significant digit in the BCD. For example, if this
   * object represents the number "3.14", the BCD will be "0x314" and the scale will be -2.
   *
   * <p>Note that in {@link java.math.BigDecimal}, the scale is defined differently: the number of
   * digits after the decimal place, which is the negative of our definition of scale.
   */
  private int scale;

  /**
   * The number of digits in the BCD. For example, "1007" has BCD "0x1007" and precision 4. The
   * maximum precision is 16 since a long can hold only 16 digits.
   *
   * <p>This value must be re-calculated whenever the value in bcd changes by using {@link
   * #computePrecisionAndCompact()}.
   */
  private int precision;

  /**
   * A bitmask of properties relating to the number represented by this object.
   *
   * @see #NEGATIVE_FLAG
   * @see #INFINITY_FLAG
   * @see #NAN_FLAG
   */
  private int flags;

  private static final int NEGATIVE_FLAG = 1;
  private static final int INFINITY_FLAG = 2;
  private static final int NAN_FLAG = 4;

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
  private int lOptPos = Integer.MAX_VALUE;
  private int lReqPos = 0;
  private int rReqPos = 0;
  private int rOptPos = Integer.MIN_VALUE;

  public FormatQuantity2(long input) {
    readLongToBcd(input);
  }

  public FormatQuantity2(int input) {
    readIntToBcd(input);
  }

  public FormatQuantity2(double input) {
    readDoubleToBcd(input);
  }

  public FormatQuantity2(BigInteger input) {
    readBigIntegerToBcd(input);
  }

  public FormatQuantity2(BigDecimal input) {
    readBigDecimalToBcd(input);
  }

  public FormatQuantity2(FormatQuantity2 other) {
    copyFrom(other);
  }

  @Override
  public FormatQuantity clone() {
    return new FormatQuantity2(this);
  }

  @Override
  public void copyFrom(FormatQuantity _other) {
    // TODO: Check before casting
    FormatQuantity2 other = (FormatQuantity2) _other;
    lOptPos = other.lOptPos;
    lReqPos = other.lReqPos;
    rReqPos = other.rReqPos;
    rOptPos = other.rOptPos;
    bcd = other.bcd;
    scale = other.scale;
    precision = other.precision;
    flags = other.flags;
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

  // TODO: Should the significant digit and interval logic be removed from this class and moved
  // into the Rounder class instead?

  @Override
  public void roundToSignificantDigits(
      int minimumSignificantDigits, int maximumSignificantDigits, RoundingMode roundingMode) {
    assert 0 <= minimumSignificantDigits && minimumSignificantDigits <= maximumSignificantDigits;
    int magnitude = getMagnitude();
    if (maximumSignificantDigits < Integer.MAX_VALUE) {
      roundToMagnitude(magnitude - maximumSignificantDigits + 1, roundingMode);
    }
    magnitude = getMagnitude(); // in case magnitude changed
    lReqPos = Math.max(lReqPos, magnitude + 1);
    rReqPos = Math.min(rReqPos, magnitude - minimumSignificantDigits + 1);
  }

  @Override
  public void roundToInterval(BigDecimal roundingInterval, RoundingMode roundingMode) {
    // TODO: Avoid converting back and forth to BigDecimal.
    BigDecimal temp = bcdToBigDecimal();
    temp = temp.setScale(8, roundingMode).divide(roundingInterval, roundingMode);
    temp = temp.setScale(0, roundingMode);
    temp = temp.multiply(roundingInterval);
    flags = 0;
    readBigDecimalToBcd(temp);
  }

  @Override
  public void roundToMagnitude(int roundingMagnitude, RoundingMode roundingMode) {
    bcdRoundToMagnitude(roundingMagnitude, roundingMode);
  }

  @Override
  public void setDigitAtMagnitude(byte digit, int magnitude) {
    assert digit >= 0 && digit < 10;
    if (magnitude >= scale && magnitude < scale + 16) {
      // Set a digit in the middle.
      int shift4 = (magnitude - scale) * 4;
      bcd = (bcd & (~(0xfL << shift4))) | (((long) digit) << shift4);
      computePrecisionAndCompact();
    } else if (magnitude >= scale + 16) {
      // Shift off digits to the right.
      int shift = magnitude - scale - 15;
      bcd = (bcd >>> (shift*4)) | (((long) digit) << 60);
      scale += shift;
      computePrecisionAndCompact();
    } else {
      // Might require shifting off digits to the left.
      // Since we never lose the high-significance digits, exit if the BCD is already full.
      int shift = scale - magnitude;
      if (shift + precision > 16) return;
      bcd = (bcd << (shift * 4)) | digit;
      scale -= shift;
      computePrecisionAndCompact();
    }
  }

  @Override
  public void multiplyBy(BigDecimal multiplicand) {
    // TODO: Perform the multiplication in BCD space.
    BigDecimal temp = bcdToBigDecimal();
    temp = temp.multiply(multiplicand);
    flags = 0;
    readBigDecimalToBcd(temp);
  }

  @Override
  public boolean isZero() {
    return precision == 0;
  }

  @Override
  public int getMagnitude() {
    if (precision == 0) {
      // Special case for the number zero; magnitude is not well defined.
      return 0;
    } else {
      return scale + precision - 1;
    }
  }

  @Override
  public void adjustMagnitude(int delta) {
    scale += delta;
    lOptPos = addOrMaxValue(lOptPos, delta);
    lReqPos = addOrMaxValue(lReqPos, delta);
    rReqPos = addOrMaxValue(rReqPos, delta);
    rOptPos = addOrMaxValue(rOptPos, delta);
  }

  private static int addOrMaxValue(int a, int b) {
    // Check for overflow, and return min/max value if overflow occurs.
    if (b < 0 && a + b > a) {
      return Integer.MIN_VALUE;
    } else if (b > 0 && a + b < a) {
      return Integer.MAX_VALUE;
    }
    return a + b;
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
  public double toDouble() {
    return bcdToDouble();
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
        return bcdToLong();
      case f:
        return bcdToFractionLong(true);
      case t:
        return bcdToFractionLong(false);
      case v:
        return fractionCount();
      case w:
        return fractionCountWithoutTrailingZeros();
      default:
        return bcdToDouble();
    }
  }

  //  @Override
  //  public boolean hasNextFraction() {
  //    if (rReqPos < 0) {
  //      // Required zone.
  //      return true;
  //    } else if (rOptPos >= 0) {
  //      // Forbidden zone.
  //      return false;
  //    } else {
  //      return scale < 0 && precision > 0;
  //    }
  //  }

  //  @Override
  //  public byte nextFraction() {
  //    byte returnValue = 0;
  //    if (scale >= 0) {
  //      // Integer with no fraction.
  //    } else if (scale < -16) {
  //      // Fraction with no 10^-1 digit.
  //      scale++;
  //    } else {
  //      // Fraction and integer.
  //      returnValue = popDigit(-scale - 1);
  //      scale++;
  //    }
  //
  //    // Update digit brackets
  //    if (lOptPos < 0) {
  //      lOptPos += 1;
  //    }
  //    if (lReqPos < 0) {
  //      lReqPos += 1;
  //    }
  //    if (rReqPos < 0) {
  //      rReqPos += 1;
  //    }
  //    if (rOptPos < 0) {
  //      rOptPos += 1;
  //    }
  //
  //    assert returnValue >= 0;
  //    return returnValue;
  //  }

  //  @Override
  //  public boolean hasNextInteger() {
  //    if (lReqPos > 0) {
  //      // Required zone.
  //      return true;
  //    } else if (lOptPos <= 0) {
  //      // Forbidden zone.
  //      return false;
  //    } else {
  //      // Optional zone.
  //      return scale + precision > 0;
  //    }
  //  }

  @Override
  public int fractionCount() {
    int ncount = scale;
    return (rReqPos < ncount) ? -rReqPos : (rOptPos > ncount) ? -rOptPos : -ncount;
  }

  private int fractionCountWithoutTrailingZeros() {
    return Math.max(-scale, 0);
  }

  @Override
  public int integerCount() {
    // Special handling for zero
    if (isZero() && lReqPos == 0 && rReqPos == 0) {
      return 1;
    }

    int count = scale + precision;
    return (lReqPos > count) ? lReqPos : (lOptPos < count) ? lOptPos : count;
  }

  @Override
  public byte getIntegerDigit(int index) {
    return getDigitPos(index - scale);
  }

  @Override
  public byte getFractionDigit(int index) {
    return getDigitPos(-index - scale - 1);
  }

  /**
   * Returns a single digit from the BCD list. No internal state is changed by calling this method.
   *
   * @param position The position of the digit to pop, counted in BCD units from the least
   *     significant digit. If outside the range [0,16), zero is returned.
   * @return The digit at the specified location.
   */
  private byte getDigitPos(int position) {
    // TODO: Make this method take the index instead of the position?
    if (position < 0 || position >= 16) return 0;
    return (byte) ((bcd >>> (position * 4)) & 0xf);
  }

  //  @Override
  //  public byte nextInteger() {
  //    byte returnValue = 0;
  //    if (scale > 0) {
  //      // Integer with no 10^0 digit.
  //      scale--;
  //    } else if (scale <= -16) {
  //      // Fraction with no integer.
  //    } else {
  //      // Fraction and integer.
  //      returnValue = popDigit(-scale);
  //    }
  //
  //    // Update digit brackets
  //    if (lOptPos > 0) {
  //      lOptPos -= 1;
  //    }
  //    if (lReqPos > 0) {
  //      lReqPos -= 1;
  //    }
  //    if (rReqPos > 0) {
  //      rReqPos -= 1;
  //    }
  //    if (rOptPos > 0) {
  //      rOptPos -= 1;
  //    }
  //
  //    return returnValue;
  //  }

  //////////////////////////////////
  ///// BCD ARITHMETIC METHODS /////
  //////////////////////////////////

  /**
   * Sets the internal BCD state to represent the value in the given long.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  private void readLongToBcd(long n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    long result = 0L;
    int i = 16;
    for (; n != 0L; n /= 10L, i--) {
      result = (result >>> 4) + ((n % 10) << 60);
    }
    int adjustment = (i > 0) ? i : 0;
    bcd = result >>> (adjustment * 4);
    scale = (i < 0) ? -i : 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given int.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  private void readIntToBcd(int n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    long result = 0L;
    int i = 16;
    for (; n != 0; n /= 10, i--) {
      result = (result >>> 4) + (((long) n % 10) << 60);
    }
    // ints can't overflow the 16 digits in the BCD, so scale is always zero
    bcd = result >>> (i * 4);
    scale = 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given double.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  private void readDoubleToBcd(double n) {
    if (n < 0) {
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
    readLongToBcd(Math.round(n));
    scale -= fracLength;
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigInteger.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  private void readBigIntegerToBcd(BigInteger n) {
    if (n.signum() == 0) {
      setToZero();
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    long result = 0L;
    int i = 16;
    for (; n.signum() != 0; i--) {
      BigInteger[] temp = n.divideAndRemainder(BigInteger.TEN);
      result = (result >>> 4) + (temp[1].longValue() << 60);
      n = temp[0];
    }
    int adjustment = (i > 0) ? i : 0;
    bcd = result >>> (adjustment * 4);
    scale = (i < 0) ? -i : 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigDecimal.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  private void readBigDecimalToBcd(BigDecimal n) {
    if (n.signum() == 0) {
      setToZero();
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

  private void setToZero() {
    bcd = 0L;
    scale = 0;
    precision = 0;
  }

  /**
   * Removes trailing zeros from the BCD (adjusting the scale as required) and then computes the
   * precision. The precision is the number of digits in the number up through the greatest nonzero
   * digit.
   *
   * <p>This method must always be called when bcd changes in order for assumptions to be correct in
   * methods like {@link #fractionCount()}.
   */
  private void computePrecisionAndCompact() {
    // Special handling for 0
    if (bcd == 0L) {
      scale = 0;
      precision = 0;
      return;
    }

    // Compact the number (remove trailing zeros)
    int delta = Long.numberOfTrailingZeros(bcd) / 4;
    bcd >>>= delta * 4;
    scale += delta;

    // Compute precision
    precision = 16 - (Long.numberOfLeadingZeros(bcd) / 4);
  }

  /**
   * Returns a long approximating the internal BCD. A long can only represent the integral part of
   * the number.
   *
   * @return A double representation of the internal BCD.
   */
  private long bcdToLong() {
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
  private long bcdToFractionLong(boolean includeTrailingZeros) {
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
  private double bcdToDouble() {
    long tempLong = 0L;
    for (int shift = (precision - 1) * 4; shift >= 0; shift -= 4) {
      tempLong = tempLong * 10 + ((bcd >>> shift) & 0xf);
    }
    double result = tempLong;
    if (scale >= 0) {
      int i = scale;
      for (; i >= 9; i -= 9) result *= 1000000000;
      for (; i >= 3; i -= 3) result *= 1000;
      for (; i >= 1; i -= 1) result *= 10;
    } else {
      int i = scale;
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
  private BigDecimal bcdToBigDecimal() {
    long tempLong = 0L;
    for (int shift = (precision - 1) * 4; shift >= 0; shift -= 4) {
      tempLong = tempLong * 10 + ((bcd >>> shift) & 0xf);
    }
    BigDecimal result = BigDecimal.valueOf(tempLong);
    result = result.scaleByPowerOfTen(scale);
    if (isNegative()) result = result.negate();
    return result;
  }

  //  /**
  //   * Removes a digit from the middle of the BCD list and shifts all higher digits to the right. Also
  //   * adjusts the precision counter if required.
  //   *
  //   * @param position The position of the digit to pop, counted in BCD units from the least
  //   *     significant digit. Assumed to be in [0,16).
  //   * @return The digit at the location that was popped.
  //   */
  //  private byte popDigit(int position) {
  //    byte returnValue;
  //
  //    // These three cases need to be handled separately in order to avoid bitwise wrap around.
  //    switch (position) {
  //      case 0:
  //        // Pop the least significant digit
  //        returnValue = (byte) (bcd & 0xf);
  //        bcd >>>= 4;
  //        break;
  //
  //      case 1:
  //        // Fast track case 1
  //        returnValue = (byte) ((bcd >>> 4) & 0xf);
  //        bcd = ((bcd >>> 4) & ~0xf) | (bcd & 0xf);
  //        break;
  //
  //      case 2:
  //        // Fast track case 2
  //        returnValue = (byte) ((bcd >>> 8) & 0xf);
  //        bcd = ((bcd >>> 4) & ~0xff) | (bcd & 0xff);
  //        break;
  //
  //      case 15:
  //        // Pop the most significant digit
  //        returnValue = (byte) ((bcd >>> 60) & 0xf);
  //        bcd = ((bcd << 4) >>> 4);
  //        break;
  //
  //      default:
  //        // Pop a digit somewhere in the middle
  //        int shift = position * 4;
  //        returnValue = (byte) ((bcd >>> shift) & 0xf);
  //        long left = (bcd << (64 - shift)) >>> (64 - shift);
  //        long right = (bcd >>> (shift + 4)) << shift;
  //        bcd = left | right;
  //        break;
  //    }
  //
  //    // TODO: Use data from above to avoid calling computePrecisionAndCompact() here?
  //    computePrecisionAndCompact();
  //    return returnValue;
  //  }

  private static enum RoundingSection {
    LOWER,
    HALF,
    UPPER
  }

  private void bcdRoundToMagnitude(int magnitude, RoundingMode roundingMode) {
    // The position in the BCD at which rounding will be performed; digits to the right of position
    // will be rounded away.
    int position = magnitude - scale;

    if (position <= 0) {
      // All digits are to the left of the rounding magnitude.
    } else if (bcd == 0L || (position <= 16 && (bcd << ((16 - position) * 4)) == 0L)) {
      // All digits that would be rounded off are zero.
      bcd >>>= (position * 4);
      scale = magnitude;
      computePrecisionAndCompact();
    } else {
      // Perform rounding logic.
      // "leading" = most significant digit to the right of rounding
      // "trailing" = least significant digit to the left of rounding
      byte leadingDigit = getDigitPos(position - 1);
      byte trailingDigit = getDigitPos(position);

      // Compute which section (lower, half, or upper) of the number we are in
      RoundingSection section = RoundingSection.HALF;
      if (leadingDigit < 5) {
        section = RoundingSection.LOWER;
      } else if (leadingDigit > 5) {
        section = RoundingSection.UPPER;
      } else {
        for (int p = position - 2; p >= 0; p--) {
          if (getDigitPos(p) != 0) {
            section = RoundingSection.UPPER;
            break;
          }
        }
      }

      // Apply the rounding rules
      boolean roundDown = true; // initialize variable to avoid compiler errors
      switch (roundingMode) {
        case UP:
          roundDown = false;
          break;

        case DOWN:
          roundDown = true;
          break;

        case CEILING:
          roundDown = isNegative();
          break;

        case FLOOR:
          roundDown = !isNegative();
          break;

        case HALF_UP:
          switch (section) {
            case HALF:
              roundDown = false;
              break;
            case LOWER:
              roundDown = true;
              break;
            case UPPER:
              roundDown = false;
              break;
          }
          break;

        case HALF_DOWN:
          switch (section) {
            case HALF:
              roundDown = true;
              break;
            case LOWER:
              roundDown = true;
              break;
            case UPPER:
              roundDown = false;
              break;
          }
          break;

        case HALF_EVEN:
          switch (section) {
            case HALF:
              roundDown = (trailingDigit % 2) == 0;
              break;
            case LOWER:
              roundDown = true;
              break;
            case UPPER:
              roundDown = false;
              break;
          }
          break;

        case UNNECESSARY:
        default:
          throw new ArithmeticException("Rounding is required on " + this);
      }

      // Perform truncation
      if (position < 16) {
        bcd >>>= (position * 4); // shift off the rounded digits
      } else {
        bcd = 0L; // all digits are being rounded off
      }
      scale = magnitude;

      // Bubble the result to the higher digits
      if (!roundDown) {
        if (trailingDigit == 9) {
          int bubblePos = 0;
          for (; getDigitPos(bubblePos) == 9; bubblePos++) {}
          // Note: the most digits BCD can have at this point is 15, so bubblePos <= 15
          bcd >>>= (bubblePos * 4); // shift off the trailing 9s
          scale += bubblePos;
        }
        assert getDigitPos(0) != 9;
        bcd += 1; // the addition operation will apply to the trailing digit at position 0
      }
      computePrecisionAndCompact();
    }
  }

  @Override
  public String toString() {
    return String.format(
        "<FormatQuantity2 %s:%d:%d:%s %016XE%d>",
        (lOptPos > 1000 ? "max" : String.valueOf(lOptPos)),
        lReqPos,
        rReqPos,
        (rOptPos < -1000 ? "min" : String.valueOf(rOptPos)),
        bcd,
        scale);
  }
}
