/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Collect build info of the current project. Default rootPath is ${project.basedir}. This is used to get svn info The
 * plugin will set these system properties "build.revision" == from svn info "Last Change Rev" "build.svn.url" == from
 * svn info "URL" "build.host" "build.user" "build.timestamp"
 * 
 * @author hhuynh
 * @goal buildinfo
 */
public class BuildInfoMojo extends AbstractMojo {
  private static final String LAST_CHANGED_REV = "Last Changed Rev";

  private static final String URL = "URL";

  private static final String UNKNOWN        = "unknown";

  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject      project;

  /**
   * @parameter property="rootPath"
   */
  private String              rootPath;

  /**
   * @parameter property="eeRootPath"
   */
  private String              eeRootPath;

  /**
   * @parameter property="osRootPath"
   */
  private String              osRootPath;

  /**
   * @parameter property="generateBuildInfoFile" default-value="false"
   * @opional
   */
  private boolean             generateBuildInfoFile;

  /**
   * @parameter property="buildInfoLocation" default-value="${project.build.outputDirectory}"
   * @optional
   */
  private String              buildInfoLocation;

  private final Properties    buildInfoProps = new Properties();

  /**
   * 
   */
  public void execute() throws MojoExecutionException {
    String svnUrl = UNKNOWN;
    String revision = UNKNOWN;
    String eeSvnUrl = UNKNOWN;
    String eeRevision = UNKNOWN;
    String osSvnUrl = UNKNOWN;
    String osRevision = UNKNOWN;

    if (rootPath == null) {
      rootPath = project.getBasedir().getAbsolutePath();
    }

    if (eeRootPath != null && osRootPath != null) { throw new MojoExecutionException(
                                                                                     "eeRootPath and osRootPath are mutual exclusive. Both cannot be set."); }

    try {
      Properties svnInfo = Util.getSvnInfo(new File(rootPath).getCanonicalPath(), getLog());
      svnUrl = svnInfo.getProperty(URL, UNKNOWN);
      revision = svnInfo.getProperty(LAST_CHANGED_REV, UNKNOWN);

      if (eeRootPath != null) {
        Properties eeSvnInfo = Util.getSvnInfo(new File(eeRootPath).getCanonicalPath(), getLog());
        eeSvnUrl = eeSvnInfo.getProperty(URL, UNKNOWN);
        eeRevision = eeSvnInfo.getProperty(LAST_CHANGED_REV, UNKNOWN);
      }

      if (osRootPath != null) {
        Properties osSvnInfo = Util.getSvnInfo(new File(osRootPath).getCanonicalPath(), getLog());
        osSvnUrl = osSvnInfo.getProperty(URL, UNKNOWN);
        osRevision = osSvnInfo.getProperty(LAST_CHANGED_REV, UNKNOWN);
      }
    } catch (IOException ioe) {
      throw new MojoExecutionException("Error reading svn info", ioe);
    }

    String host = UNKNOWN;
    String user = System.getProperty("user.name", UNKNOWN);
    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      getLog().warn("Exception while finding host name", e);
    }

    setBuildInfoProperty("build.user", user);
    setBuildInfoProperty("build.host", host);
    setBuildInfoProperty("build.hostname", host);
    setBuildInfoProperty("build.timestamp", timestamp);

    if (eeRootPath != null) {
      setBuildInfoProperty("build.ee.revision", eeRevision);
      setBuildInfoProperty("build.ee.svn.url", eeSvnUrl);
      setBuildInfoProperty("build.ee.branch", guessBranchOrTagFromUrl(eeSvnUrl));
      checkMatchingBranch(guessBranchOrTagFromUrl(svnUrl), guessBranchOrTagFromUrl(eeSvnUrl));
    }

    if (osRootPath != null) {
      setBuildInfoProperty("build.os.revision", osRevision);
      setBuildInfoProperty("build.os.svn.url", osSvnUrl);
      setBuildInfoProperty("build.os.branch", guessBranchOrTagFromUrl(osSvnUrl));
      checkMatchingBranch(guessBranchOrTagFromUrl(osSvnUrl), guessBranchOrTagFromUrl(svnUrl));
    }

    String fullRevision = revision;
    // we use the template EE_REVISION-OS_REVISION
    if (!UNKNOWN.equals(eeRevision)) {
      fullRevision = eeRevision + "-" + revision;
    }
    if (!UNKNOWN.equals(osRevision)) {
      fullRevision = revision + "-" + osRevision;
    }

    getLog().debug("Setting build.revision to " + fullRevision);
    getLog().debug("Setting build.svn.url to " + svnUrl);

    setBuildInfoProperty("build.revision", fullRevision);
    setBuildInfoProperty("build.svn.url", svnUrl);
    setBuildInfoProperty("build.branch", guessBranchOrTagFromUrl(svnUrl));

    if (generateBuildInfoFile) {
      generateBuildInfoFile();
    }
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

  private void generateBuildInfoFile() throws MojoExecutionException {
    PrintWriter out = null;
    try {
      File buildInfoFile = new File(buildInfoLocation, "build-info.properties");
      buildInfoFile.getParentFile().mkdirs();
      out = new PrintWriter(buildInfoFile);
      buildInfoProps.list(out);
    } catch (IOException e) {
      throw new MojoExecutionException("IO Error:", e);
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  private void setBuildInfoProperty(String key, String value) {
    buildInfoProps.setProperty(key, value);
    project.getProperties().setProperty(key, value);
  }

  private static void checkMatchingBranch(String osBranch, String eeBranch) throws MojoExecutionException {
    // For Ehcache branches, they don't really match 100%
    // Ehcache EE branch: ehcache-core-ee-2.8.x
    // Ehcache OS branch: ehcache-2.8.x
    // so we remove -core-ee before matching

    String os = osBranch.replace("-core-ee", "");
    String ee = eeBranch.replace("-core-ee", "");

    if (!os.equals(ee)) { throw new MojoExecutionException("branch doesn't match between EE (" + eeBranch
                                                                       + ") and OS (" + osBranch
                                                                       + "). Check your svn:externals property"); }
  }
}
