/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * abstract class to resolve dependencies of a given artifact
 * 
 * @author hhuynh
 */
public abstract class AbstractResolveDependenciesMojo extends AbstractMojo {
  private static final Pattern          MAVEN_COORDS_REGX = Pattern
                                                              .compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");

  @Component
  private MavenProjectBuilder           mavenProjectBuilder;

  @Component
  private MavenProject                  project;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
   * System.getProperty("localRepository").
   */

  @Parameter(required = true, readonly = true, defaultValue = "${localRepository}")
  private ArtifactRepository            localRepository;

  /**
   * The remote plugin repositories declared in the POM.
   */
  @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
  private List<Repository>              remoteRepositories;

  @Component
  protected ProjectDependenciesResolver projectDependenciesResolver;

  @Component
  private DependencyGraphBuilder        dependencyGraphBuilder;

  @Component
  private ArtifactFactory               defaultArtifactFactory;

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

  private void getAllNodes(DependencyNode node, Set<DependencyNode> currentNodes) {
    currentNodes.add(node);
    for (DependencyNode currentNode : node.getChildren()) {
      getAllNodes(currentNode, currentNodes);
    }
  }

  protected Collection<Artifact> resolveArtifact(Artifact artifact) throws Exception {
    Collection<Artifact> resolvedArtifacts = new ArrayList<Artifact>();
    resolvedArtifacts.add(completeArtifact(artifact));
    MavenProject pomProject = mavenProjectBuilder.buildFromRepository(artifact, remoteRepositories, localRepository);

    // we start with the root node
    DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(pomProject,
                                                                          new CumulativeScopeArtifactFilter(Arrays
                                                                              .asList(Artifact.SCOPE_COMPILE,
                                                                                      Artifact.SCOPE_RUNTIME)));
    Set<DependencyNode> nodes = new HashSet<DependencyNode>();
    getAllNodes(rootNode, nodes);
    nodes.remove(rootNode);
    for (DependencyNode node : nodes) {
      Artifact nodeArtifact = node.getArtifact();
      Artifact completeArtifact = completeArtifact(nodeArtifact);
      resolvedArtifacts.add(completeArtifact);
    }

    return resolvedArtifacts;
  }

  private Artifact completeArtifact(Artifact artifact) {
    Artifact completeArtifact = defaultArtifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                                                                      artifact.getBaseVersion(), artifact.getScope(),
                                                                      artifact.getType());
    completeArtifact.setFile(new File(localRepository.getBasedir(), localRepository.pathOf(completeArtifact)));
    return completeArtifact;
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

  private Artifact createArtifact(String coords) {
    Matcher m = MAVEN_COORDS_REGX.matcher(coords);
    if (!(m.matches())) { throw new IllegalArgumentException(
                                                             "Bad artifact coordinates "
                                                                 + coords
                                                                 + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>"); }

    String groupId = m.group(1);
    String artifactId = m.group(2);
    String extension = get(m.group(4), "jar");
    String classifier = get(m.group(6), "");
    String version = m.group(7);
    // ( String groupId, String artifactId, String version, String type, String classifier )
    return defaultArtifactFactory.createArtifactWithClassifier(groupId, artifactId, version, extension, classifier);
  }

  private static String get(String value, String defaultValue) {
    return (((value == null) || (value.length() <= 0)) ? defaultValue : value);
  }

}