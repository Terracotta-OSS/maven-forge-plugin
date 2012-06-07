/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * Set L2 (terracotta-xxx.jar or terracotta-ee-xxx.jar) classpath to Maven
 * properties
 * 
 * @author hhuynh
 * @goal setl2classpath
 * @requiresDependencyResolution test
 */
public class SetL2ClasspathMojo extends AbstractMojo {
  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject     project;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of
   * localRepository in unit tests use System.getProperty("localRepository").
   * 
   * @parameter expression="${localRepository}"
   * @required
   * @readonly
   */
  private ArtifactRepository localRepository;

  /**
   * 
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    File terracottaJarFile = getTerracottaJar();
    getLog().info("Terracotta jar file: " + terracottaJarFile);
    if (terracottaJarFile == null) {
      throw new MojoExecutionException("Couldn't find Terracotta core artifact");
    }
    try {
      getLog().debug(
          "Setting L2 classpath to system property tc.tests.info.l2.classpath");
      project.getProperties().put("tc.tests.info.l2.classpath",
          getTerracottaClassPath(terracottaJarFile));
    } catch (Exception e) {
      throw new MojoExecutionException("Error trying to find L2 classpath", e);
    }
  }

  private File getTerracottaJar() {
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getDependencyArtifacts();

    for (Artifact a : artifacts) {
      if ((a.getArtifactId().equals("terracotta") || a.getArtifactId().equals(
          "terracotta-ee"))
          && a.getGroupId().equals("org.terracotta")) {
        return a.getFile();
      }
    }
    return null;
  }

  /**
   * assume coordinate in the form groupId:artifactId:version:type
   * 
   * @param artifactCoordinate
   * @return
   * @throws IOException
   */
  private File getArtifactFile(String artifactCoordinate) throws IOException {
    String[] coords = artifactCoordinate.split(":");
    if (coords.length != 4) {
      throw new RuntimeException(
          "Coordinate doesn't match template [groupId:artifactId:version:type]: "
              + artifactCoordinate);
    }
    String groupId = coords[0];
    String artifactId = coords[1];
    String version = coords[2];
    String type = coords[3];

    File artifactFile = new File(localRepository.getBasedir(), groupId.replace(
        '.', '/')
        + "/"
        + artifactId
        + "/"
        + version
        + "/"
        + artifactId
        + "-"
        + version + "." + type);

    return artifactFile.getCanonicalFile();
  }

  private String getTerracottaClassPath(File terracottaJar) throws Exception {
    Manifest manifest = null;
    FileInputStream in = null;
    try {
      in = new FileInputStream(terracottaJar);
      JarInputStream jarStream = new JarInputStream(in);
      manifest = jarStream.getManifest();
      String mavenClassPath = manifest.getMainAttributes().getValue(
          "Maven-Class-Path");
      if (mavenClassPath == null) {
        throw new Exception("Coudln't find Maven-Class-Path in manifest of "
            + terracottaJar);
      }
      StringBuilder sb = new StringBuilder();
      sb.append(terracottaJar);

      String[] classpathElements = mavenClassPath.split(" ");
      for (String element : classpathElements) {
        File path = getArtifactFile(element);
        if (path != null) {
          sb.append(File.pathSeparator).append(path.getAbsolutePath());
        }
      }
      return sb.toString();
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
  }
}
