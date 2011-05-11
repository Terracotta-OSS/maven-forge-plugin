/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

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
	private final Pattern classnamePattern = Pattern.compile("TEST-(.*)\\.xml");

	public void execute() throws MojoExecutionException, MojoFailureException {
		File sureFireReportDir = new File(project.getBuild().getDirectory(),
				"surefire-reports");
		if (!sureFireReportDir.isDirectory()) {
			getLog().warn("surefire-reports folder was not found");
		} else {
			File[] reports = sureFireReportDir.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith("TEST-") && name.endsWith(".xml");
				}
			});
			if (reports.length == 0) {
				getLog().info("No junit reports were found");
			}
			for (File report : reports) {
				if (report.length() == 0L) {
					getLog().info("Fixing report " + report);
					String defaultReport = DEAULT_REPORT.replace("CLASSNAME",
							getClassname(report.getName()));
					Writer writer = null;
					try {
						writer = new PrintWriter(report);
						IOUtils.write(defaultReport, writer);
					}catch (IOException e) {
						throw new MojoExecutionException(e.getMessage());
					}finally {
						IOUtils.closeQuietly(writer);
					}
				}
			}
		}
	}

	private String getClassname(String filename) {
		Matcher matcher = classnamePattern.matcher(filename);
		String classname = "unknown";
		if (matcher.matches()) {
			classname = matcher.group(1);
		} else {
			throw new RuntimeException("Can't parse classname from filename: "
					+ filename);
		}
		return classname;
	}

	private static final String DEAULT_REPORT = "<?xml version='1.0' encoding='UTF-8'?>\n"
			+ "<testsuite errors='0' failures='1' name='CLASSNAME' tests='1' time='0.000'>\n"
			+ "  <testcase classname='CLASSNAME' name='test' time='0.0'>\n"
			+ "    <failure type='junit.framework.AssertionFailedError' message='Failed'>\n"
			+ "      Test has timeout or crashed. Please check logs for details.\n"
			+ "    </failure>\n"
			+ "  </testcase>\n"
			+ "  <system-out />\n"
			+ "  <system-err />\n"
			+ "</testsuite>\n";
}
