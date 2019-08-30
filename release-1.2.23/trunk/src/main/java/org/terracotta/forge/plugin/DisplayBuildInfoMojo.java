/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * Display build info of Terracotta dependencies
 * Currently we're only care about groupIds org.terracotta, net.sf.ehcache, org.quartz-scheduler
 * 
 * @author hhuynh
 * @goal displayBuildInfo
 * @requiresDependencyResolution compile
 */
public class DisplayBuildInfoMojo extends AbstractMojo {

  /**
   * project instance. Injected automatically by Maven
   *
   * @parameter property="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;
  
  /**
   * comma separated list of groupIds
   *
   * @parameter property="includes" default-value="net.sf.ehcache, org.terracotta, org.quartz-scheduler"
   */
  private String includes;

  public void execute() throws MojoExecutionException, MojoFailureException {
    Set<String> includedGroupIds = parseIncludesGroupIds();
    getLog().info("Includes '" + includedGroupIds.toString() + "'");
    Set<Artifact> artifacts = project.getArtifacts();
    for (Artifact artifact : artifacts) {
      if (isIncluded(includedGroupIds, artifact)) {
        getLog().info("Build info for artifact " + artifact);
        displayBuildInfo(artifact);
      }
    }
  }

  private Set<String> parseIncludesGroupIds() {
    if (includes == null || includes.equals("")) {
      return Collections.EMPTY_SET;
    }
    return new TreeSet(Arrays.asList(includes.trim().split("\\s*,\\s*")));
  }

  private boolean isIncluded(Set<String> groupIds, Artifact artifact) {
    // only care about 'jar' artifacts
    if (!"jar".equals(artifact.getType())) return false;
    
    String artifactGroupId = artifact.getGroupId();
    for (String includedGroupId : groupIds) {
      if (artifactGroupId.startsWith(includedGroupId)) {
        return true;
      }
    }
    return false;
  }

  private void displayBuildInfo(Artifact artifact) throws MojoExecutionException {
    File artifactFile = artifact.getFile();
    if (artifactFile != null && artifactFile.exists()) {
      getLog().info(getBuildInfo(artifact));
    } else {
      getLog().warn("Couldn't find artifact " + artifact + " in local repo");
    }
  }

  private String getBuildInfo(Artifact artifact) throws MojoExecutionException {
    try {
      File artifactFile = artifact.getFile();
      JarFile artifactJar = new JarFile(artifactFile);
      Manifest manifest = artifactJar.getManifest();
      StringBuilder sb = new StringBuilder();
      Attributes attributes = manifest.getMainAttributes();
      for (Object key : attributes.keySet()) {
        String name = ((Attributes.Name)key).toString();
        if (name.startsWith("Build")) {
          sb.append(name).append(": ").append(attributes.get(key)).append("\n");
        }
      }
      return sb.toString();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed reading manifest", ex);
    }
  }
}
