/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin.util;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.logging.Log;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;

import com.softwareag.ibit.tools.util.Finder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author hhuynh
 */
public class Util {
  public static final String DEFAULT_FINDER_EXCLUSIONS = "jackson-xc";

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
      throw new RuntimeException(e);
    } finally {
      IOUtils.closeQuietly(reader);
      outputFile.delete();
    }
  }

  public static Properties getSvnInfo(String svnRepo, Log log) throws IOException {
    String svnCommand = "svn";
    String svnHome = System.getenv("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }
    String result = exec(svnCommand, Arrays.asList("info", svnRepo), null);
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

  public static boolean isFlaggedByFinder(String file, String exclusionList, Log log) throws IOException {
    Finder finder = new Finder();
    finder.setPackageOnlySearch(true);
    finder.setSearchRootDirectory(file);
    finder.setUniqueEnabled(true);
    if (!isEmpty(exclusionList)) {
      log.info("Scanning with exclusionList: " + exclusionList);
      finder.setExcludesListFilename(exclusionList);
    }
    List<String> resultList = finder.doSearch();
    if (resultList.size() > 0) {
      for (String result : resultList) {
        log.error("Flagged: " + result);
      }
      return true;
    } else {
      return false;
    }
  }

  public static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
  }
}
