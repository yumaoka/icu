// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.dev.test.numbers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ibm.icu.impl.number.PatternString;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.util.ULocale;

/** @author sffc */
public class PatternStringTest {

  @Test
  public void testLocalized() {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(ULocale.ENGLISH);
    symbols.setDecimalSeparatorString("a");
    symbols.setPercentString("b");

    String standard = "#,##0.0%'a%'";
    String localized = "#,##0a0b'a%'";

    assertEquals(localized, PatternString.convertLocalized(standard, symbols, true));
    assertEquals(standard, PatternString.convertLocalized(localized, symbols, false));
  }
}
