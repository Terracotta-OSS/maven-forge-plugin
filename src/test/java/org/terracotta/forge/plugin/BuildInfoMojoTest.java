package org.terracotta.forge.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

public class BuildInfoMojoTest {

  @Test
  public void checkMatchingBranchTest_if_ee_branch_version_unknown_should_return() throws MojoExecutionException {
    BuildInfoMojo.checkMatchingBranch("4.1", BuildInfoMojo.UNKNOWN);
  }

  @Test(expected = MojoExecutionException.class)
  public void checkMatchingBranchTest_if_ee_branch_version_different_should_throw() throws MojoExecutionException {
    BuildInfoMojo.checkMatchingBranch("4.1", "4.2");
  }

  @Test
  public void checkMatchingBranchTest_if_ee_branch_version_the_same_should_return() throws MojoExecutionException {
    BuildInfoMojo.checkMatchingBranch("4.1", "4.1");
  }

}
