/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Used for clustered entity packaging:
 * <ul>
 * <li>Explodes embedded jars inside packaging dir</li>
 * <li>List content of zip entries that contain exploded jars</li>
 * <li>Rename .class files</li>
 * <li>Unpacks API into the final clustered entity distribution package</li>
 * <li>attaches API's javadoc to the distribution project</li>
 * <li>create a record of all the artifacts shaded into the final jar</li>
 * <ul>
 * 
 */
@Mojo(name = "packageClusteredEntity", requiresDependencyResolution = ResolutionScope.COMPILE)
public class PackageClusteredEntityMojo extends AbstractArtifactResolvingMojo {
  @Component
  protected MavenProject       project;

  @Parameter(required = false, defaultValue = ".class_terracotta")
  private String               privateClassSuffix;

  @Parameter(required = false, defaultValue = "server-content.txt")
  private String               serverContentFilename;

  @Parameter(required = false, defaultValue = "client-content.txt")
  private String               clientContentFilename;

  @Parameter(required = false, defaultValue = "false")
  private boolean              skip;

  @Parameter(required = true)
  private String               serverArtifact;

  @Parameter(required = true)
  private String               clientArtifact;

  @Parameter(required = true)
  private String               apiArtifact;

  @Parameter(required = false, defaultValue = "true")
  private boolean              attachJavadoc;

  @Parameter(required = false, defaultValue = "true")
  private boolean              createShadeRecord;

  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skipping clustered entity packaging");
      return;
    }
    File buildDirectory = new File(project.getBuild().getOutputDirectory());

    ShadeRecordTracker shadeRecordTracker = new ShadeRecordTracker();
    // Extract jars into respective folders
    Artifact serverImpl = completeArtifact(createArtifact(serverArtifact));
    shadeRecordTracker.add(serverImpl);
    extractArtifactJarTo(new File(buildDirectory, "server" + File.separator + serverImpl.getFile().getName()), serverImpl);
    Artifact clientImpl = completeArtifact(createArtifact(clientArtifact));
    shadeRecordTracker.add(clientImpl);
    extractArtifactJarTo(new File(buildDirectory, "client" + File.separator + clientImpl.getFile().getName()), clientImpl);
    for (Artifact dependency : collectDependencies(serverImpl, clientImpl)) {
      shadeRecordTracker.add(dependency);
      extractArtifactJarTo(new File(buildDirectory, "common" + File.separator + dependency.getFile()
          .getName()), dependency);
    }

    File serverEntriesFile = new File(buildDirectory, serverContentFilename);
    File clientEntriesFile = new File(buildDirectory, clientContentFilename);

    // convert .class into .clazz under embedded resources
    if (!privateClassSuffix.trim().equals("")) {
      getLog().info("Renaming private classes to use suffix " + privateClassSuffix);
      Set<String> commonResources = new LinkedHashSet<String>(renameResources(buildDirectory, "common"));
      Set<String> clientResources = new LinkedHashSet<String>(renameResources(buildDirectory, "client"));
      clientResources.addAll(commonResources);
      Set<String> serverResources = new LinkedHashSet<String>(renameResources(buildDirectory, "server"));
      serverResources.addAll(commonResources);

      writeContentFile(serverEntriesFile, serverResources);
      writeContentFile(clientEntriesFile, clientResources);
    }

    // Extract the contents of the API jar into the top level of the distribution jar
    Artifact api = completeArtifact(createArtifact(apiArtifact));
    shadeRecordTracker.add(api);
    extractArtifactJarTo(buildDirectory, api);

    if (attachJavadoc) {
      // Copy over the javadoc from the API package
      Artifact apiJavadoc = getJavadocArtifact(api);
      File javadocFile = new File(buildDirectory, project.getArtifactId() + "-" + project.getVersion() + "-javadoc.jar");
      try {
        FileUtils.copyFile(apiJavadoc.getFile(), javadocFile);
      } catch (IOException e) {
        throw new MojoExecutionException("Failed to copy javadoc", e);
      }

      // Create an artifact for javadocing the distribution package
      Artifact javadocArtifact = defaultArtifactFactory.createArtifactWithClassifier(project.getGroupId(),
          project.getArtifactId(), project.getVersion(), "jar", "javadoc");
      javadocArtifact.setFile(javadocFile);

      // Attach the copied javadoc to the project
      project.addAttachedArtifact(javadocArtifact);
    }
    try {
      shadeRecordTracker.writeTo(buildDirectory);
    } catch (FileNotFoundException e) {
      throw new MojoExecutionException("Failed to create shade record", e);
    }
  }

  private void extractArtifactJarTo(File dir, Artifact artifact) throws MojoExecutionException {
    try {
      getLog().info("Exploding " + artifact.getFile());
      ensureMkdirs(dir);
      unzip(dir, artifact.getFile());
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to extra jar.", e);
    }
  }

  private Set<Artifact> collectDependencies(final Artifact serverImpl, final Artifact clientImpl) throws MojoExecutionException {
    Set<Artifact> dependencies = new HashSet<Artifact>();
    try {
      dependencies.addAll(resolveArtifact(serverImpl));
      dependencies.addAll(resolveArtifact(clientImpl));
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to collect depdendencies.", e);
    }
    dependencies.remove(serverImpl);
    dependencies.remove(clientImpl);
    return dependencies;
  }

  private void writeContentFile(final File entriesFilename, final Set<String> resources) throws MojoExecutionException {PrintWriter pw = null;
    try {
      pw = new PrintWriter(entriesFilename);
      for (String resource : resources) {
        pw.println(resource);
      }
      pw.close();
    } catch (IOException e) {
      throw new MojoExecutionException("IO error", e);
    } finally {
      if (pw != null) {
        pw.close();
      }
    }
  }

  private Set<String> renameResources(final File buildDirectory, final String subDir) {
    File dir = new File(buildDirectory, subDir);
    if (!dir.isDirectory()) return Collections.emptySet();
    Set<String> renamed = new LinkedHashSet<String>();
    final Iterator it = FileUtils.iterateFiles(dir, null, true);
    while (it.hasNext()) {
      File resource = (File) it.next();
      if (resource.getName().endsWith(".class")) {
        File clazzFile = new File(resource.getParentFile(), resource.getName()
            .replace(".class", privateClassSuffix));
        resource.renameTo(clazzFile);
        renamed.add(cleanupSuffix(buildDirectory.getAbsolutePath() + File.separator, clazzFile.getAbsolutePath()));
      } else {
        renamed.add(cleanupSuffix(buildDirectory.getAbsolutePath() + File.separator, resource.getAbsolutePath()));
      }
    }
    return renamed;
  }

  private void unzip(File dest, File source) {
    Project dummyProject = new Project();
    dummyProject.init();
    Expand unzip = new Expand();
    unzip.setProject(dummyProject);
    unzip.setDest(dest);
    unzip.setSrc(source);
    unzip.execute();
  }

  private void ensureMkdirs(File f) throws IOException {
    if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Failed to mkdirs " + f);
  }

  private static String cleanupSuffix(String suffix, String path) {
    String cleaned = path.replace(suffix, "");
    return cleaned.replace('\\', '/');
  }

  private class ShadeRecordTracker extends HashSet<Artifact> {
    void writeTo(File dir) throws FileNotFoundException {
      if (!createShadeRecord) {
        return;
      }
      PrintWriter writer = new PrintWriter(new FileOutputStream(new File(dir, "shade-record.txt")));
      try {
        for (Artifact artifact : this) {
          writer.println(artifact.toString());
        }
      } finally {
        writer.close();
      }
    }
  }
}
