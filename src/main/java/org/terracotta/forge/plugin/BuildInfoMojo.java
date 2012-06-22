/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

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
 * @goal buildinfo
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
   * @parameter expression="${eeRootPath}
   */
  private String eeRootPath;
  
  /**
   * @parameter expression="${osRootPath}
   */
  private String osRootPath;

	/**
   * 
   */
	@Override
  public void execute() throws MojoExecutionException, MojoFailureException {
		String svnUrl = UNKNOWN;
		String revision = UNKNOWN;
    String eeSvnUrl = UNKNOWN;
    String eeRevision = UNKNOWN;
    String osSvnUrl = UNKNOWN;
    String osRevision = UNKNOWN;
		
		if (rootPath == null) {
			rootPath = project.getBasedir().getAbsolutePath();
		}
		
		if (eeRootPath != null && osRootPath != null) {
		  throw new MojoExecutionException("eeRootPath and osRootPath are mutual exclusive. Both cannot be set.");
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
			
			if (eeRootPath != null) {
			  String eeSvnInfo = Util.getSvnInfo(new File(eeRootPath).getCanonicalPath());
			  getLog().debug("EE SVN INFO: " + eeSvnInfo);
			  br = new BufferedReader(new StringReader(eeSvnInfo));
			  while ((line = br.readLine()) != null) {
	        if (line.startsWith("URL: ")) {
	          eeSvnUrl = line.substring("URL: ".length());
	        }
	        if (line.startsWith("Last Changed Rev: ")) {
	          eeRevision = line.substring("Last Changed Rev: ".length());
	        }
	      }
			}
			
	     if (osRootPath != null) {
	        String eeSvnInfo = Util.getSvnInfo(new File(osRootPath).getCanonicalPath());
	        getLog().debug("OS SVN INFO: " + eeSvnInfo);
	        br = new BufferedReader(new StringReader(eeSvnInfo));
	        while ((line = br.readLine()) != null) {
	          if (line.startsWith("URL: ")) {
	            osSvnUrl = line.substring("URL: ".length());
	          }
	          if (line.startsWith("Last Changed Rev: ")) {
	            osRevision = line.substring("Last Changed Rev: ".length());
	          }
	        }
	      }
		} catch (IOException ioe) {
			throw new MojoExecutionException("Error reading svn info", ioe);
		}
		
		String host = UNKNOWN;
		String user = System.getProperty("user.name", UNKNOWN);
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss")
				.format(new Date());
		try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (Exception e) {
			getLog().warn("Exception while finding host name", e);
		}

		project.getProperties().setProperty("build.user", user);
		project.getProperties().setProperty("build.host", host);
		project.getProperties().setProperty("build.timestamp", timestamp);
		
		if (eeRootPath != null) {
	    project.getProperties().setProperty("build.ee.revision", eeRevision);
	    project.getProperties().setProperty("build.ee.svn.url", eeSvnUrl);
	    project.getProperties().setProperty("build.ee.branch", guessBranchOrTagFromUrl(eeSvnUrl));
		}
		
    if (osRootPath != null) {
      project.getProperties().setProperty("build.os.revision", osRevision);
      project.getProperties().setProperty("build.os.svn.url", osSvnUrl);
      project.getProperties().setProperty("build.os.branch", guessBranchOrTagFromUrl(osSvnUrl));
    }
   
    String fullRevision = revision;
    // we use the template EE_REVISION-OS_REVISION
    if (eeRootPath != null) {
      fullRevision = eeRevision + "-" + revision;
    }
    if (osRootPath != null) {
      fullRevision = revision + "-" + osRevision;
    }
    
    getLog().debug("Setting build.revision to " + fullRevision);
    getLog().debug("Setting build.svn.url to " + svnUrl);
    
    project.getProperties().setProperty("build.revision", fullRevision);
    project.getProperties().setProperty("build.svn.url", svnUrl);
    project.getProperties().setProperty("build.branch", guessBranchOrTagFromUrl(svnUrl));
	}
	
	private String guessBranchOrTagFromUrl(String url) {
		if (url.contains("trunk")) return "trunk";
		int startIndex = url.indexOf("branches/");
		if (startIndex > 0) {
			int endIndex = url.indexOf("/", startIndex + 9);
			if (endIndex < 0) {
				endIndex = url.length();
			}
			return url.substring(startIndex + 9, endIndex);
		}
		startIndex = url.indexOf("tags/");
		if (startIndex > 0) {
			int endIndex = url.indexOf("/", startIndex + 5);
			if (endIndex < 0) {
				endIndex = url.length();
			}
			return url.substring(startIndex + 5, endIndex);
		}
		return "unknown";
	}
}
