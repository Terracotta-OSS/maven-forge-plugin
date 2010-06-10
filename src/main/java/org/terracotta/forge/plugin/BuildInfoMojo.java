/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.terracotta.forge.plugin.util.Util;

/**
 * Mojo to add build info (ie, timestamp, revision, url, etc.) to manifest
 * @author hhuynh
 *
 * @goal buildinfo
 * @phase prepare-package
 */
public class BuildInfoMojo extends AbstractMojo {

  /**
   * @parameter expression="${manifest.file}" default-value="${project.build.directory}/MANIFEST.MF"
   */
  private File manifestFile;
  /**
   * project instance. Injected automtically by Maven
   *
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  protected MavenProject project;

  /**
   * Extra manifest entries
   *
   * @parameter express="${manifestEntries}"
   * 
   */
  private Map<String, String> manifestEntries = new HashMap<String, String>();

  public void execute() throws MojoExecutionException, MojoFailureException {
    Manifest manifest = createOrLoadManifest();
    Attributes attributes = manifest.getMainAttributes();
    addExtraManifestEntries(attributes);
    addBuildAttributes(attributes);
    saveManifestFile(manifest);
  }

  private void saveManifestFile(Manifest manifest) throws MojoExecutionException {
    FileOutputStream out = null;
    try {
      manifestFile.getAbsoluteFile().getParentFile().mkdirs();
      out = new FileOutputStream(manifestFile);
      manifest.write(out);
      out.flush();
    } catch (IOException ex) {
      getLog().error(ex);
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
        getLog().error(ex);
        throw new MojoExecutionException("Failed to read existing manifest", ex);
      } finally {
        IOUtils.closeQuietly(in);
      }
    }
    return manifest;
  }

  private void  addExtraManifestEntries(Attributes attributes) {
    if (manifestEntries == null) return;
    for (Map.Entry<String, String> entry : manifestEntries.entrySet()) {
      attributes.putValue(entry.getKey(), entry.getValue());
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
      if (getLog().isErrorEnabled()) {
        getLog().error("Exception while finding host name", e);
      }
    }
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    attributes.putValue(BUILDINFO + "User", user);
    attributes.putValue(BUILDINFO + "Host", host);
    attributes.putValue(BUILDINFO + "Timestamp", timestamp);

    try {
      String svnInfo = Util.getSvnInfo(project.getBasedir().getAbsolutePath());
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
