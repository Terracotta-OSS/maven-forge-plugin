/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used for toolkit runtime packaging:
 * <ul>
 * <li>Explodes embedded jars inside packaging dir</li>
 * <li>List content of zip entries that contain exploded jars</li>
 * <li>Rename .class files</li>
 * <ul>
 * 
 * @author hhuynh
 * @goal parepareToolkitContent
 */
public class PrepareToolkitContentMojo extends AbstractMojo {
  private static final String  EMBEDDED_JARS_REGEX   = createToolkitEmbeddedJarsRegex();
  private static final Pattern EMBEDDED_JARS_PATTERN = Pattern.compile(EMBEDDED_JARS_REGEX);
  /**
   * project instance. Injected automtically by Maven
   * 
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject       project;

  /**
   * @parameter expression="${packagingDir}"
   * @required
   */
  private File                 packagingDir;

  /**
   * @parameter expression="${privateClassSuffix}"
   * @optional
   */
  private String               privateClassSuffix;

  /**
   * @parameter expression="${toolkitContentFilename}" default-value="toolkit-content.txt"
   * @optional
   */
  private String               toolkitContentFilename;

  public void execute() throws MojoExecutionException {
    if (!packagingDir.exists()) throw new MojoExecutionException("Packaging dir not found: " + packagingDir);
    File entriesFile = new File(packagingDir, toolkitContentFilename);
    if (entriesFile.exists()) {
      getLog().info("Embedded jars have been exploded... skipping");
      return;
    }
    try {
      StringBuilder internalJars = new StringBuilder();

      // exploding embedded jars
      Iterator it = FileUtils.iterateFiles(packagingDir, new String[] { "jar" }, true);
      while (it.hasNext()) {
        File jar = (File) it.next();
        Matcher m = EMBEDDED_JARS_PATTERN.matcher(jar.getAbsolutePath());
        if (!m.matches()) continue;
        getLog().info("Exploding " + jar);
        internalJars.append(m.group(1).replace('\\', '/')).append("\n");
        String name = jar.getName();
        File renamedJar = new File(jar.getParent(), name + ".tmp");
        ensureRename(jar, renamedJar);
        ensureMkdirs(jar);
        unzip(jar, renamedJar);
        ensureDelete(renamedJar);
      }

      // convert .class into .clazz under embedded resources
      if (privateClassSuffix != null) {
        getLog().info("Renaming private classes to use suffix " + privateClassSuffix);
        for (String subDir : Arrays.asList("ehcache", "L1", "TIMs")) {
          File dir = new File(packagingDir, subDir);
          if (!dir.isDirectory()) continue;
          it = FileUtils.iterateFiles(dir, new String[] { "class" }, true);
          while (it.hasNext()) {
            File classFile = (File) it.next();
            File clazzFile = new File(classFile.getParentFile(), classFile.getName().replace(".class",
                                                                                             privateClassSuffix));
            classFile.renameTo(clazzFile);
          }
        }
      }

      PrintWriter pw = null;
      try {
        pw = new PrintWriter(entriesFile);
        pw.print(internalJars.toString());
        pw.close();
      } catch (IOException e) {
        throw new MojoExecutionException("IO error", e);
      } finally {
        if (pw != null) {
          pw.close();
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Error", e);
    }

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

  private void ensureDelete(File f) throws IOException {
    if (!f.delete()) throw new IOException("Failed to delete file " + f);
  }

  private void ensureMkdirs(File f) throws IOException {
    if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Failed to mkdirs " + f);
  }

  private void ensureRename(File from, File to) throws IOException {
    if (!from.renameTo(to)) throw new IOException("Failed to rename " + from + " to " + to);
    from.delete();
  }

  private static String createToolkitEmbeddedJarsRegex() {
    String pathRegex = "[\\\\/]";
    String notPathRegex = "[^\\\\/]";
    return ".*" + pathRegex + "?(((ehcache)|(L1)|(TIMs))" + pathRegex + notPathRegex + "+\\.jar)" + pathRegex + "?";
  }
}
