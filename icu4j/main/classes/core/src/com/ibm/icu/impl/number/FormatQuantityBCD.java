// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.Operand;

/** @author sffc */
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
    _copyBcdFrom(_other);
    FormatQuantityBCD other = (FormatQuantityBCD) _other;
    lOptPos = other.lOptPos;
    lReqPos = other.lReqPos;
    rReqPos = other.rReqPos;
    rOptPos = other.rOptPos;
    scale = other.scale;
    precision = other.precision;
    flags = other.flags;
  }

  protected abstract void _copyBcdFrom(FormatQuantity _other);

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
    readBigDecimalToBcd(temp);
  }

  @Override
  public void roundToMagnitude(int roundingMagnitude, RoundingMode roundingMode) {
    bcdRoundToMagnitude(roundingMagnitude, roundingMode);
  }

  @Override
  public void multiplyBy(BigDecimal multiplicand) {
    // TODO: Perform the multiplication in BCD space.
    // FIXME: This can push the FormatQuantity2 out of range.
    BigDecimal temp = bcdToBigDecimal();
    temp = temp.multiply(multiplicand);
    flags = 0;
    readBigDecimalToBcd(temp);
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
        return Math.abs(bcdToDouble());
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
  public double toDouble() {
    return bcdToDouble();
  }

  @Override
  public BigDecimal toBigDecimal() {
    return bcdToBigDecimal();
  }

  @Override
  public FormatQuantity clone() {
    if (this instanceof FormatQuantity2) {
      return new FormatQuantity2((FormatQuantity2) this);
    } else if (this instanceof FormatQuantity3) {
      return new FormatQuantity3((FormatQuantity3) this);
    } else {
      throw new IllegalArgumentException("Cannot clone implementation of type " + this.getClass());
    }
  }

  /**
   * Sets the internal BCD state to represent the value in the given double.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  protected void readDoubleToBcd(double n) {
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
    readLongToBcd(Math.round(n));
    scale -= fracLength;
  }

  protected abstract void setToZero();

  protected abstract void readIntToBcd(int input);

  protected abstract void readLongToBcd(long input);

  protected abstract void readBigIntegerToBcd(BigInteger input);

  protected abstract void readBigDecimalToBcd(BigDecimal input);

  protected abstract long bcdToLong();

  protected abstract long bcdToFractionLong(boolean includeTrailingZeros);

  protected abstract double bcdToDouble();

  protected abstract BigDecimal bcdToBigDecimal();

  protected abstract void bcdRoundToMagnitude(int magnitude, RoundingMode roundingMode);
}
