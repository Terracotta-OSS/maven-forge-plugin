/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

/**
 * Collect SVN info of the current project.
 * Default path is ${project.basedir}
 * 
 * The plugin will fill out these properties 
 * 
 * "build.revision" == "Last Change Rev"
 * "build.svn.url"  == "URL"
 * 
 * @author hhuynh
 * @goal svninfo
 */
public class SvnInfoMojo extends AbstractMojo {
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
		String svnUrl = "unknown";
		String revision = "unknown";
		
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
		
		project.getProperties().setProperty("build.revision", revision);
		project.getProperties().setProperty("build.svn.url", svnUrl);
		System.setProperty("build.revision", revision);
		System.setProperty("build.svn.url", svnUrl);
	}
}
