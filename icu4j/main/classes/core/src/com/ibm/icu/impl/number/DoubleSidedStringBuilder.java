// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

public class DoubleSidedStringBuilder implements CharSequence {
  private char[] chars;
  private int zero;
  private int length;

  public DoubleSidedStringBuilder() {
    this(40);
  }

  public DoubleSidedStringBuilder(int capacity) {
    chars = new char[capacity];
    zero = capacity / 2;
    length = 0;
  }

  @Override
  public int length() {
    return length;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index > length) {
      throw new IndexOutOfBoundsException();
    }
    return chars[zero + index];
  }

  /** Appends the specified codePoint to the end of the string. */
  public int appendCodePoint(int codePoint) {
    return insertCodePoint(length, codePoint);
  }

  /** Inserts the specified codePoint at the specified index in the string. */
  public int insertCodePoint(int index, int codePoint) {
    int charCount = Character.charCount(codePoint);
    if (index == 0 && zero - charCount >= 0) {
      // Append to start
      zero -= charCount;
      Character.toChars(codePoint, chars, zero);
      length += charCount;
    } else if (index == length && zero + length + charCount < chars.length) {
      // Append to end
      Character.toChars(codePoint, chars, zero + length);
      length += charCount;
    } else {
      // Move chars around and/or allocate more space
      adjustForInsert(index, charCount);
      Character.toChars(codePoint, chars, zero + index);
    }
    return charCount;
  }

  /** Appends the specified CharSequence to the end of the string. */
  public int append(CharSequence sequence) {
    return insert(length, sequence);
  }

  /** Inserts the specified CharSequence at the specified index in the string. */
  public int insert(int index, CharSequence sequence) {
    if (sequence.length() == 1) {
      // Fast path: on a single-char string, using insertCodePoint below is 70% faster than the
      // CharSequence method: 12.2 ns versus 41.9 ns for five operations on my Linux x86-64.
      return insertCodePoint(index, sequence.charAt(0));
    } else {
      return insert(index, sequence, 0, sequence.length());
    }
  }

  /**
   * Inserts the specified CharSequence at the specified index in the string, reading from the
   * CharSequence from start (inclusive) to end (exclusive).
   */
  public int insert(int index, CharSequence sequence, int start, int end) {
    int count = end - start;
    if (index == 0 && zero - count >= 0) {
      // Append to start
      zero -= count;
      copyCharSequence(sequence, start, chars, zero, count);
      length += count;
    } else if (index == length && zero + length + count < chars.length) {
      // Append to end
      copyCharSequence(sequence, start, chars, zero + length, count);
      length += count;
    } else {
      // Move chars around and/or allocate more space
      adjustForInsert(index, count);
      copyCharSequence(sequence, start, chars, zero + index, count);
    }
    return count;
  }

  private void copyCharSequence(
      CharSequence source, int srcIndex, char[] dest, int destIndex, int count) {
    for (int i = 0; i < count; i++) {
      dest[destIndex + i] = source.charAt(srcIndex + i);
    }
  }

  private void adjustForInsert(int index, int count) {
    if (length + count > chars.length) {
      char[] newChars = new char[(length + count) * 2];
      int newZero = newChars.length / 2 - (length + count) / 2;
      System.arraycopy(chars, zero, newChars, newZero, index);
      System.arraycopy(chars, zero + index, newChars, newZero + index + count, length - index);
      chars = newChars;
      zero = newZero;
      length += count;
    } else {
      int newZero = chars.length / 2 - (length + count) / 2;
      System.arraycopy(chars, zero, chars, newZero, length);
      System.arraycopy(chars, newZero + index, chars, newZero + index + count, length - index);
      zero = newZero;
      length += count;
    }
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (start < 0 || end > length || end < start) {
      throw new IndexOutOfBoundsException();
    }
    DoubleSidedStringBuilder other = this.clone();
    other.zero = zero + start;
    other.length = end - start;
    return other;
  }

  @Override
  public String toString() {
    return new String(chars, zero, length);
  }

  @Override
  public DoubleSidedStringBuilder clone() {
    DoubleSidedStringBuilder other = new DoubleSidedStringBuilder(chars.length);
    other.zero = zero;
    other.length = length;
    System.arraycopy(chars, zero, chars, zero, length);
    return other;
  }

  public DoubleSidedStringBuilder clear() {
    zero = chars.length / 2;
    length = 0;
    return this;
  }
}
