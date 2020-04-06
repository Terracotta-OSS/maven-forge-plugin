package org.terracotta.forge.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.failsafe.IntegrationTestMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.terracotta.forge.plugin.util.Util;

import java.util.Map;

/**
 * Mimics the failsafe original but allows the JVM to be configured
 * from toolchains
 */
@Mojo(name = "integration-test", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.INTEGRATION_TEST, threadSafe = true)
public class TerracottaFailsafeIntegrationTestMojo extends IntegrationTestMojo {

    /**
     * A full toolchain specification block, eg:
     * &lt;jdk&gt;
     * &lt;version&gt;X&lt;/version&gt;
     * &lt;vendor&gt;Y&lt;/vendor&gt;
     * ...
     * &lt;/jdk&gt;
     * All items are optional, if unspecified defaults to normal surefire behavior.
     * If specified, changes the jdk used by surefire to a matching toolchain in maven's toolchains.xml
     * If the requirements are not satisfied, fails the build.
     */
    @Parameter(alias = "jdk")
    private Map<String, String> toolchainSpec;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Util.overrideToolchainConfiguration(toolchainSpec, getToolchainManager(), getSession(), getLog(), this);
        super.execute();
    }
}
