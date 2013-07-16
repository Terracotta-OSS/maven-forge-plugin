/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

/**
 * Mojo to create manifest with build info (ie, timestamp, revision, url, etc.)
 * 
 * @author hhuynh
 * @goal manifest
 * @requiresDependencyResolution compile
 */
public class ManifestMojo extends AbstractMojo {

  /**
   * @parameter property="manifest.file" default-value="${project.build.directory}/MANIFEST.MF"
   */
  private File                      manifestFile;

  /**
   * project instance. Injected automatically by Maven
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject            project;

  /**
   * Extra manifest entries
   * 
   * @parameter property="manifestEntries"
   */
  private final Map<String, String> manifestEntries = new HashMap<String, String>();

  /**
   * @parameter property="rootPath"
   */
  private String                    rootPath;

  /**
   * @parameter property="addClasspath" default-value="false"
   */
  private boolean                   addClasspath;

  /**
   * @parameter property="excludeGroupIds" default-value=""
   */
  private String                    excludeGroupIds;

  public void execute() throws MojoExecutionException {
    Manifest manifest = createOrLoadManifest();
    Attributes attributes = manifest.getMainAttributes();
    addClasspath(attributes);
    addExtraManifestEntries(attributes);
    addBuildAttributes(attributes);
    saveManifestFile(manifest);
  }

  private void addClasspath(Attributes attributes) {
    if (!addClasspath) return;
    @SuppressWarnings("unchecked")
    Set<Artifact> artifacts = project.getArtifacts();

    Set<String> excludedGroupIds = getExcludedGroupIds();
    StringBuilder mavenStyleClassPath = new StringBuilder();
    StringBuilder classpath = new StringBuilder();
    for (Artifact a : artifacts) {

      if (excludedGroupIds.contains(a.getGroupId())) {
        getLog().info("Excluding " + a + " from manifest's classpath");
        continue;
      }

      if (a.getArtifactHandler().isAddedToClasspath()) {
        if (Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())) {
          File file = a.getFile();
          if (!(file.isDirectory() && file.getName().endsWith("classes"))) {
            classpath.append(file.getName()).append(" ");

            mavenStyleClassPath.append(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getBaseVersion() + ":"
                                           + a.getType()).append(" ");
          }
        }
      }
    }
    if (classpath.length() > 0) {
      classpath.deleteCharAt(classpath.length() - 1);
      mavenStyleClassPath.deleteCharAt(mavenStyleClassPath.length() - 1);
    }
    attributes.putValue("Class-Path", classpath.toString());
    attributes.putValue("Maven-Class-Path", mavenStyleClassPath.toString());
  }

  private Set<String> getExcludedGroupIds() {
    if (excludeGroupIds == null || "".equals(excludeGroupIds)) { return Collections.EMPTY_SET; }
    String[] groupIds = excludeGroupIds.split(",");
    Set<String> rv = new HashSet<String>();
    for (String groupId : groupIds) {
      rv.add(groupId.trim());
    }
    return rv;
  }

  private void saveManifestFile(Manifest manifest) throws MojoExecutionException {
    FileOutputStream out = null;
    try {
      manifestFile.getAbsoluteFile().getParentFile().mkdirs();
      out = new FileOutputStream(manifestFile);
      manifest.write(out);
      out.flush();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to write out manifest file", ex);
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  protected Manifest createOrLoadManifest() throws MojoExecutionException {
    Manifest manifest = new Manifest();
    if (manifestFile != null && manifestFile.exists()) {
      InputStream in = null;
      try {
        in = new FileInputStream(manifestFile);
        Manifest existingManifest = new Manifest(in);
        manifest = new Manifest(existingManifest);
      } catch (IOException ex) {
        throw new MojoExecutionException("Failed to read existing manifest", ex);
      } finally {
        IOUtils.closeQuietly(in);
      }
    }
    return manifest;
  }

  private void addExtraManifestEntries(Attributes attributes) {
    if (manifestEntries == null) { return; }

    for (Map.Entry<String, String> entry : manifestEntries.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      if ("Class-Path".equals(key) && attributes.containsKey(new Name("Class-Path"))) {
        String extendedClasspath = attributes.getValue("Class-Path") + " " + value;
        attributes.putValue(key, extendedClasspath);
      } else {
        attributes.putValue(key, value);
      }
    }
  }

  private void addBuildAttributes(Attributes attributes) throws MojoExecutionException {
    final String UNKNOWN = "unknown";
    final String BUILDINFO = "BuildInfo-";
    final String urlKey = "URL: ";
    final String revisionKey = "Last Changed Rev: ";
    String host = UNKNOWN;
    String svnUrl = UNKNOWN;
    String revision = UNKNOWN;

    String user = System.getProperty("user.name", UNKNOWN);
    String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      getLog().warn("Exception while finding host name", e);
    }

    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.putValue(BUILDINFO + "User", user);
    attributes.putValue(BUILDINFO + "Host", host);
    attributes.putValue(BUILDINFO + "Timestamp", timestamp);

    if (rootPath == null) {
      rootPath = project.getBasedir().getAbsolutePath();
    }

    try {
      getLog().debug("root path " + rootPath);
      String svnInfo = Util.getSvnInfo(new File(rootPath).getCanonicalPath());
      getLog().debug("svn info " + svnInfo);
      BufferedReader br = new BufferedReader(new StringReader(svnInfo));
      String line = null;
      while ((line = br.readLine()) != null) {
        if (line.startsWith(urlKey)) {
          svnUrl = line.substring(urlKey.length());
        }
        if (line.startsWith(revisionKey)) {
          revision = line.substring(revisionKey.length());
        }
      }
    } catch (IOException ioe) {
      throw new MojoExecutionException("Exception reading svn info", ioe);
    }

    attributes.putValue(BUILDINFO + "URL", svnUrl);
    attributes.putValue(BUILDINFO + "Revision", revision);
  }
}
