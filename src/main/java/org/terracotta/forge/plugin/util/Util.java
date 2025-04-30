/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin.util;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.surefire.AbstractSurefireMojo;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author hhuynh
 */
public class Util {

  public static final String UNKNOWN = "unknown";

  /**
   * When disambiguating GIT branches returned by git rev-parse --contains,
   */
  public static final String GIT_BRANCH_MATCH_REGEX = "main|master|release/.*";

  /**
   * When disambiguating GIT branches returned by git rev-parse --contains,
   * we want to find the lowest numbered branch, with main being highest.
   * So we want this order:
   *    release/4.1
   *    release/4.3
   *    main or master
   */
  public static final Comparator<String> GIT_BRANCH_NAME_COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      if ("main".equals(o1) || "master".equals(o1)) {
        return 1;
      }
      if ("main".equals(o2) || "master".equals(o2)) {
        return -1;
      }
      return o1.compareTo(o2);
    }
  };

  /**
   *
   * @param command The string will be split by space
   * @param workdir
   * @param log
   * @return lines of stdout as an array or empty list on any failure
   */
  public static List<String> exec(String command, File workdir, Log log) {
    List<String> output = Collections.emptyList();
    ProcessBuilder builder = new ProcessBuilder(command.split(" "));
    builder.directory(workdir);
    try {
      Process process = builder.start();
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream()));

      output = IOUtils.readLines(process.getInputStream(), StandardCharsets.UTF_8);
      List<String> errors = IOUtils.readLines(process.getErrorStream(), StandardCharsets.UTF_8);

      process.waitFor(60, TimeUnit.SECONDS);
      if (process.exitValue() != 0) {
        log.debug("Exit code " + process.exitValue() + " executing command " + command);
        log.debug("OUTPUT: " + StringUtils.join(output, "\n") + StringUtils.join(errors, "\n"));
        output = Collections.emptyList();
      }
    } catch (IOException | InterruptedException e) {
      log.info("Unable to execute command " + command, e);
    }

    return output;
  }

  public static SCMInfo getScmInfo(String repo, Log log) throws IOException {
    SCMInfo scmInfo = getGitInfo(repo, log);
    if (scmInfo == null) {
      scmInfo = new SCMInfo(); //not null, for convenince
    }
    return scmInfo;
  }

  public static SCMInfo getGitInfo(String gitRepo, Log log) {
    SCMInfo result = new SCMInfo();
    File gitDir = new File(gitRepo);

    try {
      //find remote url
      List<String> exec = Util.exec("git remote -v", gitDir, log);
      Map<String, String> remoteMap = exec
          .stream()
          .map(elem -> elem.split("[ \t]"))
          .filter(e -> e.length > 1)
          .collect(Collectors.toMap(e -> e[0], e -> e[1], (match1, match2) -> match1));

      String remoteName = remoteMap.keySet().stream().filter(name ->
              Stream.of("upstream", "origin").anyMatch(it -> it.equals(name))
      ).findFirst().orElse(null);
      if (remoteName != null) {
        result.url = remoteMap.get(remoteName);
      } else {
        result.url = remoteMap.values().stream().findFirst().orElse(null);
      }
      if (result.url == null) {
        log.debug("Failed to find a standard remote name at " + gitRepo);
        return null;
      }

      result.revision = Util.exec("git rev-parse HEAD", gitDir, log).stream().findFirst().orElse(null);

      // find branch
      result.branch = Util.exec("git rev-parse --abbrev-ref HEAD", gitDir, log).stream().findFirst().orElse(null);
      if ("HEAD".equals(result.branch)) {
        log.debug("Determining branch from git branch --contains");
        // this is a detached head situation.  Let's try to guess the branch from "--contains"
        result.branch = Util.exec("git branch --remote --contains", gitDir, log).stream()
                .filter(Objects::nonNull)
                .map(branch -> branch.substring(branch.indexOf('/') + 1)) //strip remote name
                .filter(branch -> branch.matches(GIT_BRANCH_MATCH_REGEX))
                .sorted(GIT_BRANCH_NAME_COMPARATOR)
                .findFirst().orElse(null);
      }
      if (result.branch == null) {
        result.branch = System.getenv("GIT_BRANCH");
      }

    } catch (IllegalArgumentException ix) {
      // means there is no git repo
      return null;
    } catch (Exception e) {
      log.info("Failed to read git info from " + gitRepo, e);
      // partial read?  Let's not return partial data
      return null;
    }

    return result;
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

  /**
   * Adds configurable toolchains support, allowing to specify toolchains to
   * use for just this plugin execution.
   *
   * if a <toolchains></toolchains> block is provided in configuration,
   * find the first matching toolchain and set AbstractSurefireMojo.jvm (private) so that
   * surefire/failsafe use it during execution.
   * This also sets JAVA_HOME in test environment to the same JVM
   *
   * @throws MojoExecutionException
   */
  public static void overrideToolchainConfiguration(Map<String, String> toolchainSpec, ToolchainManager manager, MavenSession session, Log logger, AbstractSurefireMojo pluginInstance) throws MojoExecutionException {

    if ( toolchainSpec != null && toolchainSpec.size() > 0 && manager != null ) {
      String javaExecutableFromToolchain;
      String javaHomeFromToolchain;
      List<Toolchain> toolchains = manager.getToolchains(session, "jdk", toolchainSpec);
      if (toolchains.size() > 0) {
        Toolchain selectedToolchain = toolchains.get(0);
        javaExecutableFromToolchain = selectedToolchain.findTool("java");
        javaHomeFromToolchain = ((DefaultJavaToolChain)selectedToolchain).getJavaHome();
        if (!new File(javaExecutableFromToolchain).canExecute()) {
          throw new MojoExecutionException("Identified matching toolchain " + javaExecutableFromToolchain
                  + " but it is not an executable file");
        }
        logger.info("Setting surefire's jvm to " + javaExecutableFromToolchain
                + " from toolchain " + selectedToolchain + ", requirements: " + toolchainSpec);
      } else {
        throw new MojoExecutionException("Unable to find a matching toolchain for configuration " + toolchainSpec);
      }

      //unfortunately, current AbstractSurefireMojo.getEffectiveJvm()
      // accesses the jvm field directly, not through getJvm(),
      // so we have to hack this:
      try {
        Field jvmField = AbstractSurefireMojo.class.getDeclaredField("jvm");
        jvmField.setAccessible(true);
        jvmField.set(pluginInstance, javaExecutableFromToolchain);

        //we also want to set JAVA_HOME for the test jvm so subprocesses that do odd things
        //like spawn more jvms will do it right
        Map<String, String> environmentVariables = pluginInstance.getEnvironmentVariables();
        environmentVariables.put("JAVA_HOME", javaHomeFromToolchain);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new MojoExecutionException("Unable to set jvm field in superclass to " + javaExecutableFromToolchain, e);
      }
    }
  }

}
