/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.JUnitReportCleaner;

/**
 * Fix zero-length junit reports
 * 
 * @author hhuynh
 * @goal fix-junit-report
 */
public class FixJUnitReportMojo extends AbstractMojo {
  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  public void execute() throws MojoExecutionException, MojoFailureException {
    File sureFireReportDir = new File(project.getBuild().getDirectory(),
        "surefire-reports");
    if (!sureFireReportDir.isDirectory()) {
      getLog().warn("surefire-reports folder was not found");
    } else {

      JUnitReportCleaner cleaner = new JUnitReportCleaner();

      File[] reports = sureFireReportDir.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return (name.startsWith("TEST-") && name.endsWith(".xml"))
              || name.endsWith(".txt");
        }
      });

      if (reports.length == 0) {
        getLog().info("No empty junit reports were found");
      }

      for (File report : reports) {
        String className = cleaner.getClassname(report.getName());

        // skip non junit report
        if (className == null)
          continue;

        if (report.getName().endsWith(".xml")) {
          getLog().debug("Cleaning up report " + report);
          cleaner.cleanReport(report);
        } else if (report.getName().endsWith(".txt")) {
          File xmlReport = new File(report.getParentFile(), "TEST-" + className
              + ".xml");
          if (!xmlReport.exists() || xmlReport.length() == 0L) {
            getLog().debug("Creating default report " + report);
            cleaner.createDefaultReport(xmlReport, className);
          }
        }
      }
    }
  }
}
