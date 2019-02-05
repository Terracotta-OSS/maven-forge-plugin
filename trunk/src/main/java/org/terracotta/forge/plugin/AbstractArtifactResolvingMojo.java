package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author twu
 */
public abstract class AbstractArtifactResolvingMojo extends AbstractMojo {
  private static final Pattern MAVEN_COORDS_REGX = Pattern
      .compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
  @Component
  private MavenProjectBuilder mavenProjectBuilder;

  @Component
  private MavenProject project;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
   * System.getProperty("localRepository").
   */

  @Parameter(required = true, readonly = true, defaultValue = "${localRepository}")
  private ArtifactRepository localRepository;

  /**
   * The remote plugin repositories declared in the POM.
   */
  @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
  private List<Repository> remoteRepositories;

  @Component
  protected ProjectDependenciesResolver projectDependenciesResolver;

  @Component
  private DependencyGraphBuilder dependencyGraphBuilder;

  @Component
  protected ArtifactFactory defaultArtifactFactory;

  private static String get(String value, String defaultValue) {
    return (((value == null) || (value.length() <= 0)) ? defaultValue : value);
  }


  private void getAllNodes(DependencyNode node, Set<DependencyNode> currentNodes) {
    if (currentNodes.add(node)) {
      for (DependencyNode currentNode : node.getChildren()) {
        getAllNodes(currentNode, currentNodes);
      }
    }
  }

  protected Collection<Artifact> resolveArtifact(Artifact artifact) throws Exception {
    Collection<Artifact> resolvedArtifacts = new ArrayList<Artifact>();
    resolvedArtifacts.add(completeArtifact(artifact));
    MavenProject pomProject = mavenProjectBuilder.buildFromRepository(artifact, remoteRepositories, localRepository);

    // we start with the root node
    DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(pomProject, new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
    Set<DependencyNode> nodes = new HashSet<DependencyNode>();
    getAllNodes(rootNode, nodes);
    nodes.remove(rootNode);
    for (DependencyNode node : nodes) {
      Artifact nodeArtifact = node.getArtifact();
      Artifact completeArtifact = completeArtifact(nodeArtifact);
      getLog().info("completed artifact " + completeArtifact);
      resolvedArtifacts.add(completeArtifact);
    }

    return resolvedArtifacts;
  }

  protected Artifact completeArtifact(Artifact artifact) {
    Artifact completeArtifact = defaultArtifactFactory.createArtifactWithClassifier(artifact.getGroupId(), artifact.getArtifactId(),
        artifact.getBaseVersion(), artifact.getType(), artifact.getClassifier());
    artifact.setScope(artifact.getScope());
    completeArtifact.setFile(new File(localRepository.getBasedir(), localRepository.pathOf(completeArtifact)));
    return completeArtifact;
  }

  protected Artifact createArtifact(String coords) {
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

  protected Artifact getJavadocArtifact(Artifact artifact) {
    return completeArtifact(defaultArtifactFactory.createArtifactWithClassifier(artifact.getGroupId(),
        artifact.getArtifactId(), artifact.getVersion(), "jar", "javadoc"));
  }
}
