// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.formatters;

import com.ibm.icu.impl.number.Format.AfterFormat;
import com.ibm.icu.impl.number.ModifierHolder;
import com.ibm.icu.impl.number.NumberStringBuilder;
import com.ibm.icu.impl.number.Properties;

public class PaddingFormat implements AfterFormat {
  public enum PaddingLocation {
    BEFORE_PREFIX,
    AFTER_PREFIX,
    BEFORE_SUFFIX,
    AFTER_SUFFIX;

    public static PaddingLocation fromOld(int old) {
      switch (old) {
        case com.ibm.icu.text.DecimalFormat.PAD_BEFORE_PREFIX:
          return PaddingLocation.BEFORE_PREFIX;
        case com.ibm.icu.text.DecimalFormat.PAD_AFTER_PREFIX:
          return PaddingLocation.AFTER_PREFIX;
        case com.ibm.icu.text.DecimalFormat.PAD_BEFORE_SUFFIX:
          return PaddingLocation.BEFORE_SUFFIX;
        case com.ibm.icu.text.DecimalFormat.PAD_AFTER_SUFFIX:
          return PaddingLocation.AFTER_SUFFIX;
        default:
          throw new IllegalArgumentException("Don't know how to map " + old);
      }
    }

    public int toOld() {
      switch (this) {
        case BEFORE_PREFIX:
          return com.ibm.icu.text.DecimalFormat.PAD_BEFORE_PREFIX;
        case AFTER_PREFIX:
          return com.ibm.icu.text.DecimalFormat.PAD_AFTER_PREFIX;
        case BEFORE_SUFFIX:
          return com.ibm.icu.text.DecimalFormat.PAD_BEFORE_SUFFIX;
        case AFTER_SUFFIX:
          return com.ibm.icu.text.DecimalFormat.PAD_AFTER_SUFFIX;
        default:
          return -1; // silence compiler errors
      }
    }
  }

  public static interface IProperties {

    static int DEFAULT_PADDING_WIDTH = 0;

    /** @see #setPaddingWidth */
    public int getPaddingWidth();

    /**
     * Sets the minimum width of the string output by the formatting pipeline. For example, if
     * padding is enabled and paddingWidth is set to 6, formatting the number "3.14159" with the
     * pattern "0.00" will result in "··3.14" if '·' is your padding string.
     *
     * <p>If the number is longer than your padding width, the number will display as if no padding
     * width had been specified, which may result in strings longer than the padding width.
     *
     * <p>Width is counted in UTF-16 code units.
     *
     * @param paddingWidth The output width.
     * @return The property bag, for chaining.
     * @see #setPaddingString
     * @see #setPaddingLocation
     */
    public IProperties setPaddingWidth(int paddingWidth);

    static CharSequence DEFAULT_PADDING_STRING = null;

    /** @see #setPaddingString */
    public CharSequence getPaddingString();

    /**
     * Sets the string used for padding. This can be any string, but it usually makes sense for it
     * to be a single character or code point long.
     *
     * <p>If you do not pass a String object, the CharSequence will be converted to a String upon
     * construction of the formatting pipeline object.
     *
     * <p>Must be used in conjunction with {@link #setPaddingWidth}.
     *
     * @param paddingString The padding string. Defaults to an ASCII space (U+0020).
     * @return The property bag, for chaining.
     * @see #setPaddingWidth
     */
    public IProperties setPaddingString(CharSequence paddingString);

    static PaddingLocation DEFAULT_PADDING_LOCATION = null;

    /** @see #setPaddingLocation */
    public PaddingLocation getPaddingLocation();

    /**
     * Sets the location where the padding string is to be inserted to maintain the padding width:
     * one of BEFORE_PREFIX, AFTER_PREFIX, BEFORE_SUFFIX, or AFTER_SUFFIX.
     *
     * <p>Must be used in conjunction with {@link #setPaddingWidth}.
     *
     * @param paddingLocation The output width.
     * @return The property bag, for chaining.
     * @see #setPaddingWidth
     */
    public IProperties setPaddingLocation(PaddingLocation paddingLocation);
  }

  public static boolean usePadding(IProperties properties) {
    return properties.getPaddingWidth() != IProperties.DEFAULT_PADDING_WIDTH;
  }

  public static AfterFormat getInstance(IProperties properties) {
    return new PaddingFormat(
        properties.getPaddingWidth(),
        properties.getPaddingString(),
        properties.getPaddingLocation());
  }

  // Properties
  private final int paddingWidth;
  private final String paddingString;
  private final PaddingLocation paddingLocation;

  private PaddingFormat(int paddingWidth, CharSequence paddingString, PaddingLocation paddingLocation) {
    this.paddingWidth = paddingWidth > 0 ? paddingWidth : 10; // TODO: Is this a sensible default?
    this.paddingString = paddingString != null ? paddingString.toString() : "\u0020";
    this.paddingLocation =
        paddingLocation != null ? paddingLocation : PaddingLocation.BEFORE_PREFIX;
  }

  @Override
  public int after(ModifierHolder mods, NumberStringBuilder string, int leftIndex, int rightIndex) {

    // TODO: Count code points instead of code units?
    int requiredPadding = paddingWidth - (rightIndex - leftIndex) - mods.totalLength();

    if (requiredPadding <= 0) {
      // Skip padding, but still apply modifiers to be consistent
      return mods.applyAll(string, leftIndex, rightIndex);
    }

    int length = 0;
    if (paddingLocation == PaddingLocation.AFTER_PREFIX) {
      length += addPadding(requiredPadding, string, leftIndex);
    } else if (paddingLocation == PaddingLocation.BEFORE_SUFFIX) {
      length += addPadding(requiredPadding, string, rightIndex);
    }
    length += mods.applyAll(string, leftIndex, rightIndex + length);
    if (paddingLocation == PaddingLocation.BEFORE_PREFIX) {
      length += addPadding(requiredPadding, string, leftIndex);
    } else if (paddingLocation == PaddingLocation.AFTER_SUFFIX) {
      length += addPadding(requiredPadding, string, rightIndex + length);
    }

    return length;
  }

  private int addPadding(int requiredPadding, NumberStringBuilder string, int index) {
    for (int i = 0; i < requiredPadding; i++) {
      string.insert(index, paddingString, null);
    }
    return paddingString.length() * requiredPadding;
  }

  @Override
  public void export(Properties properties) {
    properties.setPaddingWidth(paddingWidth);
    properties.setPaddingString(paddingString);
    properties.setPaddingLocation(paddingLocation);
  }
}
