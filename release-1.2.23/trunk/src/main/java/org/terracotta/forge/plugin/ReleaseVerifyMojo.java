/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verify the release has no SNAPSHOT dependencies. Will fail if the project version is non SNAPSHOT and there are
 * SNAPSHOT deps in the dependency tree
 * 
 * @author hhuynh
 * @goal verify-release
 * @requiresDependencyResolution compile
 */
public class ReleaseVerifyMojo extends AbstractMojo {

  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject      project;

  /**
   * @parameter property="excludeArtifactIds"
   * @optional
   */
  private String         excludeArtifactIds;

  public void execute() throws MojoExecutionException {
    if (project.getArtifact().isSnapshot()) {
      getLog().info("Project is a SNAPSHOT, checking of SNAPSHOT dependencies is not required");
      return;
    }

    Set<Artifact> artifacts = filterExcludeArtifactIds(project.getArtifacts());
    for (Artifact a : artifacts) {
      getLog().info("Testing artifact " + a);
      if (a.isSnapshot()) { throw new MojoExecutionException("SNAPSHOT dependencies detected for a release project: "
                                                             + a.toString()); }
    }
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

  private static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
  }

}
