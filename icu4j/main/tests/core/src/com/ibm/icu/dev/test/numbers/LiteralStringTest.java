// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.dev.test.numbers;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;

import org.junit.Test;

import com.ibm.icu.impl.number.LiteralString;
import com.ibm.icu.impl.number.NumberStringBuilder;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.util.ULocale;

/** @author sffc */
public class LiteralStringTest {

  @Test
  public void test() throws ParseException {
    Object[][] cases = {
      {"", false, 0, ""},
      {"abc", false, 3, "abc"},
      {"-", false, 1, "−"},
      {"-!", false, 2, "−!"},
      {"+", false, 1, "\u061C+"},
      {"+!", false, 2, "\u061C+!"},
      {"‰", false, 1, "؉"},
      {"‰!", false, 2, "؉!"},
      {"-x", false, 2, "−x"},
      {"'-'x", false, 2, "-x"},
      {"'--''-'-x", false, 6, "--'-−x"},
      {"'", false, 1, "'"},
      {"''", false, 1, "'"},
      {"''''", false, 2, "''"},
      {"''''''", false, 3, "'''"},
      {"''x''", false, 3, "'x'"},
      {"¤", true, 1, "$"},
      {"¤¤", true, 2, "XXX"},
      {"¤¤¤", true, 3, "long name"},
      {"¤¤¤¤", true, 4, "long name$"},
      {"¤¤¤¤¤", true, 5, "long nameXXX"},
      {"¤!", true, 2, "$!"},
      {"¤¤!", true, 3, "XXX!"},
      {"¤¤¤!", true, 4, "long name!"},
      {"-¤¤", true, 3, "−XXX"},
      {"¤¤-", true, 3, "XXX−"},
      {"'¤'", false, 1, "¤"},
      {"%", false, 1, "٪\u061C"},
      {"'%'", false, 1, "%"},
      {"¤'-'%", true, 3, "$-٪\u061C"}
    };

    // ar_SA has an interesting percent sign and various Arabic letter marks
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new ULocale("ar_SA"));
    NumberStringBuilder sb = new NumberStringBuilder();

    for (Object[] cas : cases) {
      String input = (String) cas[0];
      boolean curr = (Boolean) cas[1];
      int length = (Integer) cas[2];
      String output = (String) cas[3];

      assertEquals("Currency on <" + input + ">", curr, LiteralString.hasCurrencySymbols(input));
      assertEquals("Length on <" + input + ">", length, LiteralString.unescapedLength(input));

      sb.clear();
      LiteralString.unescape(input, symbols, "$", "XXX", "long name", "−", sb);
      assertEquals("Output on <" + input + ">", output, sb.toString());

      sb.clear();
      LiteralString.unescape2(input, symbols, "$", "XXX", "long name", "−", sb);
      assertEquals("Output on <" + input + ">", output, sb.toString());

      sb.clear();
      LiteralString.unescape3(input, symbols, "$", "XXX", "long name", "−", sb);
      assertEquals("Output on <" + input + ">", output, sb.toString());
    }
  }
}
