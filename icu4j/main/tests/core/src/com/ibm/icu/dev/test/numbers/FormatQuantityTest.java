// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.dev.test.numbers;

import java.math.BigDecimal;
import java.math.MathContext;
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
import com.ibm.icu.impl.number.FormatQuantity3;
import com.ibm.icu.impl.number.FormatQuantity4;
import com.ibm.icu.impl.number.Properties;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;

/** TODO: This is a temporary name for this class. Suggestions for a better name? */
public class FormatQuantityTest extends TestFmwk {

  @Test
  public void testBehavior() throws ParseException {

    // Make a list of several formatters to test the behavior of FormatQuantity.
    List<Format> formats = new ArrayList<>();

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
      "0.00026418",
      "0.01789261",
      "468160.0",
      "999000.0",
      "999900.0",
      "999990.0",
      "0.0",
      "12345678901.0",
      "-5193.48",
    };

    String[] hardCases = {
      //      "512.0000000000017",
      //      "4096.000000000001",
      //      "4096.000000000004",
      //      "4096.000000000005",
      //      "4096.000000000006",
      //      "4096.000000000007",
      //      "9999999999999999.0",
      "9999999999999900.0",
      "789000000000000000000000.0",
      "789123123567853156372158.0",
      "987654321987654321987654321987654321987654311987654321.0",
    };

    int i = 0;
    for (String str : cases) {
      testFormatQuantity(i++, str, formats, false);
    }

    i = 0;
    for (String str : hardCases) {
      testFormatQuantity(i++, str, formats, true);
    }
  }

  static void testFormatQuantity(int t, String str, List<Format> formats, boolean bigOnly) {
    List<FormatQuantity> qs = new ArrayList<>();
    BigDecimal d = new BigDecimal(str);
    qs.add(new FormatQuantity1(d));
    if (!bigOnly) qs.add(new FormatQuantity2(d));
    qs.add(new FormatQuantity3(d));
    qs.add(new FormatQuantity4(d));

    if (new BigDecimal(Double.toString(d.doubleValue())).equals(d)) {
      double dv = d.doubleValue();
      qs.add(new FormatQuantity1(dv));
      if (!bigOnly) qs.add(new FormatQuantity2(dv));
      qs.add(new FormatQuantity3(dv));
      qs.add(new FormatQuantity4(dv));
    }

    if (new BigDecimal(Long.toString(d.longValue())).setScale(1).equals(d)) {
      double lv = d.longValue();
      qs.add(new FormatQuantity1(lv));
      if (!bigOnly) qs.add(new FormatQuantity2(lv));
      qs.add(new FormatQuantity3(lv));
      qs.add(new FormatQuantity4(lv));
    }

    testFormatQuantityExpectedOutput(qs.get(0), str);

    if (qs.size() == 1) {
      return;
    }

    for (int i = 1; i < qs.size(); i++) {
      FormatQuantity q0 = qs.get(0);
      FormatQuantity q1 = qs.get(i);
      testFormatQuantityExpectedOutput(q1, str);
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
    for (int m = q0.getUpperDisplayMagnitude(); m >= q0.getLowerDisplayMagnitude(); m--) {
      sb.append(q0.getDigit(m));
      if (m == 0) sb.append('.');
    }
    if (q0.isNegative()) {
      sb.insert(0, '-');
    }
    String actual = sb.toString();
    assertEquals("Unexpected output from simple string conversion (" + q0 + ")", expected, actual);
  }

  private static final MathContext MATH_CONTEXT_HALF_EVEN =
      new MathContext(0, RoundingMode.HALF_EVEN);
  private static final MathContext MATH_CONTEXT_CEILING = new MathContext(0, RoundingMode.CEILING);
  private static final MathContext MATH_CONTEXT_PRECISION =
      new MathContext(3, RoundingMode.HALF_UP);

  private static void testFormatQuantityRounding(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.roundToMagnitude(-1, MATH_CONTEXT_HALF_EVEN);
    q1.roundToMagnitude(-1, MATH_CONTEXT_HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToMagnitude(-1, MATH_CONTEXT_CEILING);
    q1.roundToMagnitude(-1, MATH_CONTEXT_CEILING);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToMagnitude(-1, MATH_CONTEXT_PRECISION);
    q1.roundToMagnitude(-1, MATH_CONTEXT_PRECISION);
    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityRoundingInterval(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();
    q0.roundToInterval(new BigDecimal("0.05"), MATH_CONTEXT_HALF_EVEN);
    q1.roundToInterval(new BigDecimal("0.05"), MATH_CONTEXT_HALF_EVEN);
    testFormatQuantityBehavior(q0, q1);

    q0 = rq0.clone();
    q1 = rq1.clone();
    q0.roundToInterval(new BigDecimal("0.05"), MATH_CONTEXT_CEILING);
    q1.roundToInterval(new BigDecimal("0.05"), MATH_CONTEXT_CEILING);
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
        "Different upper range of digits (" + q0 + ", " + q1 + ")",
        q0.getUpperDisplayMagnitude(),
        q1.getUpperDisplayMagnitude());

    assertDoubleEquals(
        "Different double values (" + q0 + ", " + q1 + ")", q0.toDouble(), q1.toDouble());

    assertBigDecimalEquals(
        "Different BigDecimal values (" + q0 + ", " + q1 + ")",
        q0.toBigDecimal(),
        q1.toBigDecimal());

    int equalityDigits = Math.min(q0.maxRepresentableDigits(), q1.maxRepresentableDigits());
    for (int m = q0.getUpperDisplayMagnitude(), i = 0;
        m >= Math.min(q0.getLowerDisplayMagnitude(), q1.getLowerDisplayMagnitude())
            && i < equalityDigits;
        m--, i++) {
      assertEquals(
          "Different digit at magnitude " + m + " (" + q0 + ", " + q1 + ")",
          q0.getDigit(m),
          q1.getDigit(m));
    }

    if (rq0 instanceof FormatQuantity4) {
      String message = ((FormatQuantity4) rq0).checkHealth();
      if (message != null) errln(message);
    }
    if (rq1 instanceof FormatQuantity4) {
      String message = ((FormatQuantity4) rq1).checkHealth();
      if (message != null) errln(message);
    }
  }

  @Test
  public void testAppend() {
    FormatQuantity4 fq = new FormatQuantity4();
    fq.appendDigit((byte) 1, 0, true);
    assertBigDecimalEquals("Failed on append", "1.", fq.toBigDecimal());
    fq.appendDigit((byte) 2, 0, true);
    assertBigDecimalEquals("Failed on append", "12.", fq.toBigDecimal());
    fq.appendDigit((byte) 3, 1, true);
    assertBigDecimalEquals("Failed on append", "1203.", fq.toBigDecimal());
    fq.appendDigit((byte) 0, 1, true);
    assertBigDecimalEquals("Failed on append", "120300.", fq.toBigDecimal());
    fq.appendDigit((byte) 4, 0, true);
    assertBigDecimalEquals("Failed on append", "1203004.", fq.toBigDecimal());
    fq.appendDigit((byte) 0, 0, true);
    assertBigDecimalEquals("Failed on append", "12030040.", fq.toBigDecimal());
    fq.appendDigit((byte) 5, 0, false);
    assertBigDecimalEquals("Failed on append", "12030040.5", fq.toBigDecimal());
    fq.appendDigit((byte) 6, 0, false);
    assertBigDecimalEquals("Failed on append", "12030040.56", fq.toBigDecimal());
    fq.appendDigit((byte) 7, 3, false);
    assertBigDecimalEquals("Failed on append", "12030040.560007", fq.toBigDecimal());
  }

  static void assertDoubleEquals(String message, double d1, double d2) {
    boolean equal = (Math.abs(d1 - d2) < 1e-6) || (Math.abs((d1 - d2) / d1) < 1e-6);
    handleAssert(equal, message, d1, d2, null, false);
  }

  static void assertBigDecimalEquals(String message, String d1, BigDecimal d2) {
    assertBigDecimalEquals(message, new BigDecimal(d1), d2);
  }

  static void assertBigDecimalEquals(String message, BigDecimal d1, BigDecimal d2) {
    boolean equal = d1.compareTo(d2) == 0;
    handleAssert(equal, message, d1, d2, null, false);
  }
}
