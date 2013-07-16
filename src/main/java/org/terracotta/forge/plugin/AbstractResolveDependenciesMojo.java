/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;

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
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject            project;

  /**
   * @parameter default-value="repositorySystemSession"
   * @readonly
   */
  protected RepositorySystemSession session;

  /**
   * @component
   */
  private RepositorySystem          system;

  /**
   * The project's remote repositories to use for the resolution.
   * 
   * @parameter default-value="${project.remoteProjectRepositories}"
   * @readonly
   */
  private List<RemoteRepository>    remoteRepos;

  /**
   * artifact groupId:artifactId:version
   * 
   * @parameter property="artifact"
   * @required
   */
  protected List<String>            artifacts;

  /**
   * comma separated list of groupIds to be excluded
   * 
   * @optional
   * @parameter property="excludeGroupIds"
   */
  protected String                  excludeGroupIds;

  /**
   * comma separated list of artifactIds to be excluded
   * 
   * @optional
   * @parameter property="excludeArtifactIds"
   */
  protected String                  excludeArtifactIds;

  /**
   * resolve dependencies transitively or not, default is true
   * 
   * @parameter property="resolveTransitively" default-value="true"
   */
  protected boolean                 resolveTransitively;

  /**
   * don't resolve, just output whatever configured, useful when we only want to append to output what we know
   * <p>
   * However, you won't be able to get output listed as file
   * </p>
   * 
   * @parameter property="doNotResolve" default-value="false"
   */
  protected boolean                 doNotResolve;

  protected Collection<Artifact> resolve() throws Exception {
    Collection<Artifact> deps = new ArrayList<Artifact>();
    if (doNotResolve) {
      for (String artifact : artifacts) {
        deps.add(new DefaultArtifact(artifact));
      }
    } else {
      for (String artifact : artifacts) {
        deps.addAll(resolveArtifact(new DefaultArtifact(artifact)));
      }
    }

    if (!resolveTransitively) {
      retainOriginalArtifacts(deps);
    }

    excludeGroupIds(deps);
    excludeArtifactIds(deps);

    return deps;
  }

  protected Collection<Artifact> resolveArtifact(Artifact artifact) throws Exception {
    DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME);
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(new Dependency(artifact, JavaScopes.RUNTIME));
    collectRequest.setRepositories(remoteRepos);
    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFlter);
    List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
    Collection<Artifact> resolvedArtifacts = new ArrayList<Artifact>();
    for (ArtifactResult artifactResult : artifactResults) {
      resolvedArtifacts.add(artifactResult.getArtifact());
    }
    return resolvedArtifacts;
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