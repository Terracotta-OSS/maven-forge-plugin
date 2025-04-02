/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

  @Component
  protected ProjectDependenciesResolver projectDependenciesResolver;

  @Component
  private DependencyGraphBuilder dependencyGraphBuilder;

  @Component
  private ArtifactFactory defaultArtifactFactory;

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

  private boolean isTerracottaJar(Artifact artifact) {
    return (artifact.getArtifactId().equals("terracotta") && artifact.getGroupId().equals("org.terracotta"))
            || (artifact.getArtifactId().equals("terracotta-ee") && artifact.getGroupId().equals("com.terracottatech"));
  }

  private File getTerracottaJar() {
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getDependencyArtifacts();

    for (Artifact a : artifacts) {
      if (isTerracottaJar(a)) {
        return a.getFile();
      }
    }
    return null;
  }

  private String getTerracottaClassPath() throws Exception{
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getDependencyArtifacts();

    for (Artifact a : artifacts) {
      if (isTerracottaJar(a)) {

        StringBuilder sb = new StringBuilder();
        MavenProject pomProject = mavenProjectBuilder.buildFromRepository(a, remoteRepositories, localRepository);

        // we start with the root node
        DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(pomProject, new CumulativeScopeArtifactFilter(Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME)));
        Set<DependencyNode> nodes = new HashSet<DependencyNode>();
        getAllNodes(rootNode, nodes);
        nodes.remove(rootNode);
        Set<Artifact> terracottaDirectAndTransitiveDependencies =  new TreeSet<Artifact>();
        for (DependencyNode node : nodes) {
          Artifact completeArtifact = ArtifactUtils.copyArtifactSafe(node.getArtifact());
          if (completeArtifact != null) {
            completeArtifact.setFile(new File(localRepository.getBasedir(), localRepository.pathOf(completeArtifact)));
            terracottaDirectAndTransitiveDependencies.add(completeArtifact);
          }
        }
        //we end up with the list of artifacts under the root node

        terracottaDirectAndTransitiveDependencies.add(a);
        int size = terracottaDirectAndTransitiveDependencies.size();
        int currentPosition = 0;
        for (Artifact artifact : terracottaDirectAndTransitiveDependencies) {
          if (!artifact.getScope().equals("test")) {
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

  private void getAllNodes(DependencyNode node,Set<DependencyNode> currentNodes) {
    currentNodes.add(node);
    for (DependencyNode currentNode : node.getChildren()) {
      getAllNodes(currentNode, currentNodes);
    }
  }

}
