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
  protected MavenProject           project;

  /**
   * @parameter expression="{excludeGroupIds}"
   * @optional
   */
  private String                   excludeGroupIds;

  /**
   * @parameter expression="{excludeArtifactIds}"
   * @optional
   */
  private String                   excludeArtifactIds;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void execute() throws MojoExecutionException {
    try {
      Set<Artifact> artifacts = filterCompileAndRuntimeScope(project.getArtifacts());
      artifacts = filterExcludeGroupIds(artifacts, excludeGroupIds);
      artifacts = filterExcludeArtifactIds(artifacts, excludeArtifactIds);
      for (Artifact a : artifacts) {
        getLog().info("Scanning " + a);
        if (isFlaggedByFinder(a)) { throw new MojoExecutionException("Artifact " + a + " was flagged by Finder"); }
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

  private static Set<Artifact> filterExcludeGroupIds(Set<Artifact> artifacts, String excludeGroupIds) {
    if (excludeGroupIds == null || excludeGroupIds.length() == 0) return artifacts;
    Set<Artifact> result = new HashSet<Artifact>();
    List<String> excludes = Arrays.asList(excludeGroupIds.split("\\s*,\\s*"));
    for (Artifact a : artifacts) {
      if (!excludes.contains(a.getGroupId())) {
        result.add(a);
      }
    }
    return result;
  }

  private static Set<Artifact> filterExcludeArtifactIds(Set<Artifact> artifacts, String excludeArtifactIds) {
    if (excludeArtifactIds == null || excludeArtifactIds.length() == 0) return artifacts;
    Set<Artifact> result = new HashSet<Artifact>();
    List<String> excludes = Arrays.asList(excludeArtifactIds.split("\\s*,\\s*"));
    for (Artifact a : artifacts) {
      if (!excludes.contains(a.getArtifactId())) {
        result.add(a);
      }
    }
    return result;
  }

  private boolean isFlaggedByFinder(Artifact a) throws Exception {
    Finder finder = new Finder();
    finder.setPackageOnlySearch(true);
    finder.setSearchRootDirectory(a.getFile().getAbsolutePath());
    finder.setUniqueEnabled(true);
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
}
