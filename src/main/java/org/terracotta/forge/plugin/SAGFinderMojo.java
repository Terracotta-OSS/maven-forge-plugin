/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.terracotta.forge.plugin.util.MinimalArtifact;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Run SAG Finder tool on the compile and runtime dependencies
 * 
 * @author hhuynh
 */
@Mojo(name = "sag-finder", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class SAGFinderMojo extends AbstractMojo {

  /**
   * project instance. Injected automatically by Maven
   */
  @Parameter(required = true, property = "project", readonly = true)
  private MavenProject     project;

  /**
   * groupIds to be excluded
   */
  @Parameter(required = false)
  private String           excludeGroupIds;

  /**
   * artfiactIds to be excluded
   */
  @Parameter(required = false)
  private String           excludeArtifactIds;

  /**
   * Allow skipping this mojo altogether
   */
  @Parameter(required = false, defaultValue = "false", property = "skipSagFinder")
  private boolean          skip;

  /**
   * Controls whether this mojo should run with -Dsag-deps
   */
  @Parameter(required = false, defaultValue = "false", property = "onlyRunWhenSagDepsIsTrue")
  private boolean          onlyRunWhenSagDepsIsTrue;

  /**
   * Directory that would be scanned by Finder. If this is specify then dependencies of the project won't be scanned
   */
  @Parameter(required = false)
  private String           scanDirectory;

  /**
   * Exclusion list, only being used when scanDirectory is not null
   */
  @Parameter(required = false)
  private String           exclusionList;

  /**
   * Specify an artifact by its coordinate (groupId:artifactId) for exclusions
   */
  @Parameter(required = false)
  private List<String>     excludeArtifacts;

  @Component
  private RepositorySystem repoSystem;

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
    File scanDirectoryAbs = new File(scanDirectory);
    if (!scanDirectoryAbs.exists() || !scanDirectoryAbs.isDirectory()) { throw new MojoExecutionException(
                                                                                                          scanDirectory
                                                                                                              + " is not a directory or doesn't exist"); }
    getLog().info("About to scan " + scanDirectoryAbs.getAbsolutePath() + " with Finder");
    if (Util.isFlaggedByFinder(scanDirectoryAbs.getAbsolutePath(), exclusionList, getLog())) { throw new MojoExecutionException(
                                                                                                           "Finder found Oracle jar(s)"); }
  }

  private void doScanDependencies() throws Exception {
    getLog().info("About to scan dependencies with Finder");
    Set<Artifact> artifacts = filterCompileAndRuntimeScope(project.getArtifacts());
    artifacts = filterExcludeGroupIds(artifacts);
    artifacts = filterExcludeArtifactIds(artifacts);
    artifacts = filterExcludeArtifacts(artifacts);
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

  private Set<Artifact> filterExcludeArtifacts(Set<Artifact> artifacts) {
    if (excludeArtifacts == null) return artifacts;
    Set<Artifact> result = new HashSet<Artifact>();

    Set<MinimalArtifact> excludes = new HashSet<MinimalArtifact>();
    for (String coords : excludeArtifacts) {
      excludes.add(new MinimalArtifact(coords));
    }

    for (Artifact a : artifacts) {
      boolean found = false;
      for (MinimalArtifact excludedArtifact : excludes) {
        if (a.getGroupId().equals(excludedArtifact.getGroupId())
            && a.getArtifactId().equals(excludedArtifact.getArtifactId())) {
          getLog().info("Exclude " + a + " from scanning");
          found = true;
          break;
        }
      }
      if (!found) {
        result.add(a);
      }
    }
    return result;
  }
}
