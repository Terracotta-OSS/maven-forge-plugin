package org.terracotta.forge.plugin;

import com.softwareag.ibit.tools.util.Finder;
import java.util.List;
import java.util.Iterator;

/**
 * Well, not really a test but this is the "common" use case for the finder tool
 * I thought it might be interesting to keep it somehwhere
 */
public class FinderTest {
  public static void main(String[] args) {
 
    Finder testFinder = new Finder();

    String searchDir = "/Users/anthony/.m2/repository/org/glassfish/hk2/external/javax.inject/2.2.0/javax.inject-2.2.0.jar";
    System.out.println("searchDir=" + searchDir);
 
    testFinder.setSearchRootDirectory(searchDir);
 
    try {
        List<String> finderResultList = testFinder.doSearch();
 
        for (Iterator<String> it = finderResultList.iterator(); it.hasNext();) {
            System.out.println(it.next());
        }
    }
    catch( java.io.FileNotFoundException fnfe)
    {
        System.out.println("fnfe=" + fnfe.getMessage());
    }
    catch( java.io.IOException ioe)
    {
        System.out.println("ioe=" + ioe.getMessage());
    }
  }
}