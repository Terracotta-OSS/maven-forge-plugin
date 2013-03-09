/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;

import com.jcabi.aether.Aether;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * print out dependencies of a given artifact
 * 
 * @author hhuynh
 * @goal copy-dependencies
 * @requiresDependencyResolution compile
 */
public class CopyArtifactDependenciesMojo extends AbstractMojo {
  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject          project;

  /**
   * @parameter default-value="${repositorySystemSession}"
   * @readonly
   */
  private RepositorySystemSession session;

  /**
   * artifact groupId:artifactId:version
   * 
   * @parameter expression="${artifact}"
   * @required
   */
  private List<String>            artifacts;

  /**
   * output dir
   * 
   * @required
   * @parameter expression="${outputDir}"
   */
  private File                    outputDir;

  /**
   * comma separated list of groupIds to be excluded
   * 
   * @optional
   * @parameter expression="${excludeGroupIds}"
   */
  private String                  excludeGroupIds;

  /**
   * comma separated list of artifactIds to be excluded
   * 
   * @optional
   * @parameter expression="${excludeArtifactIds}"
   */
  private String                  excludeArtifactIds;

  /**
   * 
   */
  public void execute() throws MojoExecutionException {
    File repo = this.session.getLocalRepository().getBasedir();
    PrintStream out = null;
    try {
      Aether aether = new Aether(project, repo.getAbsolutePath());
      Collection<Artifact> deps = new ArrayList<Artifact>();
      for (String artifact : artifacts) {
        deps.addAll(aether.resolve(new DefaultArtifact(artifact), JavaScopes.RUNTIME));
      }

      excludeGroupIds(deps);
      excludeArtifactIds(deps);

      copyDeps(deps);

    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  private void copyDeps(Collection<Artifact> deps) throws IOException {
    for (Artifact a : deps) {
      String filename = a.getFile().getName();
      if (a.isSnapshot()) {
        filename = a.getArtifactId() + "-" + a.getBaseVersion() + "." + a.getExtension();
      }
      File destFile = new File(outputDir, filename);
      FileUtils.copyFile(a.getFile(), destFile);
    }
  }

  private void excludeGroupIds(Collection<Artifact> deps) {
    if (excludeGroupIds == null) { return; }
    Set<String> exclusions = new HashSet<String>();
    for (String groupId : excludeGroupIds.split(",")) {
      exclusions.add(groupId.trim());
    }
    for (Iterator<Artifact> it = deps.iterator(); it.hasNext();) {
      if (exclusions.contains(it.next().getGroupId())) {
        it.remove();
      }
    }
  }

  private void excludeArtifactIds(Collection<Artifact> deps) {
    if (excludeArtifactIds == null) { return; }
    Set<String> exclusions = new HashSet<String>();
    for (String groupId : excludeArtifactIds.split(",")) {
      exclusions.add(groupId.trim());
    }
    for (Iterator<Artifact> it = deps.iterator(); it.hasNext();) {
      if (exclusions.contains(it.next().getArtifactId())) {
        it.remove();
      }
    }
  }

}