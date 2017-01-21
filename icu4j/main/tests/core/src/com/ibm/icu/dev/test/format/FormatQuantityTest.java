// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.dev.test.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.impl.number.Endpoint;
import com.ibm.icu.impl.number.Format;
import com.ibm.icu.impl.number.FormatQuantity;
import com.ibm.icu.impl.number.FormatQuantity1;
import com.ibm.icu.impl.number.FormatQuantity2;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;

/** TODO: This is a temporary name for this class. Suggestions for a better name? */
public class FormatQuantityTest extends TestFmwk {

  @Test
  public void test() throws ParseException {

    // Make a list of several formatters to test the behavior of FormatQuantity.
    List<Format> formats = new ArrayList<Format>();

    Properties properties = new Properties();
    Format ndf = Endpoint.fromBTA(properties);
    formats.add(ndf);

    properties =
        new Properties()
            .setMinimumSignificantDigits(3)
            .setMaximumSignificantDigits(3)
            .setCompactStyle(CompactStyle.LONG);
    Format cdf = Endpoint.fromBTA(properties);
    formats.add(cdf);

    properties =
        new Properties()
            .setExponentDigits(1)
            .setMaximumIntegerDigits(3)
            .setMaximumFractionDigits(1);
    Format exf = Endpoint.fromBTA(properties);
    formats.add(exf);

    properties = new Properties().setRoundingInterval(new BigDecimal("0.5"));
    Format rif = Endpoint.fromBTA(properties);
    formats.add(rif);

    String[] cases = {
            "1.0",
            "2.01",
            "1234.56",
            "3000.0",
            //      "512.0000000000017",
            //      "4096.000000000001",
            //      "4096.000000000004",
            //      "4096.000000000005",
            //      "4096.000000000006",
            //      "4096.000000000007",
            "0.00026418",
            "0.01789261",
            "468160.0",
            "999000.0",
            "999900.0",
            "999990.0",
            "0.0",
            "12345678901.0",
            //      "789000000000000000000000.0",
            //      "789123123567853156372158.0",
            "-5193.48",
          };

    int i = 0;
    for (String str : cases) {
      testFormatQuantity(i++, str, formats);
    }
  }

  static void testFormatQuantity(int t, String str, List<Format> formats) {
    List<FormatQuantity> qs = new ArrayList<FormatQuantity>();
    BigDecimal d = new BigDecimal(str);
    qs.add(new FormatQuantity1(d));
    qs.add(new FormatQuantity2(d));

    if (new BigDecimal(Double.toString(d.doubleValue())).equals(d)) {
      double dv = d.doubleValue();
      qs.add(new FormatQuantity1(dv));
      qs.add(new FormatQuantity2(dv));
    }

    if (new BigDecimal(Long.toString(d.longValue())).setScale(1).equals(d)) {
      double lv = d.longValue();
      qs.add(new FormatQuantity1(lv));
      qs.add(new FormatQuantity2(lv));
    }

    testFormatQuantityExpectedOutput(qs.get(0), str);

    if (qs.size() == 1) {
      return;
    }

    for (int i = 1; i < qs.size(); i++) {
      FormatQuantity q0 = qs.get(0);
      FormatQuantity q1 = qs.get(i);
      testFormatQuantityExpectedOutput(q1, str);
      testFormatQuantitySignificantDigits(q0, q1);
      testFormatQuantityRounding(q0, q1);
      testFormatQuantityRoundingInterval(q0, q1);
      testFormatQuantityMath(q0, q1);
      testFormatQuantityWithFormats(q0, q1, formats);
    }
  }

  private static void testFormatQuantityExpectedOutput(FormatQuantity rq, String expected) {
    StringBuilder sb = new StringBuilder();
    FormatQuantity q0 = rq.clone();
    q0.setIntegerFractionLength(1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    for (int m = 0; m < q0.integerCount(); m++) {
      sb.insert(0, "" + q0.getIntegerDigit(m));
    }
    sb.append('.');
    for (int m = 0; m < q0.fractionCount(); m++) {
      sb.append("" + q0.getFractionDigit(m));
    }
    if (q0.isNegative()) {
      sb.insert(0, '-');
    }
    String actual = sb.toString();
    assertEquals("Unexpected output from simple string conversion (" + q0 + ")", expected, actual);
  }

  private static void testFormatQuantitySignificantDigits(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.roundToSignificantDigits(2, 4, RoundingMode.HALF_EVEN);
    q1.roundToSignificantDigits(2, 4, RoundingMode.HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToSignificantDigits(3, 3, RoundingMode.HALF_EVEN);
    q1.roundToSignificantDigits(3, 3, RoundingMode.HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityRounding(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.roundToMagnitude(-1, RoundingMode.HALF_EVEN);
    q1.roundToMagnitude(-1, RoundingMode.HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToMagnitude(-1, RoundingMode.CEILING);
    q1.roundToMagnitude(-1, RoundingMode.CEILING);
    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityRoundingInterval(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.roundToInterval(new BigDecimal("0.05"), RoundingMode.HALF_EVEN);
    q1.roundToInterval(new BigDecimal("0.05"), RoundingMode.HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToInterval(new BigDecimal("0.05"), RoundingMode.CEILING);
    q1.roundToInterval(new BigDecimal("0.05"), RoundingMode.CEILING);
    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityMath(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.adjustMagnitude(-3);
    q1.adjustMagnitude(-3);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.multiplyBy(new BigDecimal("3.14159"));
    q1.multiplyBy(new BigDecimal("3.14159"));
    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityWithFormats(
      FormatQuantity rq0, FormatQuantity rq1, List<Format> formats) {
    for (Format format : formats) {
      FormatQuantity q0 = rq0.clone();
      FormatQuantity q1 = rq1.clone();
      String s1 = format.format(q0);
      String s2 = format.format(q1);
      assertEquals("Different output from formatter (" + q0 + ", " + q1 + ")", s1, s2);
    }
  }

  private static void testFormatQuantityBehavior(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();

    assertEquals("Different sign (" + q0 + ", " + q1 + ")", q0.isNegative(), q1.isNegative());

    assertEquals(
        "Different fingerprint (" + q0 + ", " + q1 + ")",
        q0.getPositionFingerprint(),
        q1.getPositionFingerprint());

    assertEquals(
        "Different number of integer digits (" + q0 + ", " + q1 + ")",
        q0.integerCount(),
        q1.integerCount());

    // Equality is guaranteed for only 16 digits
    int guaranteed = Math.max(16 - q0.integerCount(), 0);
    assertTrue(
        "Different number of fraction digits (" + q0 + ", " + q1 + ")",
        (q0.fractionCount() == q1.fractionCount()
            || (q0.fractionCount() >= guaranteed && q1.fractionCount() >= guaranteed)));

    for (int i = 0; i < q0.integerCount() && guaranteed > 0; i++) {
      assertEquals(
          "Different integer digit at index " + i + " (" + q0 + ", " + q1 + ")",
          q0.getIntegerDigit(i),
          q1.getIntegerDigit(i));
      guaranteed--;
    }

    for (int i = 0; i < q0.fractionCount() && guaranteed > 0; i++) {
      assertEquals(
          "Different fraction digit at index " + i + " (" + q0 + ", " + q1 + ")",
          q0.getFractionDigit(i),
          q1.getFractionDigit(i));
      guaranteed--;
    }
  }
}
