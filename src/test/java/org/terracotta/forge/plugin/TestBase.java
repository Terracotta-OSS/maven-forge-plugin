package org.terracotta.forge.plugin;

import org.apache.maven.plugin.AbstractMojo;

import java.io.File;
import java.lang.reflect.Field;

public abstract class TestBase {

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

}
