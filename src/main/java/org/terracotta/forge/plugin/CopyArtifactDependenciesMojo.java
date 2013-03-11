/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.aether.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * print out dependencies of a given artifact
 * 
 * @author hhuynh
 * @goal copy-dependencies
 * @requiresDependencyResolution compile
 */
public class CopyArtifactDependenciesMojo extends AbstractResolveDependenciesMojo {
  /**
   * output dir
   * 
   * @required
   * @parameter expression="${outputDir}"
   */
  private File                    outputDir;

  /**
   * copy dependencies
   */
  public void execute() throws MojoExecutionException {
    try {
      Collection<Artifact> deps = resolve();
      copyDeps(deps);
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void copyDeps(Collection<Artifact> deps) throws IOException {
    for (Artifact a : deps) {
      String filename = a.getFile().getName();
      if (a.isSnapshot()) {
        filename = a.getArtifactId() + "-" + a.getBaseVersion() + "." + a.getExtension();
      }
      File destFile = new File(outputDir, filename);
      FileUtils.copyFile(a.getFile(), destFile);
    }
  }
}