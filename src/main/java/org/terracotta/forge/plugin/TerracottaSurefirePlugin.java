/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.plugin.surefire.log.PluginConsoleLogger;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.suite.RunResult;

import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
public class TerracottaSurefirePlugin extends SurefirePlugin {

  @Parameter(property = "cleanJunitReports", defaultValue = "true")
  private boolean cleanJunitReports;

  @Parameter(property = "listFile")
  private File    listFile;

  @Parameter(property = "poundTimes", defaultValue = "1")
  private int     poundTimes;

  @Parameter(property = "poundEmail", required = false)
  private String  poundEmail;

  @Parameter(property = "devLog", defaultValue = "false")
  private boolean devLog;

  @Parameter(property = "useReflectionFile", defaultValue = "false")
  private boolean useReflectionFile;

  /**
   * A full toolchain specification block, eg:
   * <jdk>
   *     <version>X</version>
   *     <vendor>Y</vendor>
   *     ...
   * </jdk>
   * All items are optional, if unspecified defaults to normal surefire behavior.
   * If specified, changes the jdk used by surefire to a matching toolchain in maven's toolchains.xml
   * If the requirements are not satisfied, fails the build.
   */
  @Parameter(alias = "jdk")
  private Map<String, String> toolchainSpec;


  private String jvmFromToolchain;

  /**
   * Force SurefirePlugin to use our jvm if
   * <toolchain></toolchain> has been configured explicitly.
   */
  @Override
  public String getJvm() {
    if (jvmFromToolchain != null) {
      return jvmFromToolchain;
    }
    return super.getJvm();
  }

  /**
   * Adds configurable toolchains support, allowing to specify toolchains to
   * use for just this plugin execution.
   *
   * if a <toolchains></toolchains> block is provided in configuration,
   * find the first matching toolchain and set #jvmFromToolchain so that
   * our overridden {@link #getJvm()} can return it to SurefirePlugin.
   *
   * @throws MojoExecutionException
   */
  private void findMatchingToolchain() throws MojoExecutionException {

    if ( toolchainSpec !=null && toolchainSpec.size() > 0 && getToolchainManager() != null ) {
      List<Toolchain> toolchains = getToolchainManager().getToolchains(getSession(), "jdk", toolchainSpec);
      if (toolchains.size() > 0) {
        Toolchain selectedToolchain = toolchains.get(0);
        jvmFromToolchain = selectedToolchain.findTool("java");
        if (!new File(jvmFromToolchain).canExecute()) {
          throw new MojoExecutionException("Identified matching toolchain " + jvmFromToolchain
                  + " but it is not an executable file");
        }
        getLog().info("Using jvm configured via toolchains : " + jvmFromToolchain
                + " because " + selectedToolchain + " matched " + toolchainSpec);
      } else {
        throw new MojoExecutionException("Unable to find a matching toolchain for configuration " + toolchainSpec);
      }
    }
  }


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    findMatchingToolchain();

    int absoluteTimeoutSecs = 0;
    try {
      absoluteTimeoutSecs = Integer.valueOf(getProject().getProperties().getProperty("absolute-test-timeout-secs"));
    } catch (Exception e) {
      // ignore
    }

    if (absoluteTimeoutSecs != this.getForkedProcessTimeoutInSeconds()) {
      getLog().info("Overwriting test timeout using absolute-test-timeout-secs: " + absoluteTimeoutSecs + " seconds");
      this.setForkedProcessTimeoutInSeconds(absoluteTimeoutSecs);
    }

    if (devLog) {
      File currentDir = super.getWorkingDirectory();
      if (currentDir == null) {
        currentDir = new File(".");
      }
      File devLog4jFile = new File(currentDir, ".tc.dev.log4j.properties");
      if (!devLog4jFile.exists()) {
        try {
          devLog4jFile.getParentFile().mkdirs();
          if (!devLog4jFile.createNewFile()) throw new IOException("createNewFile return false");
        } catch (IOException e1) {
          getLog().error(e1);
          throw new MojoExecutionException("Failed to create " + devLog4jFile);
        }
      }
    }

    try {
      // recheck should_skip_test maven propperty to decide if tests should be
      // skipped
      String shouldSkipTestsValue = getProject().getProperties().getProperty("should_skip_tests");
      if (shouldSkipTestsValue != null) {
        getLog().warn("'should_skip_tests' property found, value is " + shouldSkipTestsValue
                          + ". This value overrides the 'skipTests' original setting.");
        boolean shouldSkipTests = Boolean.valueOf(shouldSkipTestsValue);
        this.setSkipTests(shouldSkipTests);
      }

      // pre-scan groups
      File reflectionFile = new File(getProject().getBuild().getDirectory(), "reflections.xml");
      if (useReflectionFile && reflectionFile.exists()) {
        List<String> includeList;
        try {
          includeList = getCategorizedTests(reflectionFile);
          if (includeList.size() == 0) {
            // add some fake classname here to trick surefire into NOT scanning
            // all tests
            includeList.add("**/FAKEFAKEFAKE.java");
          } else {
            getLog().info("Including these tests found in " + reflectionFile + " file");
            getLog().info(includeList.toString());
          }
          this.setIncludes(includeList);
        } catch (DocumentException e) {
          throw new MojoExecutionException(e.getMessage());
        }
      }

      // handle listFile
      if (listFile != null) {
        if (!listFile.exists()) {
          getLog().warn("listFile '" + listFile + "' specified but does not exist. No tests will be run");
          return;
        }
        getLog().info("Running tests found in file " + listFile);
        FileInputStream input = null;
        try {
          input = new FileInputStream(listFile);
          List<String> line = IOUtils.readLines(input);
          List<String> includeList = new ArrayList<String>();
          for (String test : line) {
            test = test.trim();
            if (test.length() == 0 || test.startsWith("#")) {
              continue;
            }
            if (!test.endsWith(".java")) {
              test += ".java";
            }
            includeList.add("**/" + test);
          }
          getLog().info("Tests to run: " + includeList);
          this.setIncludes(includeList);
        } catch (IOException e) {
          throw new MojoExecutionException(e.getMessage());
        } finally {
          IOUtils.closeQuietly(input);
        }
      }

      if (poundTimes > 1) {
        if (this.getTest() == null) {
          getLog().error("poundTimes was set but -Dtest isn't");
          throw new MojoFailureException("poundTimes was set but -Dtest isn't");
        } else {
          for (int i = 1; i <= poundTimes; i++) {
            getLog().info("* POUNDING ITERATION: " + i);
            try {
              super.execute();
            } catch (Exception e) {
              getLog().error("Test failed after iteration #" + i);
              poundAlertIfNeeded("Pounding finished: " + this.getTest() + " failed", "Test " + this.getTest()
                                                                                     + " failed after iteration #" + i);
              if (e instanceof MojoExecutionException) {
                throw (MojoExecutionException) e;
              } else if (e instanceof MojoFailureException) {
                throw (MojoFailureException) e;
              } else {
                throw new MojoExecutionException("Failed", e);
              }
            }
          }
        }
        // done pounding, exit
        getLog().info("*** Pounded " + poundTimes + " times! Test passed.");
        poundAlertIfNeeded("Pounding finished: " + this.getTest() + " passed", "Pounded " + poundTimes
                                                                               + " times! Test " + this.getTest()
                                                                               + " passed");
      } else {
        // invoke surefire plugin normally
        super.execute();
      }

    } catch (MojoExecutionException e) {
      if (e.getCause() instanceof SurefireBooterForkException) {
        // test timeout, don't throw this exception
        // so Jenkins could parse JUnit reports and treat timeout failures as
        // regular failures
	  getLog().error(e);
        PluginConsoleLogger logger = new PluginConsoleLogger(new ConsoleLogger(Logger.LEVEL_ERROR, "console"));
        SurefireHelper.reportExecution(this, new RunResult(0, 0, 0, 0, "timeout", true), logger, null);
      } else {
        throw e;
      }
    } finally {
      if (cleanJunitReports) {
        getLog().info("Fix Junit reports if needed");
        FixJUnitReportMojo fixUnitReportMojo = new FixJUnitReportMojo();
        fixUnitReportMojo.setPluginContext(getPluginContext());
        fixUnitReportMojo.setProject(getProject());
        fixUnitReportMojo.execute();
      }
    }
  }

  private void poundAlertIfNeeded(String subject, String text) throws MojoExecutionException {
    if (poundEmail == null) return;
    try {
      Properties sendmailProps = new Properties();
      File mailProps = new File(System.getProperty("user.home"), ".tc/poundmail.properties");
      if (!mailProps.exists()) {
        sendmailProps = getDefaultSendMailProps();
        sendmailProps.list(System.out);
        getLog().info(mailProps.getCanonicalPath()
                          + " doesn't exist. Using default SAG smtp, you'll need to be in VPN to be able to send mail");
      } else {
        sendmailProps.load(new FileReader(mailProps));
      }

      final String username = sendmailProps.getProperty("username");
      final String password = sendmailProps.getProperty("password");

      Authenticator auth = null;
      if (username != null && password != null) {
        auth = new javax.mail.Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password);
          }
        };
      }
      Session mailSession = Session.getInstance(sendmailProps, auth);
      Message message = new MimeMessage(mailSession);
      message.setFrom(new InternetAddress("pounder@terracottatech.com"));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(poundEmail));
      message.setSubject(subject);
      message.setText(text);
      Transport.send(message);
      getLog().info("Sent result email to " + poundEmail);

    } catch (Exception e) {
      throw new MojoExecutionException("Error emailing", e);
    }
  }

  private static Properties getDefaultSendMailProps() {
    Properties props = new Properties();
    props.setProperty("mail.smtp.host", "hqcas.eur.ad.sag");
    props.setProperty("mail.smtp.port", "25");
    return props;
  }

  @Override
  protected boolean hasExecutedBefore() {
    // if we're pounding test, we have to lie so that the test can be run again
    if (poundTimes > 1) return false;
    return super.hasExecutedBefore();
  }

  private List<String> getCategorizedTests(File reflectionFile) throws DocumentException {
    Document doc = new SAXReader().read(reflectionFile);
    List<Node> list = doc.selectNodes("//Reflections/TypeAnnotationsScanner/entry");
    List<String> result = new ArrayList<String>();
    for (Node node : list) {
      Element entry = (Element) node;
      Element key = entry.element("key");
      if (key.getText().equals("org.junit.experimental.categories.Category")) {
        Element values = entry.element("values");
        for (Iterator it = values.elements().iterator(); it.hasNext();) {
          Element value = (Element) it.next();
          String className = value.getText();
          result.add("**/" + className.substring(className.lastIndexOf(".") + 1) + ".java");
        }
      }
    }
    return result;
  }
}
