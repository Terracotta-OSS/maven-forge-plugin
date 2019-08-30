/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Parameter;

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
public abstract class AbstractResolveDependenciesMojo extends AbstractArtifactResolvingMojo {

  /**
   * artifact groupId:artifactId:version
   */
  @Parameter(required = true, readonly = true)
  protected List<String>                artifacts;

  /**
   * comma separated list of groupIds to be excluded
   */
  @Parameter(required = false)
  protected String                      excludeGroupIds;

  /**
   * comma separated list of artifactIds to be excluded
   */
  @Parameter(required = false)
  protected String                      excludeArtifactIds;

  /**
   * resolve dependencies transitively or not, default is true
   */
  @Parameter(required = false, defaultValue = "true")
  protected boolean                     resolveTransitively;

  /**
   * don't resolve, just output whatever configured, useful when we only want to append to output what we know
   * <p>
   * However, you won't be able to get output listed as file
   * </p>
   */
  @Parameter(required = false, defaultValue = "false")
  protected boolean                     doNotResolve;

  protected Collection<Artifact> resolve() throws Exception {
    Collection<Artifact> deps = new ArrayList<Artifact>();
    if (doNotResolve) {
      for (String artifact : artifacts) {
        deps.add(createArtifact(artifact));
      }
    } else {
      for (String artifact : artifacts) {
        deps.addAll(resolveArtifact(createArtifact(artifact)));
      }
    }

    if (!resolveTransitively) {
      retainOriginalArtifacts(deps);
    }

    excludeGroupIds(deps);
    excludeArtifactIds(deps);

    return deps;
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
    for (String coords : artifacts) {
      ret.add(createArtifact(coords));
    }
    return ret;
  }

  private boolean isConfiguredArtifact(Artifact a, Collection<Artifact> configuredArtifacts) {
    for (Artifact itArtifact : configuredArtifacts) {
      if (a.getGroupId().equals(itArtifact.getGroupId()) && a.getArtifactId().equals(itArtifact.getArtifactId())
          && a.getBaseVersion().equals(itArtifact.getBaseVersion()) && a.getType().equals(itArtifact.getType())) { return true; }
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