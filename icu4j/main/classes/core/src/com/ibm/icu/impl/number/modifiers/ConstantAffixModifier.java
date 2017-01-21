// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.modifiers;

import com.ibm.icu.impl.number.DoubleSidedStringBuilder;
import com.ibm.icu.impl.number.Modifier;
import com.ibm.icu.impl.number.Properties;

/** The canonical implementation of {@link Modifier}, containing a prefix and suffix string. */
public class ConstantAffixModifier extends Modifier.BaseModifier {
  // TODO: Avoid making a new ConstantAffixModifier by default if prefix and suffix are empty
  public static final ConstantAffixModifier EMPTY = new ConstantAffixModifier("", "");

  // Although these strings are public, they are read-only because they are final.
  public final String prefix;
  public final String suffix;

  /**
   * Constructs an instance with the given strings.
   *
   * <p>The arguments need to be Strings, not CharSequences, because Strings are immutable but
   * CharSequences are not.
   *
   * @param prefix The prefix string.
   * @param suffix The suffix string.
   */
  public ConstantAffixModifier(String prefix, String suffix) {
    // Use an empty string instead of null if we are given null
    this.prefix = (prefix == null ? "" : prefix);
    this.suffix = (suffix == null ? "" : suffix);
  }

  @Override
  public int apply(DoubleSidedStringBuilder output, int leftIndex, int rightIndex) {
    // Insert the suffix first since inserting the prefix will change the rightIndex
    output.insert(rightIndex, suffix);
    output.insert(leftIndex, prefix);
    return prefix.length() + suffix.length();
  }

  @Override
  public int length() {
    return prefix.length() + suffix.length();
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
