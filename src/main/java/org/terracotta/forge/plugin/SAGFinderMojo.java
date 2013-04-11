/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.softwareag.ibit.tools.util.Finder;

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
   * project instance. Injected automtically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * @parameter expression="${excludeGroupIds}"
   * @optional
   */
  private String         excludeGroupIds;

  /**
   * @parameter expression="${excludeArtifactIds}"
   * @optional
   */
  private String         excludeArtifactIds;

  /**
   * Allow skipping this mojo altogether
   * 
   * @parameter expression="${skip}" default-value="false"
   * @optional
   */
  private boolean        skip;

  /**
   * @parameter expression="${onlyRunWhenSagDepsIsTrue}" default-value="false"
   * @optional
   */
  private boolean        onlyRunWhenSagDepsIsTrue;

  /**
   * Directory that would be scanned by Finder. If this is specify then dependencies of the project won't be scanned
   * 
   * @parameter expression="${scanDirectory}"
   * @optional
   */
  private String         scanDirectory;

  /**
   * Exclusion list, only being used when scanDirectory is not null
   * 
   * @parameter expression="${exclusionList}"
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
      if (!isEmpty(scanDirectory)) {
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
    if (isFlaggedByFinder(scanDirectory)) { throw new MojoExecutionException("Finder found Oracle jar(s)"); }
  }

  private void doScanDependencies() throws Exception {
    getLog().info("About to scan dependencies with Finder");
    Set<Artifact> artifacts = filterCompileAndRuntimeScope(project.getArtifacts());
    artifacts = filterExcludeGroupIds(artifacts);
    artifacts = filterExcludeArtifactIds(artifacts);
    for (Artifact a : artifacts) {
      getLog().info("Scanning " + a);
      if (isFlaggedByFinder(a.getFile().getAbsolutePath())) {
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
    if (isEmpty(excludeGroupIds)) return artifacts;
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
    if (isEmpty(excludeArtifactIds)) return artifacts;
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

  private boolean isFlaggedByFinder(String file) throws Exception {
    Finder finder = new Finder();
    finder.setPackageOnlySearch(true);
    finder.setSearchRootDirectory(file);
    finder.setUniqueEnabled(true);
    if (!isEmpty(exclusionList)) {
      finder.setExcludesListFilename(exclusionList);
    }
    List<String> resultList = finder.doSearch();
    if (resultList.size() > 0) {
      for (String result : resultList) {
        getLog().error("Flagged: " + result);
      }
      return true;
    } else {
      return false;
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
  }
}
