/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin.util;

import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author hhuynh
 */
public class Util {

  /**
   * Run a shell command and return the output as String
   */
  public static String exec(String command, List<String> params, File workDir) {
    File outputFile;
    try {
      outputFile = File.createTempFile("exec", ".out");
      outputFile.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Project dummyProject = new Project();
    dummyProject.init();

    ExecTask execTask = new ExecTask();
    execTask.setProject(dummyProject);
    execTask.setOutput(outputFile);
    execTask.setDir(workDir != null ? workDir : new File(System.getProperty("user.dir")));
    execTask.setExecutable(command);
    if (params != null) {
      for (String param : params) {
        execTask.createArg().setValue(param);
      }
    }

    FileReader reader = null;
    try {
      execTask.execute();
      reader = new FileReader(outputFile);
      return IOUtils.toString(reader);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      IOUtils.closeQuietly(reader);
      outputFile.delete();
    }
  }

  public static String getSvnInfo(String svnRepo) {
    String svnCommand = "svn";
    String svnHome = System.getenv("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }

    return exec(svnCommand, Arrays.asList("info", svnRepo), null);
  }

  public static String getZipEntries(File file) throws IOException {
    StringBuilder buff = new StringBuilder();
    ZipFile zipFile = new ZipFile(file);
    try {
      Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
      while (zipEntries.hasMoreElements()) {
        buff.append((zipEntries.nextElement().getName())).append("\n");
      }
      return buff.toString();
    } finally {
      zipFile.close();
    }
  }
}
