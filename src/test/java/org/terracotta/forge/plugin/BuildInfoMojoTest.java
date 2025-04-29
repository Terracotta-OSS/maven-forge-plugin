package org.terracotta.forge.plugin;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.launcher.CommandLauncher;
import org.apache.commons.exec.launcher.CommandLauncherFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
 */

public class BuildInfoMojoTest extends TestBase {
  
  public static String FAKE_GIT_REPO_DIR = "fakegitrepo";
  public static String FAKE_GIT_REPO_DIR2 = "fakegitrepo2";
  public static String FAKE_GIT_REPO_SUBDIR = "fakegitrepo/subdir_repo";
  public static String FAKE_GIT_REPO_WORKTREE = "fakegitworktree";
  public static File FAKE_GIT_REPO_TMP = new File(SystemUtils.getJavaIoTmpDir(), FAKE_GIT_REPO_DIR);

  @Before
  @After
  public void cleanUp() throws Exception {
    FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_DIR));
    FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_DIR2));
    FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_SUBDIR));
    FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_WORKTREE));
    FileUtils.deleteDirectory(FAKE_GIT_REPO_TMP);
  }

  private void runShell(String command, File dir) throws Exception {
    CommandLauncher launcher = CommandLauncherFactory.createVMLauncher();
    Process process = launcher.exec(CommandLine.parse(command), null, dir);
    List<String> stdout = IOUtils.readLines(process.getInputStream(), StandardCharsets.UTF_8);
    List<String> stderr = IOUtils.readLines(process.getErrorStream(), StandardCharsets.UTF_8);
    process.waitFor(30, TimeUnit.SECONDS);
    assertEquals("Failed to execute "
            + command
            + ".Output: "
            + StringUtils.join(stdout, " ")
            + " STDERR: "
            + StringUtils.join(stderr, " ")
            , 0
            , process.exitValue());
  }
  
  
  
  private BuildInfoMojo fakeMojo() throws Exception {
    BuildInfoMojo mojo = new BuildInfoMojo();
    MavenProject dummyProject = new MavenProject();
    dummyProject.setFile(getResource("test-pom.xml"));
    mojo.project = dummyProject;
    return mojo;
  }

  private void fakeGitRepo(String dir) throws Exception {
    File mainDir = getDir(dir);

    mainDir.mkdirs();
    runShell("git init " + mainDir.getCanonicalPath(), mainDir);
    runShell("git remote add origin https://an.example/repo.git", mainDir);
    runShell("git remote add another https://wrong.example/repo.git", mainDir);

    File file = getDir(dir + "/afile.txt");
    FileUtils.write(file, new Random().nextLong() + "junk", StandardCharsets.UTF_8);
    File childFile = new File(mainDir, "subdir1/subdir2/anotherfile.txt");
    FileUtils.forceMkdirParent(childFile);
    FileUtils.write(childFile, new Random().nextLong() + "junk", StandardCharsets.UTF_8);

    // make 2 commits
    runShell("git add afile.txt", mainDir);
    runShell("git commit -m test1", mainDir);
    runShell("git add subdir1", mainDir);
    runShell("git commit -m test2", mainDir);
    // now we should have a revision
  }

  private void fakeGitWorktree(String dir, String worktreeDir) throws Exception {
    File mainDir = getDir(dir);
    File worktree = getDir(worktreeDir);

    mainDir.mkdirs();
    runShell("git init " + mainDir.getCanonicalPath(), mainDir);
    runShell("git remote add origin https://an.example/repo.git", mainDir);
    runShell("git remote add another https://wrong.example/repo.git", mainDir);
    FileUtils.touch(new File(mainDir, "test.txt"));
    runShell("git add test.txt", mainDir);
    runShell("git commit -m test", mainDir);
    runShell("git checkout -b branch2", mainDir);
    runShell("git checkout main", mainDir);
    runShell("git worktree add " + worktree.getCanonicalPath() + " branch2", mainDir);
  }

  @Test
  public void checkCorrectBuildInfo_from_git() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", getDir(FAKE_GIT_REPO_DIR).getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://an.example/repo.git", properties.getProperty("build.scm.url"));
    assertEquals("main", properties.getProperty("build.branch"));
  }

  @Test
  public void checkCorrectBuildInfo_from_git_ee() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    fakeGitRepo(FAKE_GIT_REPO_SUBDIR); // another git repo in a subdir
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", getDir(FAKE_GIT_REPO_DIR).getCanonicalPath());
    setMojoConfig(bm, "eeRootPath", getDir(FAKE_GIT_REPO_SUBDIR).getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://an.example/repo.git", properties.getProperty("build.scm.url"));
    assertEquals("main", properties.getProperty("build.branch"));
  }

  /**
   * Checks that the plugin can find a repo from a subdirectory (ie a maven submodule project)
   * @throws Exception
   */
  @Test
  public void checkCorrectBuildInfo_from_git_subdirectory() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", getDir(FAKE_GIT_REPO_DIR).getCanonicalPath() + "/subdir1/subdir2");
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://an.example/repo.git", properties.getProperty("build.scm.url"));
    assertEquals("main", properties.getProperty("build.branch"));
  }

  @Test
  public void checkCorrectBuildInfo_from_git_nonexistent_directory() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", "/tmp");
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("unknown", properties.getProperty("build.scm.url"));
    assertEquals("unknown", properties.getProperty("build.branch"));
  }

  @Test
  public void checkCorrectBuildInfo_from_git_no_branch() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    runShell("git remote remove origin", getDir(FAKE_GIT_REPO_DIR));
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", "/tmp");
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("unknown", properties.getProperty("build.scm.url"));
    assertEquals("unknown", properties.getProperty("build.branch"));
  }


  /**
   * Although we don't make a submodule, in real life a submodule is almost always
   * in detached head state, so it's hard to guess the branch.
   * We use FAKE_GIT_REPO_DIR as the remote.
   */
  @Test
  public void checkCorrectBuildInfo_from_git_detached_head() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    File cloneDir = getDir(FAKE_GIT_REPO_DIR2);
    runShell("git clone " + getDir(FAKE_GIT_REPO_DIR) + " " + cloneDir.getCanonicalPath(), getDir(FAKE_GIT_REPO_DIR).getParentFile());
    // go back a commit so that we're in detached head
    runShell("git checkout HEAD~1", cloneDir);

    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", cloneDir.getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals(getDir(FAKE_GIT_REPO_DIR).toString(), properties.getProperty("build.scm.url"));
    assertEquals("main", properties.getProperty("build.branch"));
  }


  @Test
  public void checkCorrectBuildInfo_from_git_worktree() throws Exception {
    fakeGitWorktree(FAKE_GIT_REPO_DIR, FAKE_GIT_REPO_WORKTREE);
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", getDir(FAKE_GIT_REPO_WORKTREE).getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://an.example/repo.git", properties.getProperty("build.scm.url"));
    assertEquals("branch2", properties.getProperty("build.branch"));
  }

  /**
   * Point the mojo at a tmpdir because otherwise it'll find
   * maven-forge-plugin git repo
   */
  @Test
  public void checkNoBuildInfo_no_git_repo() throws Exception {
    FAKE_GIT_REPO_TMP.mkdirs();
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", FAKE_GIT_REPO_TMP.getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("unknown", properties.getProperty("build.scm.url"));
    assertEquals("unknown", properties.getProperty("build.branch"));
  }
  @Test
  public void checkMatchingBranchTest_if_ee_branch_version_unknown_should_return() throws MojoExecutionException {
    BuildInfoMojo bm = new BuildInfoMojo();
    bm.checkMatchingBranch("4.1", Util.UNKNOWN);
  }

  @Test(expected = MojoExecutionException.class)
  public void checkMatchingBranchTest_if_ee_branch_version_different_should_throw() throws MojoExecutionException {
    BuildInfoMojo bm = new BuildInfoMojo();
    bm.checkMatchingBranch("4.1", "4.2");
  }

  @Test
  public void checkMatchingBranchTest_if_ee_branch_version_the_same_should_return() throws MojoExecutionException {
    BuildInfoMojo bm = new BuildInfoMojo();
    bm.checkMatchingBranch("4.1", "4.1");
  }

}
