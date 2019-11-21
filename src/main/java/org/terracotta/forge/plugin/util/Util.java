/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin.util;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author hhuynh
 */
public class Util {
  /**
   * Run a shell command and return the output as String
   */
  public static String exec(String command, List<String> params, File workDir, Log log) {
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
    execTask.setResultProperty("svninfoexitcode");
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
      // This should not be terminal, for example if svn is not installed or this is not an svn project
      log.warn("Unable to use svn info : " + e);
    } finally {
      IOUtils.closeQuietly(reader);
      outputFile.delete();
    }
    return "";
  }

  public static Properties getSvnInfo(String svnRepo, Log log) throws IOException {
    String svnCommand = "svn";
    String svnHome = System.getenv("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }
    //This is for ease of testing
    svnHome = System.getProperty("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }

    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
      if (new File(svnCommand + ".bat").exists()) {
        svnCommand += ".bat";
      }
    }

    String result = exec(svnCommand, Arrays.asList("info", svnRepo), null, log);
    log.debug("svn info " + svnRepo + ": " + result);
    return parseSvnInfo(result);
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

  public static Properties parseSvnInfo(String svnInfo) throws IOException {
    Properties props = new Properties();
    BufferedReader br = new BufferedReader(new StringReader(svnInfo));
    String line = null;
    while ((line = br.readLine()) != null) {
      String[] tokens = line.split(":", 2);
      if (tokens.length == 2) {
        props.put(tokens[0].trim(), tokens[1].trim());
      }
    }
    return props;
  }

  public static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
  }
}
