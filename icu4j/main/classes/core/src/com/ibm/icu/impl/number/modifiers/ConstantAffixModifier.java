// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.modifiers;

import com.ibm.icu.impl.number.Modifier;
import com.ibm.icu.impl.number.Modifier.AffixModifier;
import com.ibm.icu.impl.number.NumberStringBuilder;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.text.NumberFormat.Field;

/** The canonical implementation of {@link Modifier}, containing a prefix and suffix string. */
public class ConstantAffixModifier extends Modifier.BaseModifier implements AffixModifier {

  private final String prefix;
  private final String suffix;
  private final Field field;

  /**
   * Constructs an instance with the given strings.
   *
   * <p>The arguments need to be Strings, not CharSequences, because Strings are immutable but
   * CharSequences are not.
   *
   * @param prefix The prefix string.
   * @param suffix The suffix string.
   * @param field The field type to be associated with this modifier. Can be null.
   * @see Field
   */
  public ConstantAffixModifier(String prefix, String suffix, Field field) {
    // Use an empty string instead of null if we are given null
    this.prefix = (prefix == null ? "" : prefix);
    this.suffix = (suffix == null ? "" : suffix);
    this.field = field;
  }

  @Override
  public int apply(NumberStringBuilder output, int leftIndex, int rightIndex) {
    // Insert the suffix first since inserting the prefix will change the rightIndex
    int length = output.insert(rightIndex, suffix, field);
    length += output.insert(leftIndex, prefix, field);
    return length;
  }

  @Override
  public int length() {
    return prefix.length() + suffix.length();
  }

  @Override
  public String getPrefix() {
    return prefix;
  }

  @Override
  public String getSuffix() {
    return suffix;
  }

  @Override
  public String toString() {
    return String.format(
        "<ConstantAffixModifier(%d) prefix:'%s' suffix:'%s'>", length(), prefix, suffix);
  }

  @Override
  public void export(Properties properties) {
    properties.setPositivePrefix(prefix);
    properties.setPositiveSuffix(suffix);
  }
}
