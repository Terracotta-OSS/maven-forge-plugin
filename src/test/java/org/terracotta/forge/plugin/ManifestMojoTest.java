/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.junit.Test;

/**
 *
 * @author hhuynh
 */
public class ManifestMojoTest extends AbstractMojoTestCase {

  @Test
  public void testBuildInfo() throws Exception {
    File manifestFile = new File("target/test/MANIFEST");
    Map<String, String> manifestEntries = new HashMap<String, String>();
    manifestEntries.put("foo", "bar");
    
    ManifestMojo ManifestMojo = new ManifestMojo();
    MavenProjectStub project = new MavenProjectStub();
    setVariableValueToObject(ManifestMojo, "project", project);
    setVariableValueToObject(ManifestMojo, "manifestFile", manifestFile);
    setVariableValueToObject(ManifestMojo, "manifestEntries", manifestEntries);
    ManifestMojo.execute();

    InputStream in = null;
    try {
      in = new FileInputStream(manifestFile);
      Manifest manifest = new Manifest(in);
      assertManifest(manifest, "BuildInfo-User", System.getProperty("user.name"));
      assertManifest(manifest, "foo", "bar");
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  private void assertManifest(Manifest manifest, String attr, String expectedValue) {
    assertEquals(expectedValue, manifest.getMainAttributes().getValue(attr));
  }
}
