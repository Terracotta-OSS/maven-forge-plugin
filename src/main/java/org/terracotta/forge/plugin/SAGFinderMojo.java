/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Run SAG Finder tool on the compile and runtime dependencies
 * 
 * @author hhuynh
 * @goal sag-finder
 * @requiresDependencyResolution compile
 */
public class SAGFinderMojo extends AbstractMojo {

  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * @parameter property="excludeGroupIds"
   * @optional
   */
  private String         excludeGroupIds;

  /**
   * @parameter property="excludeArtifactIds"
   * @optional
   */
  private String         excludeArtifactIds;

  /**
   * Allow skipping this mojo altogether
   * 
   * @parameter property="skipSagFinder" default-value="false"
   * @optional
   */
  private boolean        skip;

  /**
   * @parameter property="onlyRunWhenSagDepsIsTrue" default-value="false"
   * @optional
   */
  private boolean        onlyRunWhenSagDepsIsTrue;

  /**
   * Directory that would be scanned by Finder. If this is specify then dependencies of the project won't be scanned
   * 
   * @parameter property="scanDirectory"
   * @optional
   */
  private String         scanDirectory;

  /**
   * Exclusion list, only being used when scanDirectory is not null
   * 
   * @parameter property="exclusionList"
   * @optional
   */
  private String         exclusionList;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("skip is set to true. Skipping");
      return;
    }
    if (onlyRunWhenSagDepsIsTrue && !Boolean.getBoolean("sag-deps")) {
      getLog().info("Skipped condition found: onlyRunWhenSagDepsIsTrue = true and sag-deps = false");
      return;
    }
    try {
      if (!Util.isEmpty(scanDirectory)) {
        doScanDirectory();
      } else {
        doScanDependencies();
      }
      getLog().info("Scanning completed! Nothing flagged by Finder");
    } catch (Exception e) {
      if (e instanceof MojoExecutionException) {
        throw (MojoExecutionException) e;
      } else {
        throw new MojoExecutionException("Error", e);
      }
    }
  }

  private void doScanDirectory() throws Exception {
    getLog().info("About to scan " + scanDirectory + " with Finder");
    if (Util.isFlaggedByFinder(scanDirectory, exclusionList, getLog())) { throw new MojoExecutionException(
                                                                                                           "Finder found Oracle jar(s)"); }
  }

  private void doScanDependencies() throws Exception {
    getLog().info("About to scan dependencies with Finder");
    Set<Artifact> artifacts = filterCompileAndRuntimeScope(project.getArtifacts());
    artifacts = filterExcludeGroupIds(artifacts);
    artifacts = filterExcludeArtifactIds(artifacts);
    for (Artifact a : artifacts) {
      getLog().info("Scanning " + a);
      if (Util.isFlaggedByFinder(a.getFile().getAbsolutePath(), exclusionList, getLog())) {
        //
        throw new MojoExecutionException("Artifact " + a + " was flagged by Finder");
      }
    }
  }

  private static Set<Artifact> filterCompileAndRuntimeScope(Set<Artifact> artifacts) {
    Set<Artifact> result = new HashSet<Artifact>();
    for (Artifact a : artifacts) {
      if (a.getArtifactHandler().isAddedToClasspath()) {
        if (Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())) {
          result.add(a);
        }
      }
    }
    return result;
  }

  private Set<Artifact> filterExcludeGroupIds(Set<Artifact> artifacts) {
    if (Util.isEmpty(excludeGroupIds)) return artifacts;
    Set<Artifact> result = new HashSet<Artifact>();
    List<String> excludes = Arrays.asList(excludeGroupIds.split("\\s*,\\s*"));
    for (Artifact a : artifacts) {
      if (!excludes.contains(a.getGroupId())) {
        result.add(a);
      } else {
        getLog().info("Exclude " + a + " from scanning");
      }
    }
    return result;
  }

  private Set<Artifact> filterExcludeArtifactIds(Set<Artifact> artifacts) {
    if (Util.isEmpty(excludeArtifactIds)) return artifacts;
    Set<Artifact> result = new HashSet<Artifact>();
    List<String> excludes = Arrays.asList(excludeArtifactIds.split("\\s*,\\s*"));
    for (Artifact a : artifacts) {
      if (!excludes.contains(a.getArtifactId())) {
        result.add(a);
      } else {
        getLog().info("Exclude " + a + " from scanning");
      }
    }
    return result;
  }
}
