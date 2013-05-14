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
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Set L2 (terracotta-xxx.jar or terracotta-ee-xxx.jar) classpath to Maven properties
 * 
 * @author hhuynh
 * @goal setl2classpath
 * @requiresDependencyResolution test
 */
public class SetL2ClasspathMojo extends AbstractMojo {

  /** @component */
  private MavenProjectBuilder mavenProjectBuilder;
  /** @component */
  private ArtifactMetadataSource artifactMetadataSource;
  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject     project;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
   * System.getProperty("localRepository").
   * 
   * @parameter expression="${localRepository}"
   * @required
   * @readonly
   */
  private ArtifactRepository localRepository;

  /**
   * Creates the artifact.
   * 
   * @component
   */
  private ArtifactFactory    artifactFactory;

  /**
   * The remote plugin repositories declared in the POM.
   * 
   * @parameter expression="${project.remoteArtifactRepositories}"
   * @since 2.2
   */
  @SuppressWarnings("rawtypes")
  private List               remoteRepositories;

  /**
   * Resolves the artifacts needed.
   * 
   * @component
   */
  private ArtifactResolver   artifactResolver;

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
        Set terracottaDirectDependencies = pomProject.createArtifacts(artifactFactory, null, null);
        ArtifactFilter testAndWarFilter = new ArtifactFilter() {
          public boolean include(Artifact artifact) {
            if(artifact.getScope().equals("test") || (artifact.getType().equals("war")) ) {
              return false;
            }
            return true;
          }
        };
        ArtifactResolutionResult arr = artifactResolver.resolveTransitively(terracottaDirectDependencies, a, pomProject.getManagedVersionMap(), localRepository, remoteRepositories, artifactMetadataSource, testAndWarFilter);

        Set<Artifact> terracottaDirectAndTransitiveDependencies = arr.getArtifacts();
        terracottaDirectAndTransitiveDependencies.add(a);
        int size = terracottaDirectAndTransitiveDependencies.size();
        int currentPosition = 0;
        for (Artifact artifact : terracottaDirectAndTransitiveDependencies) {
          File file = artifact.getFile();
          sb.append(file.getCanonicalPath());
          if (currentPosition < size - 1) {
            sb.append(File.pathSeparator);
          }
          currentPosition++;
        }
        return sb.toString();
      }
    }
    getLog().error("No org.terracotta:terracotta(-ee) could be found among this project dependencies; hence no terracotta classpath will be generated!");
    return "";
  }
}
