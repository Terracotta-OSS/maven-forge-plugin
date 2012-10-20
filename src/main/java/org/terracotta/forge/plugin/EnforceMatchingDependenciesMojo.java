/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Given a zip file, this goals will create a content.txt that lists zip entries
 * 
 * @author hhuynh
 * @goal enforceDependencies
 * @requiresDependencyResolution compile
 */
public class EnforceMatchingDependenciesMojo extends AbstractMojo {

  /**
   * project instance. Injected automtically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject           project;

  /**
   * @component
   * @required
   * @readonly
   */
  protected MavenProjectBuilder    projectBuilder;

  /**
   * @component
   * @required
   * @readonly
   */
  protected ArtifactMetadataSource metadataSource;

  /**
   * Used to look up Artifacts in the remote repository.
   * 
   * @parameter expression= "${component.org.apache.maven.artifact.factory.ArtifactFactory}"
   * @required
   * @readonly
   */
  protected ArtifactFactory        artifactFactory;

  /**
   * Used to look up Artifacts in the remote repository.
   * 
   * @parameter expression= "${component.org.apache.maven.artifact.resolver.ArtifactResolver}"
   * @required
   * @readonly
   */
  protected ArtifactResolver       artifactResolver;

  /**
   * List of Remote Repositories used by the resolver
   * 
   * @parameter expression="${project.remoteArtifactRepositories}"
   * @readonly
   * @required
   */
  protected List                   remoteRepositories;

  /**
   * Location of the local repository.
   * 
   * @parameter expression="${localRepository}"
   * @readonly
   * @required
   */
  protected ArtifactRepository     localRepository;

  /**
   * The target pom's artifactId
   * 
   * @parameter expression="${enforceArtifactId}"
   * @required
   */
  private String                   enforceArtifactId;

  /**
   * The target pom's groupId
   * 
   * @parameter expression="${enforceGroupId}"
   * @required
   */
  private String                   enforceGroupId;

  /**
   * The target pom's type
   * 
   * @parameter expression="${enforceType}" default-value="jar"
   * @optional
   */
  private String                   enforceType;

  /**
   * The target pom's version
   * 
   * @parameter expression="${enforceVersion}"
   * @required
   */
  private String                   enforceVersion;

  /**
   * @parameter expression="{excludeGroupIds}"
   * @optional
   */
  private String                   excludeGroupIds;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void execute() throws MojoExecutionException {
    try {
      Artifact enforceArtifact = this.artifactFactory.createArtifact(enforceGroupId, enforceArtifactId, enforceVersion,
                                                                     "", enforceType);

      artifactResolver.resolve(enforceArtifact, this.remoteRepositories, this.localRepository);

      Artifact pomArtifact = this.artifactFactory.createArtifact(enforceGroupId, enforceArtifactId, enforceVersion, "",
                                                                 "pom");

      MavenProject projectForPom = null;
      projectForPom = projectBuilder.buildFromRepository(pomArtifact, remoteRepositories, localRepository);

      List dependencies = projectForPom.getDependencies();

      Set dependencyArtifacts = MavenMetadataSource.createArtifacts(artifactFactory, dependencies, null, null, null);
      dependencyArtifacts.add(projectForPom.getArtifact());

      ArtifactResolutionResult result = artifactResolver.resolveTransitively(dependencyArtifacts, pomArtifact,
                                                                             Collections.EMPTY_MAP, localRepository,
                                                                             remoteRepositories, metadataSource, null,
                                                                             Collections.EMPTY_LIST);

      Set<Artifact> enforceArtifacts = filterCompileAndRuntimeScope(result.getArtifacts());
      getLog().info("enforce artifacts before exclusions: " + enforceArtifacts);
      if (excludeGroupIds != null) {
        enforceArtifacts = filterExcludeGroupIds(enforceArtifacts, excludeGroupIds);
        getLog().info("enforce artifacts after exclusions: " + enforceArtifacts);
      }

      Set<Artifact> thisProjectartifacts = filterCompileAndRuntimeScope(project.getArtifacts());
      getLog().info("current artifacts: " + thisProjectartifacts);

      // enforce artifacts should be a subset of this project's artifacts
      if (!thisProjectartifacts.containsAll(enforceArtifacts)) {
        Set<Artifact> missingArtifacts = new HashSet(enforceArtifacts);
        missingArtifacts.removeAll(thisProjectartifacts);
        String message = "This pom is missing some dependencies of the enforcing artifact " + enforceArtifact + "\n";
        message += "Missing " + missingArtifacts;
        throw new MojoFailureException(message);
      }

    } catch (Exception e) {
      getLog().error(e.getMessage());
      throw new MojoExecutionException("Error", e);
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
