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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * This mojo will compare the project's dependencies with the enforcedArtifact dependencies.
 * If the current project dependencies set does not contain all the enforcedArtifact deps, it will fail.
 *
 * @author hhuynh
 */
@Mojo( name = "enforceDependencies", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME )
public class EnforceMatchingDependenciesMojo extends AbstractMojo {

  /**
   * project instance. Injected automatically by Maven
   */
  @Parameter(required = true, property = "project", readonly = true)
  private MavenProject           project;

  /**
   * List of Remote Repositories used by the resolver
   */
  @Parameter( defaultValue = "${project.remoteArtifactRepositories}" )
  private List<Repository>                   remoteRepositories;

  /**
   * Location of the local repository.
   */
  @Parameter(required = true, property = "localRepository", readonly = true)
  private ArtifactRepository     localRepository;

  /**
   * The target pom's artifactId
   */
  @Parameter(required = true, property = "enforceArtifactId")
  private String                   enforceArtifactId;

  /**
   * The target pom's groupId
   */
  @Parameter(required = true, property = "enforceGroupId")
  private String                   enforceGroupId;

  /**
   * The target pom's type
   */
  @Parameter(required = false, property = "enforceType", defaultValue = "jar")
  private String enforceType;

  /**
   * The target pom's version
   */
  @Parameter(required = true, property = "enforceVersion")
  private String                   enforceVersion;

  @Parameter(required = false, property = "excludeGroupIds")
  private String                   excludeGroupIds;


  @Component
  private RepositorySystem repositorySystem;

  @Component
  private ProjectDependenciesResolver projectDependenciesResolver;

  @Component
  private MavenProjectBuilder mavenProjectBuilder;

  @Component
  private DependencyGraphBuilder dependencyGraphBuilder;

  @Component
  private ArtifactFactory defaultArtifactFactory;

  public void execute() throws MojoExecutionException {
    try {
      Artifact enforceArtifact = defaultArtifactFactory.createArtifact(enforceGroupId,enforceArtifactId,enforceVersion,"",enforceType);
      MavenProject enforcePom = mavenProjectBuilder.buildFromRepository( enforceArtifact, remoteRepositories, localRepository);
      DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(enforcePom, new CumulativeScopeArtifactFilter(Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME)));

      Set<DependencyNode> nodes = new HashSet<DependencyNode>();
      getAllNodes(rootNode, nodes);
      nodes.remove(rootNode);
      Set<Artifact> enforceArtifacts =  new HashSet<Artifact>();
      for (DependencyNode node : nodes) {
        Artifact artifact = node.getArtifact();
        enforceArtifacts.add(defaultArtifactFactory.createArtifactWithClassifier(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), artifact.getClassifier()));
      }


      getLog().debug("enforce artifacts before exclusions: " + enforceArtifacts);
      enforceArtifacts = filterCompileAndRuntimeScope(enforceArtifacts);
      if (excludeGroupIds != null) {
        enforceArtifacts = filterExcludeGroupIds(enforceArtifacts, excludeGroupIds);
        getLog().debug("enforce artifacts after exclusions: " + enforceArtifacts);
      }

      Set<Artifact> artifacts = project.getArtifacts();
      Set<Artifact> thisProjectArtifacts = filterCompileAndRuntimeScope(artifacts);
      getLog().debug("current artifacts: " + thisProjectArtifacts);

      // enforce artifacts should be a subset of this project's artifacts
      if (!thisProjectArtifacts.containsAll(enforceArtifacts)) {
        Set<Artifact> missingArtifacts = new HashSet<Artifact>(enforceArtifacts);
        missingArtifacts.removeAll(thisProjectArtifacts);
        String message = "This pom is missing some dependencies of the enforcing artifact " + enforceArtifact + "\n";
        message += "Missing " + missingArtifacts;
        throw new MojoFailureException(message);
      }

    } catch (Exception e) {
      getLog().error(e.getMessage());
      throw new MojoExecutionException("Error", e);
    }
  }

  private void getAllNodes(DependencyNode node,Set<DependencyNode> currentNodes) {
    currentNodes.add(node);
    for (DependencyNode currentNode : node.getChildren()) {
      getAllNodes(currentNode, currentNodes);
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
    Set<Artifact> result = new HashSet<Artifact>();
    List<String> excludes = Arrays.asList(excludeGroupIds.split("\\s*,\\s*"));
    for (Artifact a : artifacts) {
      if (!excludes.contains(a.getGroupId())) {
        result.add(a);
      }
    }
    return result;
  }
}
