// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.FieldPosition;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.NumberFormat.Field;

public class NumberStringBuilder implements CharSequence {
  private char[] chars;
  private Field[] fields;
  private int zero;
  private int length;

  public NumberStringBuilder() {
    this(40);
  }

  public NumberStringBuilder(int capacity) {
    chars = new char[capacity];
    fields = new Field[capacity];
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

  /**
   * Appends the specified codePoint to the end of the string.
   *
   * @return The number of chars added: 1 if the code point is in the BMP, or 2 otherwise.
   */
  public int appendCodePoint(int codePoint, Field field) {
    return insertCodePoint(length, codePoint, field);
  }

  /**
   * Inserts the specified codePoint at the specified index in the string.
   *
   * @return The number of chars added: 1 if the code point is in the BMP, or 2 otherwise.
   */
  public int insertCodePoint(int index, int codePoint, Field field) {
    int count = Character.charCount(codePoint);
    int position = prepareForInsert(index, count);
    Character.toChars(codePoint, chars, position);
    Arrays.fill(fields, position, position + count, field);
    return count;
  }

  /**
   * Appends the specified CharSequence to the end of the string.
   *
   * @return The number of chars added, which is the length of CharSequence.
   */
  public int append(CharSequence sequence, Field field) {
    return insert(length, sequence, field);
  }

  /**
   * Inserts the specified CharSequence at the specified index in the string.
   *
   * @return The number of chars added, which is the length of CharSequence.
   */
  public int insert(int index, CharSequence sequence, Field field) {
    if (sequence.length() == 0) {
      // Nothing to insert.
      return 0;
    } else if (sequence.length() == 1) {
      // Fast path: on a single-char string, using insertCodePoint below is 70% faster than the
      // CharSequence method: 12.2 ns versus 41.9 ns for five operations on my Linux x86-64.
      return insertCodePoint(index, sequence.charAt(0), field);
    } else {
      return insert(index, sequence, 0, sequence.length(), field);
    }
  }

  /**
   * Inserts the specified CharSequence at the specified index in the string, reading from the
   * CharSequence from start (inclusive) to end (exclusive).
   *
   * @return The number of chars added, which is the length of CharSequence.
   */
  public int insert(int index, CharSequence sequence, int start, int end, Field field) {
    int count = end - start;
    int position = prepareForInsert(index, count);
    for (int i = 0; i < count; i++) {
      chars[position + i] = sequence.charAt(start + i);
      fields[position + i] = field;
    }
    return count;
  }

  /**
   * Appends the chars in the specified char array to the end of the string, and associates them
   * with the fields in the specified field array, which must have the same length as chars.
   *
   * @return The number of chars added, which is the length of the char array.
   */
  public int append(char[] chars, Field[] fields) {
    return insert(length, chars, fields);
  }

  /**
   * Inserts the chars in the specified char array at the specified index in the string, and
   * associates them with the fields in the specified field array, which must have the same length
   * as chars.
   *
   * @return The number of chars added, which is the length of the char array.
   */
  public int insert(int index, char[] chars, Field[] fields) {
    assert chars.length == fields.length;
    int count = chars.length;
    if (count == 0) return 0; // nothing to insert
    int position = prepareForInsert(index, count);
    for (int i = 0; i < count; i++) {
      this.chars[position + i] = chars[i];
      this.fields[position + i] = fields[i];
    }
    return count;
  }

  /**
   * Appends the contents of another {@link NumberStringBuilder} to the end of this instance.
   *
   * @return The number of chars added, which is the length of the other {@link
   *     NumberStringBuilder}.
   */
  public int append(NumberStringBuilder other) {
    return insert(length, other);
  }

  /**
   * Inserts the contents of another {@link NumberStringBuilder} into this instance at the given
   * index.
   *
   * @return The number of chars added, which is the length of the other {@link
   *     NumberStringBuilder}.
   */
  public int insert(int index, NumberStringBuilder other) {
    int count = other.length;
    if (count == 0) return 0; // nothing to insert
    int position = prepareForInsert(index, count);
    for (int i = 0; i < count; i++) {
      this.chars[position + i] = other.chars[other.zero + i];
      this.fields[position + i] = other.fields[other.zero + i];
    }
    return count;
  }

  private int prepareForInsert(int index, int count) {
    if (index == 0 && zero - count >= 0) {
      // Append to start
      zero -= count;
      length += count;
      return zero;
    } else if (index == length && zero + length + count < chars.length) {
      // Append to end
      length += count;
      return zero + length - count;
    } else {
      // Move chars around and/or allocate more space
      if (length + count > chars.length) {
        char[] newChars = new char[(length + count) * 2];
        Field[] newFields = new Field[(length + count) * 2];
        int newZero = newChars.length / 2 - (length + count) / 2;
        System.arraycopy(chars, zero, newChars, newZero, index);
        System.arraycopy(chars, zero + index, newChars, newZero + index + count, length - index);
        System.arraycopy(fields, zero, newFields, newZero, index);
        System.arraycopy(fields, zero + index, newFields, newZero + index + count, length - index);
        chars = newChars;
        fields = newFields;
        zero = newZero;
        length += count;
      } else {
        int newZero = chars.length / 2 - (length + count) / 2;
        System.arraycopy(chars, zero, chars, newZero, length);
        System.arraycopy(chars, newZero + index, chars, newZero + index + count, length - index);
        System.arraycopy(fields, zero, fields, newZero, length);
        System.arraycopy(fields, newZero + index, fields, newZero + index + count, length - index);
        zero = newZero;
        length += count;
      }
      return zero + index;
    }
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (start < 0 || end > length || end < start) {
      throw new IndexOutOfBoundsException();
    }
    NumberStringBuilder other = this.clone();
    other.zero = zero + start;
    other.length = end - start;
    return other;
  }

  /**
   * Returns the string represented by the characters in this string builder.
   *
   * <p>For a string intended be used for debugging, use {@link #toDebugString}.
   */
  @Override
  public String toString() {
    return new String(chars, zero, length);
  }

  private static final Map<Field, Character> fieldToDebugChar = new HashMap<Field, Character>();

  static {
    fieldToDebugChar.put(Field.SIGN, '-');
    fieldToDebugChar.put(Field.INTEGER, 'i');
    fieldToDebugChar.put(Field.FRACTION, 'f');
    fieldToDebugChar.put(Field.EXPONENT, 'e');
    fieldToDebugChar.put(Field.EXPONENT_SIGN, '+');
    fieldToDebugChar.put(Field.EXPONENT_SYMBOL, 'E');
    fieldToDebugChar.put(Field.DECIMAL_SEPARATOR, '.');
    fieldToDebugChar.put(Field.GROUPING_SEPARATOR, ',');
    fieldToDebugChar.put(Field.PERCENT, '%');
    fieldToDebugChar.put(Field.PERMILLE, '‰');
    fieldToDebugChar.put(Field.CURRENCY, '$');
  }

  /**
   * Returns a string that includes field information, for debugging purposes.
   *
   * <p>For example, if the string is "-12.345", the debug string will be something like
   * "&lt;NumberStringBuilder [-123.45] [-iii.ff]&gt;"
   *
   * @return A string for debugging purposes.
   */
  public String toDebugString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<NumberStringBuilder [");
    sb.append(this.toString());
    sb.append("] [");
    for (int i = zero; i < zero + length; i++) {
      if (fields[i] == null) {
        sb.append('n');
      } else {
        sb.append(fieldToDebugChar.get(fields[i]));
      }
    }
    sb.append("]>");
    return sb.toString();
  }

  /** @return A new array containing the contents of this string builder. */
  public char[] toCharArray() {
    return Arrays.copyOfRange(chars, zero, zero + length);
  }

  /** @return A new array containing the field values of this string builder. */
  public Field[] toFieldArray() {
    return Arrays.copyOfRange(fields, zero, zero + length);
  }

  /**
   * @return Whether the contents and field values of this string builder are equal to the given
   *     chars and fields.
   * @see #toCharArray
   * @see #toFieldArray
   */
  public boolean contentEquals(char[] chars, Field[] fields) {
    if (chars.length != length) return false;
    if (fields.length != length) return false;
    for (int i = 0; i < length; i++) {
      if (this.chars[zero + i] != chars[i]) return false;
      if (this.fields[zero + i] != fields[i]) return false;
    }
    return true;
  }

  /**
   * Populates the given {@link FieldPosition} based on this string builder.
   *
   * @param fp The FieldPosition to populate.
   * @param offset An offset to add to the field position index; can be zero.
   */
  public void populateFieldPosition(FieldPosition fp, int offset) {
    Field field = (Field) fp.getFieldAttribute();
    if (field == null) {
      // Backwards compatibility: read from fp.getField()
      if (fp.getField() == NumberFormat.INTEGER_FIELD) {
        field = Field.INTEGER;
      } else if (fp.getField() == NumberFormat.FRACTION_FIELD) {
        field = Field.FRACTION;
      } else {
        // No field is set
        return;
      }
    }

    boolean seenStart = false;
    int fractionStart = -1;
    for (int i = zero; i <= zero + length; i++) {
      Field _field = (i < zero + length) ? fields[i] : null;
      if (seenStart && field != _field) {
        // Special case: GROUPING_SEPARATOR counts as an INTEGER.
        if (field == Field.INTEGER && _field == Field.GROUPING_SEPARATOR) continue;
        fp.setEndIndex(i - zero + offset);
        break;
      } else if (!seenStart && field == _field) {
        fp.setBeginIndex(i - zero + offset);
        seenStart = true;
      }
      if (_field == Field.INTEGER || _field == Field.DECIMAL_SEPARATOR) {
        fractionStart = i - zero + 1;
      }
    }

    // Backwards compatibility: FRACTION needs to start after INTEGER if empty
    if (field == Field.FRACTION && !seenStart) {
      fp.setBeginIndex(fractionStart);
      fp.setEndIndex(fractionStart);
    }
  }

  public AttributedCharacterIterator getIterator() {
    AttributedString as = new AttributedString(toString());
    Field current = null;
    int currentStart = -1;
    for (int i = 0; i < length; i++) {
      Field field = fields[i + zero];
      if (current == Field.INTEGER && field == Field.GROUPING_SEPARATOR) {
        // Special case: GROUPING_SEPARATOR counts as an INTEGER.
        as.addAttribute(Field.GROUPING_SEPARATOR, Field.GROUPING_SEPARATOR, i, i + 1);
      } else if (current != field) {
        if (current != null) {
          as.addAttribute(current, current, currentStart, i);
        }
        current = field;
        currentStart = i;
      }
    }
    if (current != null) {
      as.addAttribute(current, current, currentStart, length);
    }
    return as.getIterator();
  }

  @Override
  public NumberStringBuilder clone() {
    NumberStringBuilder other = new NumberStringBuilder(chars.length);
    other.zero = zero;
    other.length = length;
    System.arraycopy(chars, zero, other.chars, zero, length);
    System.arraycopy(fields, zero, other.fields, zero, length);
    return other;
  }

  public NumberStringBuilder clear() {
    zero = chars.length / 2;
    length = 0;
    return this;
  }
}
