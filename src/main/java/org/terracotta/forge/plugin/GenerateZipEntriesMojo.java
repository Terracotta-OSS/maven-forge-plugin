/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.terracotta.forge.plugin.util.Util;

/**
 * Given a zip file, this goals will create a content.txt that lists zip entries
 * 
 * @author hhuynh
 * 
 * @goal generateZipEntries
 */
public class GenerateZipEntriesMojo extends AbstractMojo {
  
  /**
   * project instance. Injected automtically by Maven
   * 
   * @parameter property="project"
   * @required
   * @readonly
   */
  protected MavenProject      project;  

  /**
   * @parameter property="zipFile"
   * @required
   */
  private File   zipFile;

  /**
   * @parameter property="entriesFilename" default-value="toolkit-content.txt"
   * @optional
   */
  private String entriesFilename;

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (!zipFile.exists()) throw new MojoExecutionException("File not found: " + zipFile);
    
    // generate file that holds toolkit entries
    File entriesFile = new File(project.getBuild().getDirectory(), entriesFilename);
    PrintWriter pw = null;
    try {
      pw = new PrintWriter(entriesFile);
      pw.println(Util.getZipEntries(zipFile));
      pw.close();
    } catch (IOException e) {
      throw new MojoExecutionException("IO error", e);
    } finally {
      if (pw != null) {
        pw.close();
      }
    }
    
    // add that file to existing toolkit jar
    Project dummyProject = new Project();
    dummyProject.init();
    Zip zip = new Zip();
    zip.setProject(dummyProject);
    zip.setUpdate(true);
    zip.setBasedir(new File(project.getBuild().getDirectory()));
    zip.setIncludes("**/" + entriesFilename);
    zip.setDestFile(zipFile);
    getLog().info("Adding " + entriesFile + " to archive " + zipFile);
    zip.execute();
  }

}
