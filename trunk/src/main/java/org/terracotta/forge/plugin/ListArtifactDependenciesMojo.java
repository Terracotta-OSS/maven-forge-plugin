/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.Collection;

/**
 * print out dependencies of a given artifact
 * 
 * @author hhuynh
 */
@Mojo(name = "list-dependencies", requiresDependencyResolution = ResolutionScope.COMPILE)
public class ListArtifactDependenciesMojo extends AbstractResolveDependenciesMojo {
  /**
   * output file
   * 
   */
  @Parameter(required = false)
  private File                    outputFile;

  /**
   * append listing to existing outputFile, default is false
   * 
   */
  @Parameter(required = false, defaultValue = "false")
  private boolean                 appendFile;

  /**
   * print the dependencies as file URL, default is true
   * 
   */
  @Parameter(required = false, defaultValue = "true")
  private boolean                 listAsUrl;

  /**
   * comment added as a line beginging of the output
   * 
   */
  @Parameter(required = false, defaultValue = "")
  private String                  comment;

  /**
   * list dependencies
   */
  public void execute() throws MojoExecutionException {
    PrintStream out = null;
    try {
      Collection<Artifact> deps = resolve();

      if (outputFile != null) {
        outputFile.getParentFile().mkdirs();
        out = new PrintStream(new FileOutputStream(outputFile, appendFile));
        getLog().info("Printing dependencies of " + artifacts + " to file " + outputFile);
        printDeps(deps, out);
      } else {
        printDeps(deps, System.out);
      }

    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(out);
    }
  }

  private void printDeps(Collection<Artifact> deps, PrintStream out) throws MalformedURLException {
    if (comment != null && !"".equals(comment)) {
      out.println("#" + comment);
    }
    for (Artifact a : deps) {
      if (listAsUrl) {
        out.println(a.getFile().toURI().toURL().toString());
      } else {
        out.println(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getType() + ":" + a.getBaseVersion()
                    + ":runtime");
      }
    }
  }
}