// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.impl.number.formatters.PaddingFormat.PaddingLocation;
import com.ibm.icu.impl.number.formatters.RangeFormat;
import com.ibm.icu.impl.number.modifiers.SimpleModifier;
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.util.MeasureUnit;

public class demo {

  public static void main(String[] args) throws ParseException {
    SimpleModifier.testFormatAsPrefixSuffix();

    System.out.println(new FormatQuantity1(3.14159));
    System.out.println(new FormatQuantity1(3.14159, true));
    System.out.println(new FormatQuantity2(3.14159));

    System.out.println(
        PatternString.propertiesToString(PatternString.parseToProperties("+**##,##,#00.05#%")));

    ParsePosition ppos = new ParsePosition(0);
    System.out.println(
        Parse.parse(
            "dd123",
            ppos,
            Parse.ParseMode.STRICT,
            false,
            false,
            new Properties().setPositivePrefix("dd").setNegativePrefix("ddd"),
            DecimalFormatSymbols.getInstance()));
    System.out.println(ppos);

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
        new Properties().setPaddingWidth(10).setPaddingLocation(PaddingLocation.AFTER_PREFIX);
    Format pdf = Endpoint.fromBTA(properties);
    formats.add(pdf);

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

    properties = new Properties().setMeasureUnit(MeasureUnit.HECTARE);
    Format muf = Endpoint.fromBTA(properties);
    formats.add(muf);

    properties =
        new Properties().setMeasureUnit(MeasureUnit.HECTARE).setCompactStyle(CompactStyle.LONG);
    Format cmf = Endpoint.fromBTA(properties);
    formats.add(cmf);

    properties = PatternString.parseToProperties("#,##0.00 \u00a4");
    Format ptf = Endpoint.fromBTA(properties);
    formats.add(ptf);

    RangeFormat rf = new RangeFormat(cdf, cdf, " to ");
    System.out.println(rf.format(new FormatQuantity2(1234), new FormatQuantity2(2345)));

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
      System.out.println("----------");
      System.out.println(str);
      System.out.println("  NDF: " + ndf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  CDF: " + cdf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  PWD: " + pdf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  EXF: " + exf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  RIF: " + rif.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  MUF: " + muf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  CMF: " + cmf.format(new FormatQuantity2(Double.parseDouble(str))));
      System.out.println("  PTF: " + ptf.format(new FormatQuantity2(Double.parseDouble(str))));
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
    assert expected.equals(actual);
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

    //    q0 = rq0.clone();
    //    q1 = rq1.clone();
    //    q0.divideBy(new BigDecimal("3.14159"));
    //    q1.divideBy(new BigDecimal("3.14159"));
    //    testFormatQuantityBehavior(q0, q1);
  }

  private static void testFormatQuantityWithFormats(
      FormatQuantity rq0, FormatQuantity rq1, List<Format> formats) {
    for (Format format : formats) {
      FormatQuantity q0 = rq0.clone();
      FormatQuantity q1 = rq1.clone();
      String s1 = format.format(q0);
      String s2 = format.format(q1);
      assert s1.equals(s2);
    }
  }

  private static void testFormatQuantityBehavior(FormatQuantity rq0, FormatQuantity rq1) {
    FormatQuantity q0 = rq0.clone();
    FormatQuantity q1 = rq1.clone();

    assert q0.isNegative() == q1.isNegative();
    assert q0.getPositionFingerprint() == q1.getPositionFingerprint();

    assert q0.integerCount() == q1.integerCount();
    // Equality is guaranteed for only 16 digits
    int guaranteed = Math.max(16 - q0.integerCount(), 0);
    assert q0.fractionCount() == q1.fractionCount()
        || (q0.fractionCount() >= guaranteed && q1.fractionCount() >= guaranteed);
    for (int i = 0; i < q0.integerCount() && guaranteed > 0; i++) {
      assert q0.getIntegerDigit(i) == q1.getIntegerDigit(i);
      guaranteed--;
    }
    for (int i = 0; i < q0.fractionCount() && guaranteed > 0; i++) {
      assert q0.getFractionDigit(i) == q1.getFractionDigit(i);
      guaranteed--;
    }
  }
}
