/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.*;
import org.sonatype.aether.RepositorySystemSession;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

//import org.apache.maven.artifact.resolver.ArtifactResolver;

/**
 * Set L2 (terracotta-xxx.jar or terracotta-ee-xxx.jar) classpath to Maven properties
 */
@Mojo( name = "setl2classpath", requiresDependencyResolution = ResolutionScope.TEST )
public class SetL2ClasspathMojo extends AbstractMojo {


  @Component
  private MavenProjectBuilder mavenProjectBuilder;

  @Component
  private MavenProject project;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
   * System.getProperty("localRepository").
   *
   */

  @Parameter( required = true, readonly = true, defaultValue = "${localRepository}" )
  private ArtifactRepository localRepository;

  /**
   * The remote plugin repositories declared in the POM.
   */
  @Parameter( defaultValue = "${project.remoteArtifactRepositories}" )
  private List<Repository>               remoteRepositories;


  /**
   * The current repository/network configuration of Maven.
   *
   */
  @Parameter( readonly = true, defaultValue = "${repositorySystemSession}" )
  private RepositorySystemSession repoSession;

  @Component
  protected ProjectDependenciesResolver projectDependenciesResolver;

  /**
   * 
   */
  public void execute() throws MojoExecutionException {
    File terracottaJarFile = getTerracottaJar();
    if (terracottaJarFile == null) { throw new MojoExecutionException("Couldn't find Terracotta core artifact"); }
    try {
      String l2Classppath = getTerracottaClassPath();
      getLog().debug("Setting tc.tests.info.l2.classpath to: " + l2Classppath);
      project.getProperties().put("tc.tests.info.l2.classpath", l2Classppath);
    } catch (Exception e) {
      throw new MojoExecutionException("Error trying to find L2 classpath", e);
    }
  }

  private File getTerracottaJar() {
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getDependencyArtifacts();

    for (Artifact a : artifacts) {
      if ((a.getArtifactId().equals("terracotta") || a.getArtifactId().equals("terracotta-ee"))
              && a.getGroupId().equals("org.terracotta")) { return a.getFile(); }
    }
    return null;
  }

  private String getTerracottaClassPath() throws Exception{
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getDependencyArtifacts();

    for (Artifact a : artifacts) {
      if ((a.getArtifactId().equals("terracotta") || a.getArtifactId().equals("terracotta-ee"))
              && a.getGroupId().equals("org.terracotta")) {

        StringBuilder sb = new StringBuilder();
        MavenProject pomProject = mavenProjectBuilder.buildFromRepository(a, remoteRepositories, localRepository);
//        Set terracottaDirectDependencies = pomProject.createArtifacts(artifactFactory, null, null);
//        ArtifactFilter testAndWarFilter = new ArtifactFilter() {
//          public boolean include(Artifact artifact) {
//            if(artifact.getScope().equals("test") || (artifact.getType().equals("war")) ) {
//              return false;
//            }
//            return true;
//          }
//        };


        DefaultDependencyResolutionRequest dependencyResolutionRequest = new DefaultDependencyResolutionRequest(pomProject, repoSession);
//        dependencyResolutionRequest.setResolutionFilter(dependencyFilter);
        DependencyResolutionResult dependencyResolutionResult;

        dependencyResolutionResult = projectDependenciesResolver.resolve(dependencyResolutionRequest);

        Set<Artifact> terracottaDirectAndTransitiveDependencies = new LinkedHashSet<Artifact>();
        if (dependencyResolutionResult.getDependencyGraph() != null
                && !dependencyResolutionResult.getDependencyGraph().getChildren().isEmpty()) {
          RepositoryUtils.toArtifacts(terracottaDirectAndTransitiveDependencies, dependencyResolutionResult.getDependencyGraph().getChildren(),
                  Collections.singletonList(pomProject.getArtifact().getId()), null);
        }
//
//        ArtifactResolutionResult arr = artifact.resolveTransitively(terracottaDirectDependencies, a, pomProject.getManagedVersionMap(), localRepository, remoteRepositories, artifactMetadataSource, testAndWarFilter);
//        ArtifactResolutionResult arr = null;
//        Set<Artifact> terracottaDirectAndTransitiveDependencies = arr.getArtifacts();
        terracottaDirectAndTransitiveDependencies.add(a);
        int size = terracottaDirectAndTransitiveDependencies.size();
        int currentPosition = 0;
        for (Artifact artifact : terracottaDirectAndTransitiveDependencies) {
          if(!artifact.getScope().equals("test") && !(artifact.getType().equals("war")) ) {
            File file = artifact.getFile();
            sb.append(file.getCanonicalPath());
            if (currentPosition < size - 1) {
              sb.append(File.pathSeparator);
            }
            currentPosition++;
          }
        }
        return sb.toString();
      }
    }
    getLog().error("No org.terracotta:terracotta(-ee) could be found among this project dependencies; hence no terracotta classpath will be generated!");
    return "";
  }
}
