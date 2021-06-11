package org.terracotta.forge.plugin;

import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Test;
import org.terracotta.forge.plugin.util.SCMInfo;
import org.terracotta.forge.plugin.util.Util;

import java.io.File;
import java.lang.reflect.Field;
import java.util.jar.Manifest;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by akom on 6/22/16.
 */
public class ManifestMojoTest extends TestBase {
    
    private File outputManifest = new File(getDir("."), "TEST_MANIFEST.MF");

    @After
    public void cleanUp() {
        System.clearProperty("SVN_HOME");
    }
    
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
        assertTrue(getFromManifestBuildInfoUrl().contains(".git")); // failed over to git, picked up our own project

        //also run the method directly:
        SCMInfo svnInfo = Util.getSvnInfo("anything", mojo.getLog());
        assertEquals(null, svnInfo);
    }

    @Test
    public void checkSVNInfoLoading_withoutSVN() throws Exception {
        ManifestMojo mojo = fakeMojo();
        System.setProperty("SVN_HOME", "/totally/bogus");
        mojo.execute();
        assertTrue(getFromManifestBuildInfoUrl().contains(".git")); // failed over to git, picked up our own project

        //also run the method directly:
        SCMInfo svnInfo = Util.getSvnInfo("anything", mojo.getLog());
        assertEquals(null, svnInfo);

    }


    private void checkManifest(String expectedURL) throws Exception {
        assertEquals(expectedURL, getFromManifestBuildInfoUrl());
    }

    private String getFromManifestBuildInfoUrl() throws Exception {
        return getFromManifest("BuildInfo-URL");
    }
    private String getFromManifest(String key) throws Exception {
        Manifest manifest = new Manifest();
        manifest.read(FileUtils.openInputStream(outputManifest));

        return manifest.getMainAttributes().getValue("BuildInfo-URL");
    }

}
