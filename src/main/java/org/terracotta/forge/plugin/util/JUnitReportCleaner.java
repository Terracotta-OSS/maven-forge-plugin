/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
 */
package org.terracotta.forge.plugin.util;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * A tool to clean up junit report for elements that we're not interested in
 * like "properties" "system-out" and "system-err"
 * 
 * If the report has syntax error or zero in length, the report will be replaced
 * with a generic error report
 * 
 * @author hhuynh
 * 
 */
public class JUnitReportCleaner extends DefaultHandler {
  private final Set<String>   UNWANTED_ELEMENTS  = new HashSet<String>();
  private final Pattern       CLASSNAME_FROM_XML = Pattern
                                                     .compile("TEST-(.*)\\.xml");
  private final Pattern       CLASSNAME_FROM_TXT = Pattern
                                                     .compile("(.*)\\.txt");
  private static final String DEAULT_REPORT      = "<?xml version='1.0' encoding='UTF-8'?>\n"
                                                     + "<testsuite errors='0' failures='1' name='CLASSNAME' tests='1' time='0.000'>\n"
                                                     + "  <testcase classname='CLASSNAME' name='test' time='0.0'>\n"
                                                     + "    <failure type='junit.framework.AssertionFailedError' message='Failed'>\n"
                                                     + "      Test has timeout or crashed. Please check logs for details.\n"
                                                     + "    </failure>\n"
                                                     + "  </testcase>\n"
                                                     + "  <system-out />\n"
                                                     + "  <system-err />\n"
                                                     + "</testsuite>\n";

  private StringBuilder       reportBuffer;
  private StringBuilder       currentText;
  private int                 deleteDepth;
  private int                 failCount;
  private final Log           log;

  public JUnitReportCleaner(Log log) {
    this.log = log;
    UNWANTED_ELEMENTS.add("properties");
    UNWANTED_ELEMENTS.add("system-out");
    UNWANTED_ELEMENTS.add("system-err");
  }

  public void cleanReport(File report) {
    if (!report.exists()) {
      throw new RuntimeException("JUnit report " + report + " doesn't exist");
    }
    
    String className = getClassname(report.getName());
    
    if (report.length() == 0L) {
      createDefaultReport(report, className);
      return;
    }

    reset(report);

    SAXParserFactory factory = SAXParserFactory.newInstance();
    SAXParser parser;
    try {
      parser = factory.newSAXParser();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    } catch (SAXException e) {
      throw new RuntimeException(e);
    }

    try {
      parser.parse(report, this);
      Writer writer = null;
      try {
        writer = new PrintWriter(report);
        IOUtils.write(reportBuffer.toString(), writer);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        IOUtils.closeQuietly(writer);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (SAXException e) {
      createDefaultReport(report, className);
    }
  }

  public void createDefaultReport(File report, String className) {
    String defaultReport = DEAULT_REPORT.replace("CLASSNAME", className);
    Writer writer = null;
    try {
      writer = new PrintWriter(report);
      IOUtils.write(defaultReport, writer);
      log.info("TEST " + className + " FAILED.");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      IOUtils.closeQuietly(writer);
    }
  }

  public String getClassname(String filename) {
    Matcher matcher = null;
    if (filename.endsWith(".xml")) {
      matcher = CLASSNAME_FROM_XML.matcher(filename);
    } else if (filename.endsWith(".txt")) {
      matcher = CLASSNAME_FROM_TXT.matcher(filename);
    }

    if (matcher.matches()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }

  private void reset(File report) {
    reportBuffer = new StringBuilder();
    deleteDepth = 0;
    failCount = 0;
  }

  @Override
  public void startDocument() throws SAXException {
    reportBuffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
  }

  @Override
  public void endDocument() throws SAXException {
    // nothing yet
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    if (deleteDepth == 0) {
      String s = new String(ch, start, length);
      if (s.trim().length() == 0)
        return;
      currentText.append(escapeXML(s));
    }
  }

  @Override
  public void startElement(String uri, String localName, String name,
      Attributes attributes) throws SAXException {
    currentText = new StringBuilder();
    
    if ("testsuite".equals(name)) {
      String errors = attributes.getValue("errors");
      String failures = attributes.getValue("failures");
      if (errors != null) {
        failCount += Integer.valueOf(errors);
      }
      if (failures != null) {
        failCount += Integer.valueOf(failures);
      }
    }

    if (UNWANTED_ELEMENTS.contains(name)) {
      deleteDepth++;
    }

    if (deleteDepth == 0) {
      reportBuffer.append("<" + name + " ");
      for (int i = 0; i < attributes.getLength(); i++) {
        reportBuffer.append(attributes.getQName(i) + "=\""
            + escapeXML(attributes.getValue(i)) + "\" ");
      }
      reportBuffer.append(">\n");
    }
  }

  @Override
  public void endElement(String uri, String localName, String name)
      throws SAXException {
    if (deleteDepth == 0) {
      if (currentText != null && currentText.length() > 0) {
        reportBuffer.append(currentText.toString()).append("\n");
      }
      reportBuffer.append("</" + name + ">\n");
      currentText = null;
    }

    if (UNWANTED_ELEMENTS.contains(name)) {
      deleteDepth--;
    }
  }

  private static String escapeXML(String s) {
    StringBuilder result = new StringBuilder();
    StringCharacterIterator iterator = new StringCharacterIterator(s);
    char character = iterator.current();
    while (character != CharacterIterator.DONE) {
      if (character == '<') {
        result.append("&lt;");
      } else if (character == '>') {
        result.append("&gt;");
      } else if (character == '\"') {
        result.append("&quot;");
      } else if (character == '\'') {
        result.append("&#039;");
      } else if (character == '&') {
        result.append("&amp;");
      } else {
        result.append(character);
      }
      character = iterator.next();
    }
    return result.toString();
  }
}
