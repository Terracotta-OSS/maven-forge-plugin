/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Set L2 (terracotta-xxx.jar or terracotta-ee-xxx.jar) classpath to Maven properties
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
      String l2Classppath = getTerracottaClassPath(terracottaJarFile);
      if (Boolean.valueOf(project.getProperties().getProperty("devmode"))) {
        l2Classppath = generateDevModeClasspath(terracottaJarFile) + l2Classppath;
      }
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

  /**
   * assume coordinate in the form groupId:artifactId:version:type
   * 
   * @param artifactCoordinate
   * @return
   * @throws IOException
   */
  private File getArtifactFile(String artifactCoordinate) throws Exception {
    String[] coords = artifactCoordinate.split(":");
    if (coords.length != 4) { throw new RuntimeException(
                                                         "Coordinate doesn't match template [groupId:artifactId:version:type]: "
                                                             + artifactCoordinate); }
    String groupId = coords[0];
    String artifactId = coords[1];
    String version = coords[2];
    String type = coords[3];

    Artifact artifact = artifactFactory.createArtifact(groupId, artifactId, version, "test", type);
    getLog().debug("XXX resolving L2 classpath element " + artifactCoordinate);
    artifactResolver.resolveAlways(artifact, remoteRepositories, localRepository);

    File artifactFile = new File(localRepository.getBasedir(), groupId.replace('.', '/') + "/" + artifactId + "/"
                                                               + version + "/" + artifactId + "-" + version + "."
                                                               + type);

    if (!artifactFile.exists()) throw new RuntimeException("Failed to resolve artifact: " + artifactCoordinate);

    return artifactFile.getCanonicalFile();
  }

  private String getTerracottaClassPath(File terracottaJar) throws Exception {
    Manifest manifest = null;
    FileInputStream in = null;
    try {
      in = new FileInputStream(terracottaJar);
      JarInputStream jarStream = new JarInputStream(in);
      manifest = jarStream.getManifest();
      jarStream.close();
      String mavenClassPath = manifest.getMainAttributes().getValue("Maven-Class-Path");
      if (mavenClassPath == null) { throw new Exception("Coudln't find Maven-Class-Path in manifest of "
                                                        + terracottaJar); }
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
      IOUtils.closeQuietly(in);
    }
  }

  private String generateDevModeClasspath(File terracottaJarFile) throws IOException {
    ZipFile zip = new ZipFile(terracottaJarFile);
    ZipEntry devmodeClassDir = zip.getEntry("devmode-classdir.txt");
    if (devmodeClassDir == null) {
      getLog().warn("devmode is on but couldn't find devmode-classdir.txt inside terracotta jar" + terracottaJarFile);
      zip.close();
      return "";
    }
    StringBuilder sb = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(zip.getInputStream(devmodeClassDir)));
      String line = null;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0) continue;
        for (String part : line.split(File.pathSeparator)) {
          String path = part.trim();
          if (path.length() == 0) continue;
          File file = new File(path);
          sb.append(file.getCanonicalPath()).append(File.pathSeparator);
        }
      }
      return sb.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      zip.close();
      IOUtils.closeQuietly(reader);
    }
  }
}
