// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class FormatQuantity4 extends FormatQuantityBCD {

  /**
   * The BCD of the 16 digits of the number represented by this object. Every 4 bits of the long map
   * to one digit. For example, the number "12345" in BCD is "0x12345".
   *
   * <p>Whenever bcd changes internally, {@link #compact()} must be called, except in special cases
   * like setting the digit to zero.
   */
  private byte[] bcdBytes;

  private long bcdLong = 0L;

  private boolean usingBytes = false;;

  @Override
  public int maxRepresentableDigits() {
    return Integer.MAX_VALUE;
  }

  public FormatQuantity4() {
    setBcdToZero();
  }

  public FormatQuantity4(long input) {
    setToLong(input);
  }

  public FormatQuantity4(int input) {
    setToInt(input);
  }

  public FormatQuantity4(double input) {
    setToDouble(input);
  }

  public FormatQuantity4(BigInteger input) {
    setToBigInteger(input);
  }

  public FormatQuantity4(BigDecimal input) {
    setToBigDecimal(input);
  }

  public FormatQuantity4(FormatQuantity4 other) {
    copyFrom(other);
  }

  @Override
  protected byte getDigitPos(int position) {
    if (usingBytes) {
      if (position < 0 || position > precision) return 0;
      return bcdBytes[position];
    } else {
      if (position < 0 || position >= 16) return 0;
      return (byte) ((bcdLong >>> (position * 4)) & 0xf);
    }
  }

  @Override
  protected void setDigitPos(int position, byte value) {
    assert position >= 0;
    if (usingBytes) {
      ensureCapacity(position + 1);
      bcdBytes[position] = value;
    } else if (position >= 16) {
      switchStorage();
      ensureCapacity(position + 1);
      bcdBytes[position] = value;
    } else {
      int shift = position * 4;
      bcdLong = bcdLong & ~(0xfL << shift) | ((long) value << shift);
    }
  }

  /**
   * Appends a digit, optionally with one or more leading zeros, to the end of the value represented
   * by this FormatQuantity.
   *
   * <p>The primary use of this method is to construct numbers during a parsing loop. It allows
   * parsing to take advantage of the digit list infrastructure primarily designed for formatting.
   *
   * @param value The digit to append.
   * @param leadingZeros The number of zeros to append before the digit. For example, if the value
   *     in this instance starts as 12.3, and you append a 4 with 1 leading zero, the value becomes
   *     12.304.
   * @param appendAsInteger If true, increase the magnitude of existing digits to make room for the
   *     new digit. If false, append to the end like a fraction digit. If true, there must not be
   *     any fraction digits already in the number.
   * @internal
   * @deprecated This API is ICU internal only.
   */
  @Deprecated
  public void appendDigit(byte value, int leadingZeros, boolean appendAsInteger) {
    assert leadingZeros >= 0;

    // Zero requires special handling to maintain the invariant that the least-significant digit
    // in the BCD is nonzero.
    if (value == 0) {
      if (appendAsInteger && precision != 0) {
        scale += leadingZeros + 1;
      }
      return;
    }

    // Adjust scale
    if (appendAsInteger) {
      if (scale < 0) {
        throw new IllegalArgumentException(
            "Can't append integer digit if fraction digit is already present");
      } else if (scale > 0) {
        // Integer digit appended against trailing zeros.
        leadingZeros += scale;
        scale = 0;
      }
    } else {
      if (scale > 0) {
        // Fraction digit appended against trailing zeros.
        int oldScale = scale;
        scale = -leadingZeros - 1;
        leadingZeros += oldScale;
      } else {
        scale -= leadingZeros + 1;
      }
    }

    // Append digit
    if (precision == 0) {
      bcdLong |= value;
    } else if (precision + leadingZeros + 1 <= 16) {
      assert !usingBytes;
      bcdLong <<= (4 * (leadingZeros + 1));
      bcdLong |= value;
    } else {
      if (!usingBytes) {
        switchStorage();
      }
      ensureCapacity(precision + leadingZeros + 1);
      for (int i = 0; i < precision; i++) {
        bcdBytes[i + leadingZeros + 1] = bcdBytes[i];
      }
      for (int i = 0; i < leadingZeros; i++) {
        bcdBytes[i + 1] = 0;
      }
      bcdBytes[0] = value;
    }
    precision += 1 + leadingZeros;
  }

  @Override
  protected void shiftRight(int numDigits) {
    if (usingBytes) {
      int i = 0;
      for (; i < precision - numDigits; i++) {
        bcdBytes[i] = bcdBytes[i + numDigits];
      }
      for (; i < precision; i++) {
        bcdBytes[i] = 0;
      }
    } else {
      bcdLong >>>= (numDigits * 4);
    }
    scale += numDigits;
    precision -= numDigits;
  }

  @Override
  protected void setBcdToZero() {
    if (usingBytes) {
      for (int i = 0; i < precision; i++) {
        bcdBytes[i] = (byte) 0;
      }
    }
    usingBytes = false;
    bcdLong = 0L;
    scale = 0;
    precision = 0;
  }

  @Override
  protected void readIntToBcd(int n) {
    // ints always fit inside the long implementation.
    long result = 0L;
    int i = 16;
    for (; n != 0; n /= 10, i--) {
      result = (result >>> 4) + (((long) n % 10) << 60);
    }
    usingBytes = false;
    bcdLong = result >>> (i * 4);
    scale = 0;
    precision = 16 - i;
  }

  @Override
  protected void readLongToBcd(long n) {
    if (n >= 10000000000000000L) {
      ensureCapacity();
      int i = 0;
      for (; n != 0L; n /= 10L, i++) {
        bcdBytes[i] = (byte) (n % 10);
      }
      usingBytes = true;
      scale = 0;
      precision = i;
    } else {
      long result = 0L;
      int i = 16;
      for (; n != 0L; n /= 10L, i--) {
        result = (result >>> 4) + ((n % 10) << 60);
      }
      assert i >= 0;
      usingBytes = false;
      bcdLong = result >>> (i * 4);
      scale = 0;
      precision = 16 - i;
    }
  }

  @Override
  protected void readBigIntegerToBcd(BigInteger n) {
    ensureCapacity(); // allocate initial byte array
    int i = 0;
    for (; n.signum() != 0; i++) {
      BigInteger[] temp = n.divideAndRemainder(BigInteger.TEN);
      ensureCapacity(i + 1);
      bcdBytes[i] = temp[1].byteValue();
      n = temp[0];
    }
    usingBytes = true;
    scale = 0;
    precision = i;
  }

  @Override
  protected BigDecimal bcdToBigDecimal() {
    if (usingBytes) {
      // Converting to a string here is faster than doing BigInteger/BigDecimal arithmetic.
      StringBuilder sb = new StringBuilder();
      if (isNegative()) sb.append('-');
      assert precision > 0;
      for (int i = precision - 1; i >= 0; i--) {
        sb.append(getDigitPos(i));
      }
      if (scale != 0) {
        sb.append('E');
        sb.append(scale);
      }
      return new BigDecimal(sb.toString());
    } else {
      long tempLong = 0L;
      for (int shift = (precision - 1); shift >= 0; shift--) {
        tempLong = tempLong * 10 + getDigitPos(shift);
      }
      BigDecimal result = BigDecimal.valueOf(tempLong);
      result = result.scaleByPowerOfTen(scale);
      if (isNegative()) result = result.negate();
      return result;
    }
  }

  @Override
  protected void compact() {
    if (usingBytes) {
      int delta = 0;
      for (; delta < precision && bcdBytes[delta] == 0; delta++) ;
      if (delta == precision) {
        // Number is zero
        setBcdToZero();
        return;
      } else {
        // Remove trailing zeros
        shiftRight(delta);
      }

      // Compute precision
      int leading = precision - 1;
      for (; leading >= 0 && bcdBytes[leading] == 0; leading--) ;
      precision = leading + 1;

      // Switch storage mechanism if possible
      if (precision <= 16) {
        switchStorage();
      }

    } else {
      if (bcdLong == 0L) {
        // Number is zero
        setBcdToZero();
        return;
      }

      // Compact the number (remove trailing zeros)
      int delta = Long.numberOfTrailingZeros(bcdLong) / 4;
      bcdLong >>>= delta * 4;
      scale += delta;

      // Compute precision
      precision = 16 - (Long.numberOfLeadingZeros(bcdLong) / 4);
    }
  }

  /** Ensure that a byte array of at least 40 digits is allocated. */
  private void ensureCapacity() {
    ensureCapacity(40);
  }

  private void ensureCapacity(int capacity) {
    if (bcdBytes == null && capacity > 0) {
      bcdBytes = new byte[capacity];
    } else if (bcdBytes.length < capacity) {
      byte[] bcd1 = new byte[capacity * 2];
      System.arraycopy(bcdBytes, 0, bcd1, 0, bcdBytes.length);
      bcdBytes = bcd1;
    }
  }

  /** Switches the internal storage mechanism between the 64-bit long and the byte array. */
  private void switchStorage() {
    if (usingBytes) {
      // Change from bytes to long
      bcdLong = 0L;
      for (int i = precision - 1; i >= 0; i--) {
        bcdLong <<= 4;
        bcdLong |= bcdBytes[i];
        bcdBytes[i] = 0;
      }
      usingBytes = false;
    } else {
      // Change from long to bytes
      ensureCapacity();
      for (int i = 0; i < precision; i++) {
        bcdBytes[i] = (byte) (bcdLong & 0xf);
        bcdLong >>>= 0;
      }
      usingBytes = true;
    }
  }

  @Override
  protected void copyBcdFrom(FormatQuantity _other) {
    FormatQuantity4 other = (FormatQuantity4) _other;
    if (other.usingBytes) {
      usingBytes = true;
      ensureCapacity(other.precision);
      System.arraycopy(other.bcdBytes, 0, bcdBytes, 0, other.precision);
    } else {
      usingBytes = false;
      bcdLong = other.bcdLong;
    }
  }

  /**
   * Checks whether the bytes stored in this instance are all valid. For internal unit testing only.
   *
   * @return An error message if this instance is invalid, or null if this instance is healthy.
   * @internal
   * @deprecated This API is for ICU internal use only.
   */
  @Deprecated
  public String checkHealth() {
    if (usingBytes) {
      if (bcdLong != 0) return "Value in bcdLong but we are in byte mode";
      if (precision == 0) return "Zero precision but we are in byte mode";
      if (precision > bcdBytes.length) return "Precision exceeds length of byte array";
      if (getDigitPos(precision - 1) == 0) return "Most significant digit is zero in byte mode";
      if (getDigitPos(0) == 0) return "Least significant digit is zero in long mode";
      for (int i = 0; i < precision; i++) {
        if (getDigitPos(i) >= 10) return "Digit exceeding 10 in byte array";
        if (getDigitPos(i) < 0) return "Digit below 0 in byte array";
      }
      for (int i = precision; i < bcdBytes.length; i++) {
        if (getDigitPos(i) != 0) return "Nonzero digits outside of range in byte array";
      }
    } else {
      if (bcdBytes != null) {
        for (int i = 0; i < bcdBytes.length; i++) {
          if (bcdBytes[i] != 0) return "Nonzero digits in byte array but we are in long mode";
        }
      }
      if (precision == 0 && bcdLong != 0) return "Value in bcdLong even though precision is zero";
      if (precision > 16) return "Precision exceeds length of long";
      if (precision != 0 && getDigitPos(precision - 1) == 0)
        return "Most significant digit is zero in long mode";
      if (precision != 0 && getDigitPos(0) == 0)
        return "Least significant digit is zero in long mode";
      for (int i = 0; i < precision; i++) {
        if (getDigitPos(i) >= 10) return "Digit exceeding 10 in long";
        if (getDigitPos(i) < 0) return "Digit below 0 in long (?!)";
      }
      for (int i = precision; i < 16; i++) {
        if (getDigitPos(i) != 0) return "Nonzero digits outside of range in long";
      }
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (usingBytes) {
      for (int i = precision - 1; i >= 0; i--) {
        sb.append(bcdBytes[i]);
      }
    } else {
      sb.append(Long.toHexString(bcdLong));
    }
    return String.format(
        "<FormatQuantity4 %s:%d:%d:%s %s %s%s%d>",
        (lOptPos > 1000 ? "max" : String.valueOf(lOptPos)),
        lReqPos,
        rReqPos,
        (rOptPos < -1000 ? "min" : String.valueOf(rOptPos)),
        (bcdLong < 0 ? "bytes" : "long"),
        sb,
        "E",
        scale);
  }
}
