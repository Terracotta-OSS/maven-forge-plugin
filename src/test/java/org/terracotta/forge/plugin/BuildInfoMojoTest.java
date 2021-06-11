package org.terracotta.forge.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Test;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;

import static junit.framework.TestCase.assertEquals;

/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

public class BuildInfoMojoTest extends TestBase {
  
  public static String FAKE_GIT_REPO_DIR = "fakegitrepo";
  public static String FAKE_GIT_REPO_SUBDIR = "fakegitrepo/subdir_repo";

  @After
  public void cleanUp() throws Exception {
    FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_DIR));
    System.clearProperty("SVN_HOME");
  }

  private BuildInfoMojo fakeMojo() throws Exception {
    BuildInfoMojo mojo = new BuildInfoMojo();
    MavenProject dummyProject = new MavenProject();
    dummyProject.setFile(getResource("test-pom.xml"));
    mojo.project = dummyProject;
    return mojo;
  }

  private void fakeGitRepo(String dir) throws Exception {

    Git git = Git.init().setDirectory( getDir(dir) ).call();
    git.remoteAdd().setName("origin").setUri(new URIish("https://an.example/repo.git")).call();
    assertEquals(1, git.remoteList().call().size());

    File file = getDir(dir + "/afile.txt");
    FileUtils.write(file, new Random().nextLong() + "junk", StandardCharsets.UTF_8);
    File childFile = new File(getDir(dir), "subdir1/subdir2/anotherfile.txt");
    FileUtils.forceMkdirParent(childFile);

    git.add().addFilepattern("*.txt").addFilepattern("subdir*").call();
    git.commit().setCommitter("someone", "some@email.com").setMessage("something").call();
    // now we should have a revision
  }

  @Test
  public void checkCorrectBuildInfo_from_fake_svn() throws Exception {
    BuildInfoMojo bm = fakeMojo();
    System.setProperty("SVN_HOME", getDir("fakesvn-good").getAbsolutePath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://svn.terracotta.org/repo/forge/some/fake/path", properties.getProperty("build.scm.url"));
  }

  @Test
  public void checkCorrectBuildInfo_from_git() throws Exception {
    fakeGitRepo(FAKE_GIT_REPO_DIR);
    BuildInfoMojo bm = fakeMojo();
    setMojoConfig(bm, "rootPath", getDir(FAKE_GIT_REPO_DIR).getCanonicalPath());
    bm.execute();

    Properties properties = bm.project.getProperties();
    assertEquals("https://an.example/repo.git", properties.getProperty("build.scm.url"));
    assertEquals("master", properties.getProperty("build.branch"));
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
    assertEquals("master", properties.getProperty("build.branch"));
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
    assertEquals("master", properties.getProperty("build.branch"));
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
