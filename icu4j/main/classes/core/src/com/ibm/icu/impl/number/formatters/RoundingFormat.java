// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number.formatters;

import com.ibm.icu.impl.number.Rounder;
import com.ibm.icu.impl.number.Rounder.IBasicRoundingProperties;
import com.ibm.icu.impl.number.rounders.IntervalRounder;
import com.ibm.icu.impl.number.rounders.MagnitudeRounder;
import com.ibm.icu.impl.number.rounders.NoRounder;
import com.ibm.icu.impl.number.rounders.SignificantDigitsRounder;

// TODO: Figure out a better place to put these methods.

public class RoundingFormat {

  public static interface IProperties
      extends IBasicRoundingProperties,
          IntervalRounder.IProperties,
          MagnitudeRounder.IProperties,
          SignificantDigitsRounder.IProperties {}

  public static Rounder getDefaultOrNoRounder(IProperties properties) {
    Rounder candidate = getDefaultOrNull(properties);
    if (candidate == null) {
      candidate = NoRounder.getInstance(properties);
    }
    return candidate;
  }

  public static Rounder getDefaultOrNull(IProperties properties) {
    if (SignificantDigitsRounder.useSignificantDigits(properties)) {
      return SignificantDigitsRounder.getInstance(properties);
    } else if (IntervalRounder.useRoundingInterval(properties)) {
      return IntervalRounder.getInstance(properties);
    } else if (MagnitudeRounder.useFractionFormat(properties)) {
      return MagnitudeRounder.getInstance(properties);
    } else {
      return null;
    }
  }
}
