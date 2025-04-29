package org.terracotta.forge.plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.jar.Manifest;
import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by akom on 6/22/16.
 */
public class ManifestMojoTest extends TestBase {
    
    private File outputManifest = new File(getDir("."), "TEST_MANIFEST.MF");

    private ManifestMojo fakeMojo() throws Exception {
        outputManifest.delete();

        ManifestMojo mojo = new ManifestMojo();
        MavenProject dummyProject = new MavenProject();
        dummyProject.setFile(getResource("test-pom.xml"));
        mojo.project = dummyProject;

        Field f = ManifestMojo.class.getDeclaredField("manifestFile");
        f.setAccessible(true);
        f.set(mojo, outputManifest );

        return mojo;
    }

    @Test
    public void checkBuildInfoURL() throws Exception {
        ManifestMojo mojo = fakeMojo();
        mojo.execute();
        String expectedBuildInfoURL = "git@github.com:Terracotta-OSS/maven-forge-plugin.git";
        assertEquals(expectedBuildInfoURL, getFromManifest("BuildInfo-URL"));
    }

    private String getFromManifest(String key) throws Exception {
        Manifest manifest = new Manifest();
        manifest.read(FileUtils.openInputStream(outputManifest));

        return manifest.getMainAttributes().getValue(key);
    }
}
