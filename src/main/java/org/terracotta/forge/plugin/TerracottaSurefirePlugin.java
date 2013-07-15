/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.suite.RunResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mojo( name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST )
public class TerracottaSurefirePlugin extends SurefirePlugin {

  @Parameter( property = "cleanJunitReports", defaultValue = "false" )
  private boolean                              cleanJunitReports;

  @Parameter( property = "listFile" )
  private File                                 listFile;

  @Parameter( property = "poundTimes" , defaultValue = "1")
  private int                                  poundTimes;

  @Parameter( property = "devLog" , defaultValue = "false")
  private boolean                              devLog;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (devLog) {
      File currentDir = super.getWorkingDirectory();
      if (currentDir == null) {
        currentDir = new File(".");
      }
      File devLog4jFile = new File(currentDir, ".tc.dev.log4j.properties");
      if (!devLog4jFile.exists()) {
        try {
          devLog4jFile.getParentFile().mkdirs();
          if (!devLog4jFile.createNewFile())
            throw new IOException("createNewFile return false");
        } catch (IOException e1) {
          getLog().error(e1);
          throw new MojoExecutionException("Failed to create " + devLog4jFile);
        }
      }
    }

    try {
      // recheck should_skip_test maven propperty to decide if tests should be
      // skipped
      String shouldSkipTestsValue = project.getProperties().getProperty(
          "should_skip_tests");
      if (shouldSkipTestsValue != null) {
        getLog().warn(
            "'should_skip_tests' property found, value is "
                + shouldSkipTestsValue
                + ". This value overrides the 'skipTests' original setting.");
        boolean shouldSkipTests = Boolean.valueOf(shouldSkipTestsValue);
        this.setSkipTests(shouldSkipTests);
      }

      // handle listFile
      if (listFile != null) {
        if (!listFile.exists()) {
          getLog().warn(
              "listFile '" + listFile
                  + "' specified but does not exist. No tests will be run");
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
            } catch (MojoExecutionException e) {
              getLog().error("Test failed after iteration #" + i);
              throw e;
            }
          }
        }
        // done pounding, exit
        getLog().info("*** Pounded " + poundTimes + " times! Test passed.");
      } else {
        // invoke surefire plugin normally
        super.execute();
      }

    } catch (MojoExecutionException e) {
      if (e.getCause() instanceof SurefireBooterForkException) {
        // test timeout, don't throw this exception
        // so Jenkins could parse JUnit reports and treat timeout failures as
        // regular failures
        SurefireHelper.reportExecution(this, new RunResult(0, 0, 0, 0, "timeout", true), getLog());
      } else {
        throw e;
      }
    } finally {
      if (cleanJunitReports) {
        getLog().info("Fix Junit reports if needed");
        FixJUnitReportMojo fixUnitReportMojo = new FixJUnitReportMojo();
        fixUnitReportMojo.setPluginContext(getPluginContext());
        fixUnitReportMojo.setProject(project);
        fixUnitReportMojo.execute();
      }
    }
  }

  @Override
  protected boolean hasExecutedBefore() {
    // if we're pounding test, we have to lie so that the test can be run again
    if (poundTimes > 1)
      return false;
    return super.hasExecutedBefore();
  }

}
