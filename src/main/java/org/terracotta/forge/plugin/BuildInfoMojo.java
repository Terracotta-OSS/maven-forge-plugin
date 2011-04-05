/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

/**
 * Collect build info of the current project.
 * Default rootPath is ${project.basedir}. This is used to get svn info
 * 
 * The plugin will set these system properties
 * 
 * "build.revision" == from svn info "Last Change Rev"
 * "build.svn.url"  == from svn info "URL"
 * "build.host"
 * "build.user"
 * "build.timpestamp"
 * 
 * @author hhuynh
 * @goal svninfo
 */
public class BuildInfoMojo extends AbstractMojo {
	private static final String UNKNOWN = "unknown";

	/**
	 * project instance. Injected automatically by Maven
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;

	/**
	 * @parameter expression="${rootPath}
	 */
	private String rootPath;

	/**
   * 
   */
	public void execute() throws MojoExecutionException, MojoFailureException {
		String svnUrl = UNKNOWN;
		String revision = UNKNOWN;
		
		if (rootPath == null) {
			rootPath = project.getBasedir().getAbsolutePath();
		}
		
		try {
			String svnInfo = Util.getSvnInfo(new File(rootPath).getCanonicalPath());
			getLog().debug("SVN INFO: " + svnInfo);
			BufferedReader br = new BufferedReader(new StringReader(svnInfo));
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("URL: ")) {
					svnUrl = line.substring("URL: ".length());
				}
				if (line.startsWith("Last Changed Rev: ")) {
					revision = line.substring("Last Changed Rev: ".length());
				}
			}
		} catch (IOException ioe) {
			throw new MojoExecutionException("Error reading svn info", ioe);
		}
		
		getLog().debug("Setting build.revision to " + revision);
		getLog().debug("Setting build.svn.url to " + svnUrl);
		
		System.setProperty("build.revision", revision);
		System.setProperty("build.svn.url", svnUrl);
		
		String host = UNKNOWN;
		String user = System.getProperty("user.name", UNKNOWN);
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss")
				.format(new Date());
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
			getLog().warn("Exception while finding host name", e);
		}

		System.setProperty("build.user", user);
		System.setProperty("build.host", host);
		System.setProperty("build.timestamp", timestamp);
	}
}
