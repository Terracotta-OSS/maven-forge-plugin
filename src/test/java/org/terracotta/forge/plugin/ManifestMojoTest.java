package org.terracotta.forge.plugin;

import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * Created by akom on 6/22/16.
 */
public class ManifestMojoTest {
    
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
    public void checkSVNInfoLoading_withSVN_healthy_output() throws Exception {
        ManifestMojo mojo = fakeMojo();
        System.setProperty("SVN_HOME", getDir("fakesvn-good").getAbsolutePath());
        mojo.execute();
        checkManifest("https://svn.terracotta.org/repo/forge/some/fake/path");
    }

    @Test
    public void checkSVNInfoLoading_withSVN_blank_output() throws Exception {
        ManifestMojo mojo = fakeMojo();
        System.setProperty("SVN_HOME", getDir("fakesvn-blank").getAbsolutePath());
        mojo.execute();
        checkManifest("unknown");
    }

    @Test
    public void checkSVNInfoLoading_withoutSVN() throws Exception {
        ManifestMojo mojo = fakeMojo();
        System.setProperty("SVN_HOME", "/totally/bogus");
        mojo.execute();
        checkManifest("unknown");
    }


    private void checkManifest(String expectedURL) throws Exception {
        Properties p = new Properties();
        p.load(new FileReader(outputManifest));

        Assert.assertTrue(p.containsKey("BuildInfo-URL"));
        Assert.assertTrue("Expected URL value", p.getProperty("BuildInfo-URL").equals(expectedURL));
        

    }

    private File getDir(String subdir) {
        return new File(getResource("test-pom.xml").getParent(), subdir);
    }

    private File getResource(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(fileName).getFile());
    }
}
