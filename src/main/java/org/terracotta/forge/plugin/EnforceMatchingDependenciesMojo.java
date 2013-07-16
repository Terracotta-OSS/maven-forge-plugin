/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.*;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.repository.RemoteRepository;

import java.util.*;

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
   * project instance. Injected automtically by Maven
   */
  @Parameter(required = true, property = "project", readonly = true)
  private MavenProject           project;

  /**
   * List of Remote Repositories used by the resolver
   */
  @Parameter( defaultValue = "${project.remoteArtifactRepositories}" )
  private List<RemoteRepository>                   remoteRepositories;

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

  @Parameter( readonly = true, defaultValue = "${repositorySystemSession}" )
  private RepositorySystemSession repoSession;

  public void execute() throws MojoExecutionException {
    try {

      String coords = getEnforcedArtifactCoordinates();
      DefaultArtifact enforceArtifact = new DefaultArtifact(coords);
      MavenProject enforcePom = mavenProjectBuilder.buildFromRepository( RepositoryUtils.toArtifact(enforceArtifact), remoteRepositories, localRepository);
      DefaultDependencyResolutionRequest enforceResolutionRequest = new DefaultDependencyResolutionRequest(enforcePom, repoSession);
      DependencyResolutionResult enforceResolutionResult = projectDependenciesResolver.resolve(enforceResolutionRequest);

      Set<Artifact> enforceArtifacts = filterCompileAndRuntimeScope(enforceResolutionResult.getDependencies());
      getLog().debug("enforce artifacts before exclusions: " + enforceArtifacts);
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

  private String getEnforcedArtifactCoordinates() {
    String coords = enforceGroupId + ":" + enforceArtifactId ;
    if(enforceType!= null && !"".equals(enforceType)) {
      coords+=":" + enforceType;
    }
    coords+= ":" + enforceVersion;
    return coords;
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

  private static Set<Artifact> filterCompileAndRuntimeScope(Collection<Dependency> dependencies) {
    Set<Artifact> result = new HashSet<Artifact>();
    for (Dependency dependency : dependencies) {
        if (Artifact.SCOPE_COMPILE.equals(dependency.getScope()) || Artifact.SCOPE_RUNTIME.equals(dependency.getScope())) {
          result.add(RepositoryUtils.toArtifact(dependency.getArtifact()));
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
