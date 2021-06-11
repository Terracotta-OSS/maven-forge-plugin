/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.SCMInfo;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Collect build info of the current project. Default rootPath is ${project.basedir}. This is used to get svn info The
 * plugin will set these system properties "build.revision" == from svn info "Last Change Rev" "build.svn.url" == from
 * svn info "URL" "build.timestamp"
 * 
 * @author hhuynh
 * @goal buildinfo
 */
public class BuildInfoMojo extends AbstractMojo {

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

  /**
   * @parameter property="skipBranchMatchingCheck" default-value="false"
   * @optional
   */
  private boolean             skipBranchMatchingCheck;

  private final Properties    buildInfoProps   = new Properties();

  /**
   * 
   */
  public void execute() throws MojoExecutionException {

    if (rootPath == null) {
      rootPath = project.getBasedir().getAbsolutePath();
    }

    if (eeRootPath != null && osRootPath != null) { throw new MojoExecutionException(
            "eeRootPath and osRootPath are mutual exclusive. Both cannot be set."); }


    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    setBuildInfoProperty("build.timestamp", timestamp);

    SCMInfo scmInfo = null;
    SCMInfo scmInfoEE = null;
    SCMInfo scmInfoOS = null;
    
    try {
      String fullRevision = "";
      scmInfo = Util.getScmInfo(rootPath, getLog());
      fullRevision = scmInfo.revision;
      getLog().info(String.format("Determined SCM info: %s, branch %s, revision %s", scmInfo.url, scmInfo.branch, scmInfo.revision));
      
      if (eeRootPath != null) {
        scmInfoEE = Util.getScmInfo(eeRootPath, getLog());
        setBuildInfoProperties(scmInfoEE, "build.ee");
        checkMatchingBranch(scmInfo.branch, scmInfoEE.branch);
        // we use the template EE_REVISION-OS_REVISION
        if (scmInfoEE.revision != null) {
          fullRevision = scmInfoEE.revision + "-" + scmInfo.revision;
        }
        getLog().info(String.format("Determined SCM info (EE): %s, branch %s, revision %s", scmInfoEE.url, scmInfoEE.branch, scmInfoEE.revision));
      }

      if (osRootPath != null) {
        scmInfoOS = Util.getScmInfo(osRootPath, getLog());
        setBuildInfoProperties(scmInfoOS, "build.os");
        checkMatchingBranch(scmInfoOS.branch, scmInfo.branch);

        if (scmInfoOS.revision != null) {
          fullRevision = scmInfo.revision + "-" + scmInfoOS.revision;
        }
        getLog().info(String.format("Determined SCM info (OS): %s, branch %s, revision %s", scmInfoOS.url, scmInfoOS.branch, scmInfoOS.revision));
      }

      getLog().debug("Setting build.revision to " + fullRevision);
      getLog().debug("Setting build.scm.url to " + scmInfo.url);

      scmInfo.revision = fullRevision;
      setBuildInfoProperties(scmInfo, "build");

    } catch (IOException ioe) {
      throw new MojoExecutionException("Error reading scm info", ioe);
    }


    if (generateBuildInfoFile) {
      generateBuildInfoFile();
    }
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

  private void setBuildInfoProperties(SCMInfo scmInfo, String prefix) {
    if (scmInfo != null) {
      setBuildInfoProperty(prefix + ".revision", scmInfo.revision);
      setBuildInfoProperty(prefix + ".scm.url", scmInfo.url);
      setBuildInfoProperty(prefix + ".branch", scmInfo.branch);
    }
  }

  private void setBuildInfoProperty(String key, String value) {
    if (value == null) {
      value = Util.UNKNOWN;
    }
    buildInfoProps.setProperty(key, value);
    project.getProperties().setProperty(key, value);
  }

  void checkMatchingBranch(String osBranch, String eeBranch) throws MojoExecutionException {
    if (skipBranchMatchingCheck) {
      getLog().info("skipBranchMatchingCheck is true, skipping. osBranch is " + osBranch + " eeBranch is " + eeBranch);
      return;
    }
    // if the user did not check out the ee branch, it's going to be unknown: we skip the check
    if (eeBranch == null || Util.UNKNOWN.equals(eeBranch)) { return; }
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
