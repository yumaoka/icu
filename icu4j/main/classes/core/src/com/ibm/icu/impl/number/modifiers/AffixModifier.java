// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.modifiers;

import com.ibm.icu.impl.number.Format;
import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.Modifier;
import com.ibm.icu.impl.number.ModifierHolder;
import com.ibm.icu.impl.number.Properties;

/** A class containing a positive form and a negative form of {@link ConstantAffixModifier}. */
public class AffixModifier extends Format.BeforeFormat
    implements Modifier.PositiveNegativeModifier {
  private final ConstantAffixModifier positive;
  private final ConstantAffixModifier negative;

  /**
   * Constructs an instance using the two {@link ConstantAffixModifier} classes for positive and
   * negative.
   *
   * @param positive The positive-form Modifier.
   * @param negative The negative-form Modifier.
   */
  public AffixModifier(ConstantAffixModifier positive, ConstantAffixModifier negative) {
    this.positive = positive;
    this.negative = negative;
  }

  @Override
  public Modifier getModifier(boolean isNegative) {
    return isNegative ? negative : positive;
  }

  @Override
  public void before(FormatQuantity input, ModifierHolder mods) {
    Modifier mod = getModifier(input.isNegative());
    mods.add(mod);
  }

  @Override
  public void export(Properties properties) {
    properties.setPositivePrefix(positive.prefix);
    properties.setPositiveSuffix(positive.suffix);
    properties.setNegativePrefix(negative.prefix);
    properties.setNegativeSuffix(negative.suffix);
  }
}
