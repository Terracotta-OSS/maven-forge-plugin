package org.terracotta.forge.plugin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.launcher.CommandLauncher;
import org.apache.commons.exec.launcher.CommandLauncherFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;

import java.io.File;
import java.lang.reflect.Field;

import static junit.framework.TestCase.assertEquals;

public abstract class TestBase {
    public static String FAKE_GIT_REPO_DIR = "fakegitrepo";

    protected File getResource(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource(fileName).getFile());
    }

    protected File getDir(String subdir) {
        return new File(getResource("test-pom.xml").getParent(), subdir);
    }

    protected void setMojoConfig(AbstractMojo mojo, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field f = mojo.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(mojo, value );
    }

    protected void fakeGitRepo(String dir) throws Exception {
        File mainDir = getDir(dir);

        mainDir.mkdirs();
        runShell("git init -b main " + mainDir.getCanonicalPath(), mainDir);
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

    protected void runShell(String command, File dir) throws Exception {
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
}
