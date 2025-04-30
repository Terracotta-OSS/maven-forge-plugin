package org.terracotta.forge.plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.jar.Manifest;
import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by akom on 6/22/16.
 */
public class ManifestMojoTest extends TestBase {
    
    private File outputManifest = new File(getDir("."), "TEST_MANIFEST.MF");

    @Before
    @After
    public void cleanUp() throws Exception {
        FileUtils.deleteDirectory(getDir(FAKE_GIT_REPO_DIR));
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
    public void checkBuildInfoURL() throws Exception {
        fakeGitRepo(FAKE_GIT_REPO_DIR);
        ManifestMojo mojo = fakeMojo();
        setMojoConfig(mojo, "rootPath", getDir(FAKE_GIT_REPO_DIR).getCanonicalPath());
        mojo.execute();

        String expectedBuildInfoURL = "https://an.example/repo.git";
        assertEquals(expectedBuildInfoURL, getFromManifest("BuildInfo-URL"));
    }

    private String getFromManifest(String key) throws Exception {
        Manifest manifest = new Manifest();
        manifest.read(FileUtils.openInputStream(outputManifest));

        return manifest.getMainAttributes().getValue(key);
    }
}
