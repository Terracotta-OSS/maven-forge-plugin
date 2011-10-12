package org.terracotta.forge.plugin;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.ContextEnabled;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This hack is meant to be used instead of the standard surefire plugin. It does our special toolkit resolving and then
 * uses the real surefire plugin to run the test as normal
 * 
 * @requiresDependencyResolution test
 * @goal test
 * @threadSafe
 */

public class TerracottaSurefireMojo implements Mojo, ContextEnabled {

  /**
   * Set this to "true" to skip running tests, but still compile them. Its use is NOT RECOMMENDED, but quite
   * convenient on occasion.
   *
   * @parameter default-value="false" expression="${skipTests}"
   * @since 2.4
   */
  @SuppressWarnings("unused")
  private boolean skipTests;

  /**
   * This old parameter is just like <code>skipTests</code>, but bound to the old property "maven.test.skip.exec".
   *
   * @parameter expression="${maven.test.skip.exec}"
   * @since 2.3
   * @deprecated Use skipTests instead.
   */
  @SuppressWarnings("unused")
  private boolean skipExec;

  /**
   * Set this to "true" to bypass unit tests entirely. Its use is NOT RECOMMENDED, especially if you enable it using
   * the "maven.test.skip" property, because maven.test.skip disables both running the tests and compiling the tests.
   * Consider using the <code>skipTests</code> parameter instead.
   *
   * @parameter default-value="false" expression="${maven.test.skip}"
   */
  @SuppressWarnings("unused")
  private boolean skip;

  /**
   * Set this to "true" to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
   * occasion.
   *
   * @parameter default-value="false" expression="${maven.test.failure.ignore}"
   */
  @SuppressWarnings("unused")
  private boolean testFailureIgnore;

  /**
   * The base directory of the project being tested. This can be obtained in your unit test via
   * System.getProperty("basedir").
   *
   * @parameter default-value="${basedir}"
   */
  @SuppressWarnings("unused")
  private File basedir;

  /**
   * The directory containing generated test classes of the project being tested. This will be included at the
   * beginning of the test classpath. *
   *
   * @parameter default-value="${project.build.testOutputDirectory}"
   */
  @SuppressWarnings("unused")
  private File testClassesDirectory;

  /**
   * The directory containing generated classes of the project being tested. This will be included after the test
   * classes in the test classpath.
   *
   * @parameter default-value="${project.build.outputDirectory}"
   */
  @SuppressWarnings("unused")
  private File classesDirectory;

  /**
   * The Maven Project Object.
   *
   * @parameter default-value="${project}"
   * @readonly
   */
  private MavenProject project;

  /**
   * List of dependencies to exclude from the test classpath. Each dependency string must follow the format
   * <i>groupId:artifactId</i>. For example: <i>org.acme:project-a</i>
   *
   * @parameter
   * @since 2.6
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private List classpathDependencyExcludes;

  /**
   * A dependency scope to exclude from the test classpath. The scope can be one of the following scopes:
   * <p/>
   * <ul>
   * <li><i>compile</i> - system, provided, compile
   * <li><i>runtime</i> - compile, runtime
   * <li><i>test</i> - system, provided, compile, runtime, test
   * </ul>
   *
   * @parameter default-value=""
   * @since 2.6
   */
  @SuppressWarnings("unused")
  private String classpathDependencyScopeExclude;

  /**
   * Additional elements to be appended to the classpath.
   *
   * @parameter
   * @since 2.4
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private List additionalClasspathElements;

  /**
   * Base directory where all reports are written to.
   *
   * @parameter default-value="${project.build.directory}/surefire-reports"
   */
  @SuppressWarnings("unused")
  private File reportsDirectory;

  /**
   * The test source directory containing test class sources.
   *
   * @parameter default-value="${project.build.testSourceDirectory}"
   * @required
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private File testSourceDirectory;

  /**
   * Specify this parameter to run individual tests by file name, overriding the <code>includes/excludes</code>
   * parameters. Each pattern you specify here will be used to create an include pattern formatted like
   * <code>**&#47;${test}.java</code>, so you can just type "-Dtest=MyTest" to run a single test called
   * "foo/MyTest.java".<br/>
   * This parameter overrides the <code>includes/excludes</code> parameters, and the TestNG <code>suiteXmlFiles</code>
   * parameter.
   * <p/>
   * since 2.7.3 You can execute a limited number of method in the test with adding #myMethod or #my*ethod. Si type
   * "-Dtest=MyTest#myMethod" <b>supported for junit 4.x and testNg</b>
   *
   * @parameter expression="${test}"
   */
  @SuppressWarnings("unused")
  private String test;

  /**
   * A list of &lt;include> elements specifying the tests (by pattern) that should be included in testing. When not
   * specified and when the <code>test</code> parameter is not specified, the default includes will be <code><br/>
   * &lt;includes><br/>
   * &nbsp;&lt;include>**&#47;Test*.java&lt;/include><br/>
   * &nbsp;&lt;include>**&#47;*Test.java&lt;/include><br/>
   * &nbsp;&lt;include>**&#47;*TestCase.java&lt;/include><br/>
   * &lt;/includes><br/>
   * </code> This parameter is ignored if the TestNG <code>suiteXmlFiles</code> parameter is specified.
   *
   * @parameter
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private List includes;

  /**
   * A list of &lt;exclude> elements specifying the tests (by pattern) that should be excluded in testing. When not
   * specified and when the <code>test</code> parameter is not specified, the default excludes will be <code><br/>
   * &lt;excludes><br/>
   * &nbsp;&lt;exclude>**&#47;*$*&lt;/exclude><br/>
   * &lt;/excludes><br/>
   * </code> (which excludes all inner classes).<br>
   * This parameter is ignored if the TestNG <code>suiteXmlFiles</code> parameter is specified.
   *
   * @parameter
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private List excludes;

  /**
   * ArtifactRepository of the localRepository. To obtain the directory of localRepository in unit tests use
   * System.getProperty("localRepository").
   *
   * @parameter expression="${localRepository}"
   * @required
   * @readonly
   */
  private ArtifactRepository localRepository;

  /**
   * List of System properties to pass to the JUnit tests.
   *
   * @parameter
   * @deprecated Use systemPropertyVariables instead.
   */
  @SuppressWarnings("unused")
  private Properties systemProperties;

  /**
   * List of System properties to pass to the JUnit tests.
   *
   * @parameter
   * @since 2.5
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private Map systemPropertyVariables;

  /**
   * List of System properties, loaded from a file, to pass to the JUnit tests.
   *
   * @parameter
   * @since 2.8.2
   */
  @SuppressWarnings("unused")
  private File systemPropertiesFile;

  /**
   * List of properties for configuring all TestNG related configurations. This is the new preferred method of
   * configuring TestNG.
   *
   * @parameter
   * @since 2.4
   */
  @SuppressWarnings("unused")
  private Properties properties;

  /**
   * Map of plugin artifacts.
   *
   * @parameter expression="${plugin.artifactMap}"
   * @required
   * @readonly
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private Map pluginArtifactMap;

  /**
   * Map of project artifacts.
   *
   * @parameter expression="${project.artifactMap}"
   * @required
   * @readonly
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private Map projectArtifactMap;

  /**
   * Option to print summary of test suites or just print the test cases that have errors.
   *
   * @parameter expression="${surefire.printSummary}" default-value="true"
   */
  @SuppressWarnings("unused")
  private boolean printSummary;

  /**
   * Selects the formatting for the test report to be generated. Can be set as "brief" or "plain".
   *
   * @parameter expression="${surefire.reportFormat}" default-value="brief"
   */
  @SuppressWarnings("unused")
  private String reportFormat;

  /**
   * Add custom text into report filename: TEST-testClassName-reportNameSuffix.xml,
   * testClassName-reportNameSuffix.txt and testClassName-reportNameSuffix-output.txt.
   * File TEST-testClassName-reportNameSuffix.xml has changed attributes 'testsuite'--'name'
   * and 'testcase'--'classname' - reportNameSuffix is added to the attribute value.
   *
   * @parameter expression="${surefire.reportNameSuffix}" default-value=""
   */
  @SuppressWarnings("unused")
  private String reportNameSuffix;

  /**
   * Option to generate a file test report or just output the test report to the console.
   *
   * @parameter expression="${surefire.useFile}" default-value="true"
   */
  @SuppressWarnings("unused")
  private boolean useFile;

  /**
   * Set this to "true" to redirect the unit test standard output to a file (found in
   * reportsDirectory/testName-output.txt).
   *
   * @parameter expression="${maven.test.redirectTestOutputToFile}" default-value="false"
   * @since 2.3
   */
  @SuppressWarnings("unused")
  private boolean redirectTestOutputToFile;

  /**
   * Set this to "true" to cause a failure if there are no tests to run. Defaults to "false".
   *
   * @parameter expression="${failIfNoTests}"
   * @since 2.4
   */
  @SuppressWarnings("unused")
  private Boolean failIfNoTests;

  /**
   * Option to specify the forking mode. Can be "never", "once" or "always". "none" and "pertest" are also accepted
   * for backwards compatibility. "always" forks for each test-class.
   *
   * @parameter expression="${forkMode}" default-value="once"
   * @since 2.1
   */
  @SuppressWarnings("unused")
  private String forkMode;

  /**
   * Option to specify the jvm (or path to the java executable) to use with the forking options. For the default, the
   * jvm will be a new instance of the same VM as the one used to run Maven. JVM settings are not inherited from
   * MAVEN_OPTS.
   *
   * @parameter expression="${jvm}"
   * @since 2.1
   */
  @SuppressWarnings("unused")
  private String jvm;

  /**
   * Arbitrary JVM options to set on the command line.
   *
   * @parameter expression="${argLine}"
   * @since 2.1
   */
  @SuppressWarnings("unused")
  private String argLine;

  /**
   * Attach a debugger to the forked JVM. If set to "true", the process will suspend and wait for a debugger to attach
   * on port 5005. If set to some other string, that string will be appended to the argLine, allowing you to configure
   * arbitrary debuggability options (without overwriting the other options specified through the <code>argLine</code>
   * parameter).
   *
   * @parameter expression="${maven.surefire.debug}"
   * @since 2.4
   */
  @SuppressWarnings("unused")
  private String debugForkedProcess;

  /**
   * Kill the forked test process after a certain number of seconds. If set to 0, wait forever for the process, never
   * timing out.
   *
   * @parameter expression="${surefire.timeout}"
   * @since 2.4
   */
  @SuppressWarnings("unused")
  private int forkedProcessTimeoutInSeconds;

  /**
   * Additional environment variables to set on the command line.
   *
   * @parameter
   * @since 2.1.3
   */
  @SuppressWarnings({ "unused", "rawtypes" })
  private Map environmentVariables = new HashMap();

  /**
   * Command line working directory.
   *
   * @parameter expression="${basedir}"
   * @since 2.1.3
   */
  @SuppressWarnings("unused")
  private File workingDirectory;

  /**
   * When false it makes tests run using the standard classloader delegation instead of the default Maven isolated
   * classloader. Only used when forking (forkMode is not "none").<br/>
   * Setting it to false helps with some problems caused by conflicts between xml parsers in the classpath and the
   * Java 5 provider parser.
   *
   * @parameter expression="${childDelegation}" default-value="false"
   * @since 2.1
   */
  @SuppressWarnings("unused")
  private boolean childDelegation;

  /**
   * (TestNG only) Groups for this test. Only classes/methods/etc decorated with one of the groups specified here will
   * be included in test run, if specified.<br/>
   * This parameter is ignored if the <code>suiteXmlFiles</code> parameter is specified.
   *
   * @parameter expression="${groups}"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private String groups;

  /**
   * (TestNG only) Excluded groups. Any methods/classes/etc with one of the groups specified in this list will
   * specifically not be run.<br/>
   * This parameter is ignored if the <code>suiteXmlFiles</code> parameter is specified.
   *
   * @parameter expression="${excludedGroups}"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private String excludedGroups;

  /**
   * (TestNG only) List of &lt;suiteXmlFile> elements specifying TestNG suite xml file locations. Note that
   * <code>suiteXmlFiles</code> is incompatible with several other parameters of this plugin, like
   * <code>includes/excludes</code>.<br/>
   * This parameter is ignored if the <code>test</code> parameter is specified (allowing you to run a single test
   * instead of an entire suite).
   *
   * @parameter
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private File[] suiteXmlFiles;

  /**
   * Allows you to specify the name of the JUnit artifact. If not set, <code>junit:junit</code> will be used.
   *
   * @parameter expression="${junitArtifactName}" default-value="junit:junit"
   * @since 2.3.1
   */
  @SuppressWarnings("unused")
  private String junitArtifactName;

  /**
   * Allows you to specify the name of the TestNG artifact. If not set, <code>org.testng:testng</code> will be used.
   *
   * @parameter expression="${testNGArtifactName}" default-value="org.testng:testng"
   * @since 2.3.1
   */
  @SuppressWarnings("unused")
  private String testNGArtifactName;

  /**
   * (TestNG/JUnit 4.7 provider only) The attribute thread-count allows you to specify how many threads should be
   * allocated for this execution. Only makes sense to use in conjunction with the <code>parallel</code> parameter.
   *
   * @parameter expression="${threadCount}"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private int threadCount;

  /**
   * (JUnit 4.7 provider) Indicates that threadCount is per cpu core.
   *
   * @parameter expression="${perCoreThreadCount}" default-value="true"
   * @since 2.5
   */
  @SuppressWarnings("unused")
  private boolean perCoreThreadCount;

  /**
   * (JUnit 4.7 provider) Indicates that the thread pool will be unlimited. The <code>parallel</code> parameter and
   * the actual number of classes/methods will decide. Setting this to "true" effectively disables
   * <code>perCoreThreadCount</code> and <code>threadCount</code>. Defaults to "false".
   *
   * @parameter expression="${useUnlimitedThreads}" default-value="false"
   * @since 2.5
   */
  @SuppressWarnings("unused")
  private boolean useUnlimitedThreads;

  /**
   * (TestNG only) When you use the <code>parallel</code> attribute, TestNG will try to run all your test methods in
   * separate threads, except for methods that depend on each other, which will be run in the same thread in order to
   * respect their order of execution.
   * <p/>
   * (JUnit 4.7 provider) Supports values "classes"/"methods"/"both" to run in separate threads, as controlled by
   * <code>threadCount</code>.
   *
   * @parameter expression="${parallel}"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private String parallel;

  /**
   * Whether to trim the stack trace in the reports to just the lines within the test, or show the full trace.
   *
   * @parameter expression="${trimStackTrace}" default-value="true"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private boolean trimStackTrace;

  /**
   * Resolves the artifacts needed.
   *
   * @component
   */
  private ArtifactResolver artifactResolver;

  /**
   * Creates the artifact.
   *
   * @component
   */
  private ArtifactFactory artifactFactory;

  /**
   * The remote plugin repositories declared in the POM.
   *
   * @parameter expression="${project.pluginArtifactRepositories}"
   * @since 2.2
   */
  @SuppressWarnings("rawtypes")
  private List remoteRepositories;

  /**
   * For retrieval of artifact's metadata.
   *
   * @component
   */
  private ArtifactMetadataSource metadataSource;

  @SuppressWarnings("unused")
  private Properties originalSystemProperties;

  /**
   * systemPropertyVariables + systemProperties
   */
  @SuppressWarnings("unused")
  private Properties internalSystemProperties = new Properties();

  /**
   * Flag to disable the generation of report files in xml format.
   *
   * @parameter expression="${disableXmlReport}" default-value="false"
   * @since 2.2
   */
  @SuppressWarnings("unused")
  private boolean disableXmlReport;

  /**
   * Option to pass dependencies to the system's classloader instead of using an isolated class loader when forking.
   * Prevents problems with JDKs which implement the service provider lookup mechanism by using the system's
   * classloader.
   *
   * @parameter expression="${surefire.useSystemClassLoader}" default-value="true"
   * @since 2.3
   */
  @SuppressWarnings("unused")
  private boolean useSystemClassLoader;

  /**
   * By default, Surefire forks your tests using a manifest-only JAR; set this parameter to "false" to force it to
   * launch your tests with a plain old Java classpath. (See
   * http://maven.apache.org/plugins/maven-surefire-plugin/examples/class-loading.html for a more detailed explanation
   * of manifest-only JARs and their benefits.)
   * <p/>
   * Beware, setting this to "false" may cause your tests to fail on Windows if your classpath is too long.
   *
   * @parameter expression="${surefire.useManifestOnlyJar}" default-value="true"
   * @since 2.4.3
   */
  @SuppressWarnings("unused")
  private boolean useManifestOnlyJar;

  /**
   * By default, Surefire enables JVM assertions for the execution of your test cases. To disable the assertions, set
   * this flag to "false".
   *
   * @parameter expression="${enableAssertions}" default-value="true"
   * @since 2.3.1
   */
  @SuppressWarnings("unused")
  private boolean enableAssertions;

  /**
   * The current build session instance.
   *
   * @parameter expression="${session}"
   * @required
   * @readonly
   */
  @SuppressWarnings("unused")
  private MavenSession session;

  /**
   * (TestNG only) Define the factory class used to create all test instances.
   *
   * @parameter expression="${objectFactory}"
   * @since 2.5
   */
  @SuppressWarnings("unused")
  private String objectFactory;

  /**
   * @parameter default-value="${session.parallel}"
   * @readonly
   * @noinspection UnusedDeclaration
   */
  @SuppressWarnings("unused")
  private Boolean parallelMavenExecution;

  /**
   * Defines the order the tests will be run in. Supported values are "alphabetical", "reversealphabetical", "random",
   * "hourly" (alphabetical on even hours, reverse alphabetical on odd hours) and "filesystem".
   * <p/>
   * <p/>
   * Odd/Even for hourly is determined at the time the of scanning the classpath, meaning it could change during a
   * multi-module build.
   *
   * @parameter default-value="filesystem"
   * @since 2.7
   */
  @SuppressWarnings("unused")
  private String runOrder;

  /**
   * @component
   */
  @SuppressWarnings("unused")
  private ToolchainManager toolchainManager;

  /**
   * For collecting artifacts that match
   * 
   * @parameter expression= "${component.org.apache.maven.artifact.resolver.ArtifactCollector}"
   * @readonly
   * @required
   */
  private ArtifactCollector                    __TC_artifactCollector;

  /**
   * The Terracotta core version against which to resolve the toolkit.
   * 
   * @parameter expression="${terracotta.core.version}"
   */
  private String                               __TC_terracottaCoreVersion;

  private final TerracottaSurefirePlugin __TC_delegate            = new TerracottaSurefirePlugin();

  /**
   * Set this to 'true' to not require a qualifier match (ie. SNAPSHOT) between tc.core and toolkit version
   * 
   * @parameter default-value="false"
   */
  private boolean                              __TC_skipQualifierMatch;

  /**
   * Set this to 'true' to skip any magic toolkit resolving. Default is 'true'
   * 
   * @parameter default-value="true"
   */
  private boolean                              __TC_skipToolkitResolve;

  /**
   * Should Junit reports be cleaned
   * 
   * @parameter expression="${maven.test.clean.reports}" default-value="true"
   */
  private boolean                              __TC_cleanJunitReports;

  /**
   * File that contains list of tests to run
   * 
   * @parameter expression="${listFile}"
   */
  private File                                 __TC_listFile;

  /**
   * @parameter expression="${poundTimes}" default-value="1"
   */
  private int                                  __TC_poundTimes;

  /**
   * @parameter expression="${devLog}" default-value="false"
   */
  private boolean                              __TC_devLog;

  public void execute() throws MojoExecutionException, MojoFailureException {
    __TC_delegate.setup(__TC_terracottaCoreVersion, project, artifactFactory, artifactResolver, remoteRepositories,
                        localRepository, __TC_artifactCollector, metadataSource, __TC_skipQualifierMatch,
                        __TC_skipToolkitResolve, __TC_cleanJunitReports, __TC_listFile, __TC_poundTimes, __TC_devLog);
    initDelegateFields();
    __TC_delegate.execute();
  }

  private void initDelegateFields() throws MojoExecutionException {
    Class delegateClass = SurefirePlugin.class;

    try {
      // make sure we have all the fields as the real surefire plugin
      for (Field f : delegateClass.getDeclaredFields()) {
        getClass().getDeclaredField(f.getName());
      }
    } catch (Exception e) {
      throw new MojoExecutionException("Missing field in " + getClass() + "?", e);
    }

    try {
      for (Field f : getClass().getDeclaredFields()) {
        if (f.getName().startsWith("__TC_")) continue;

        if (!Modifier.isStatic(f.getModifiers())) {
          Field superField = delegateClass.getDeclaredField(f.getName());
          superField.setAccessible(true);
          superField.set(__TC_delegate, f.get(this));
        }
      }
    } catch (Exception e) {
      throw new MojoExecutionException("failed to set super class fields", e);
    }
  }

  public void setPluginContext(Map pluginContext) {
    __TC_delegate.setPluginContext(pluginContext);
  }

  public Map getPluginContext() {
    return __TC_delegate.getPluginContext();
  }

  public void setLog(Log log) {
    __TC_delegate.setLog(log);
  }

  public Log getLog() {
    return __TC_delegate.getLog();
  }
}
