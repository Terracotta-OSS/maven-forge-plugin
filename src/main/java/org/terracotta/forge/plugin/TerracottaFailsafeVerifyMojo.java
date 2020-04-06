package org.terracotta.forge.plugin;

import org.apache.maven.plugin.failsafe.VerifyMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * This class exists to provide a consistent "verify" goal in this plugin, same as in failsafe
 */
@Mojo( name = "verify", defaultPhase = LifecyclePhase.VERIFY, requiresProject = true, threadSafe = true )
public class TerracottaFailsafeVerifyMojo extends VerifyMojo {
}
