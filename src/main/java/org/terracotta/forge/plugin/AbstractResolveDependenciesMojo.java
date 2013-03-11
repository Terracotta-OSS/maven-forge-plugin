/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

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
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * abstract class to resolve dependencies of a given artifact
 * 
 * @author hhuynh
 */
public abstract class AbstractResolveDependenciesMojo extends AbstractMojo {
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
  protected RepositorySystemSession session;

  /**
   * artifact groupId:artifactId:version
   * 
   * @parameter expression="${artifact}"
   * @required
   */
  protected List<String>            artifacts;

  /**
   * comma separated list of groupIds to be excluded
   * 
   * @optional
   * @parameter expression="${excludeGroupIds}"
   */
  protected String                  excludeGroupIds;

  /**
   * comma separated list of artifactIds to be excluded
   * 
   * @optional
   * @parameter expression="${excludeArtifactIds}"
   */
  protected String                  excludeArtifactIds;

  /**
   * resolve dependencies transitively or not, default is true
   * 
   * @parameter expression="${resolveTransitively}" default-value="true"
   */
  protected boolean                 resolveTransitively;

  protected Collection<Artifact> resolve() throws MojoExecutionException {
    File repo = this.session.getLocalRepository().getBasedir();
    PrintStream out = null;
    try {
      Aether aether = new Aether(project, repo.getAbsolutePath());
      Collection<Artifact> deps = new ArrayList<Artifact>();
      for (String artifact : artifacts) {
        deps.addAll(aether.resolve(new DefaultArtifact(artifact), JavaScopes.RUNTIME));
      }

      if (!resolveTransitively) {
        retainOriginalArtifacts(deps);
      }

      excludeGroupIds(deps);
      excludeArtifactIds(deps);

      return deps;

    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(out);
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

  /**
   * NOTE: configured artifacts have not been resolved so their getFile() method will return null thus we can't use them
   * directly
   */
  private Collection<Artifact> getConfiguredArtifacts() {
    Collection<Artifact> ret = new ArrayList<Artifact>();
    for (String a : artifacts) {
      ret.add(new DefaultArtifact(a));
    }
    return ret;
  }

  private boolean isConfiguredArtifact(Artifact a, Collection<Artifact> configuredArtifacts) {
    for (Artifact itArtifact : configuredArtifacts) {
      if (a.getGroupId().equals(itArtifact.getGroupId()) && a.getArtifactId().equals(itArtifact.getArtifactId())
          && a.getBaseVersion().equals(itArtifact.getBaseVersion())
          && a.getExtension().equals(itArtifact.getExtension()) && a.getClassifier().equals(itArtifact.getClassifier())) { return true; }
    }
    return false;
  }

  private void retainOriginalArtifacts(Collection<Artifact> resolvedArtifacts) {
    Collection<Artifact> configuredArtifacts = getConfiguredArtifacts();
    for (Iterator<Artifact> it = resolvedArtifacts.iterator(); it.hasNext();) {
      if (!isConfiguredArtifact(it.next(), configuredArtifacts)) {
        it.remove();
      }
    }
  }

}