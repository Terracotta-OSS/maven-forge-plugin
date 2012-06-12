/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.forge.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.surefire.SurefireHelper;
import org.apache.maven.plugin.surefire.SurefirePlugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.booter.SurefireBooterForkException;

public class TerracottaSurefirePlugin extends SurefirePlugin {

  static final Map<ToolkitAPIVersion, String>  BASE_CORE_VERSIONS                  = new ConcurrentHashMap<ToolkitAPIVersion, String>();

  static {
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 0), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 1), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 2), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 3), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 4), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 5), "3.3.0");
    BASE_CORE_VERSIONS.put(new ToolkitAPIVersion(1, 6), "3.3.0");
  }

  private static final String                  TOOLKIT_TIM_GROUP_ID                = "org.terracotta.toolkit";
  private static final String                  TOOLKIT_RUNTIME_GROUP_ID            = "org.terracotta";

  private static final Pattern                 TOOLKIT_TIM_ARTIFACT_ID_PATTERN     = Pattern
                                                                                       .compile("^terracotta-toolkit-(\\d+)\\.(\\d+)(\\-ee)?$");
  private static final Pattern                 TOOLKIT_RUNTIME_ARTIFACT_ID_PATTERN = Pattern
                                                                                       .compile("^terracotta-toolkit-(\\d+)\\.(\\d+)-runtime(\\-ee)?$");

  private final Map<ToolkitAPIVersion, String> baseCoreVersions;

  private String                               terracottaCoreVersion;
  private MavenProject                         project;
  private ArtifactFactory                      artifactFactory;
  private ArtifactResolver                     artifactResolver;
  private List                                 remoteRepositories;
  private ArtifactRepository                   localRepository;
  private ArtifactCollector                    artifactCollector;
  private ArtifactMetadataSource               metadataSource;
  private boolean                              skipQualifierMatch;
  private boolean                              skipToolkitResolve;

  private boolean                              cleanJunitReports;
  private File                                 listFile;
  private int                                  poundTimes;
  private boolean                              devLog;

  public TerracottaSurefirePlugin() {
    this(BASE_CORE_VERSIONS);
  }

  public TerracottaSurefirePlugin(
      Map<ToolkitAPIVersion, String> baseCoreVersions) {
    this.baseCoreVersions = baseCoreVersions;
  }

  void setup(String terracottaCoreVersion, MavenProject project,
      ArtifactFactory factory, ArtifactResolver artifactResolver,
      List remoteRepositories, ArtifactRepository localRepository,
      ArtifactCollector artifactCollector,
      ArtifactMetadataSource metadataSource, boolean skipQualifierMatch,
      boolean skipToolkitResolve, boolean cleanJunitReports, File listFile,
      int poundTimes, boolean devLog) {
    this.terracottaCoreVersion = terracottaCoreVersion;
    this.project = project;
    this.artifactFactory = factory;
    this.artifactResolver = artifactResolver;
    this.remoteRepositories = remoteRepositories;
    this.localRepository = localRepository;
    this.artifactCollector = artifactCollector;
    this.metadataSource = metadataSource;
    this.skipQualifierMatch = skipQualifierMatch;
    this.skipToolkitResolve = skipToolkitResolve;
    this.cleanJunitReports = cleanJunitReports;
    this.listFile = listFile;
    this.poundTimes = poundTimes;
    this.devLog = devLog;
  }

  public boolean isCleanJunitReports() {
    return cleanJunitReports;
  }

  public void setCleanJunitReports(boolean cleanJunitReports) {
    this.cleanJunitReports = cleanJunitReports;
  }

  public void setListFile(File listFile) {
    this.listFile = listFile;
  }

  public File getListFile() {
    return listFile;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (devLog) {
      File currentDir = super.getWorkingDirectory();
      if (currentDir == null) {
        currentDir = new File(".");
      }
      File devLog4jFile = new File(currentDir, ".tc.dev.log4j.properties");
      if (!devLog4jFile.exists()) {
        try {
          devLog4jFile.getParentFile().mkdirs();
          if (!devLog4jFile.createNewFile())
            throw new IOException("createNewFile return false");
        } catch (IOException e1) {
          getLog().error(e1);
          throw new MojoExecutionException("Failed to create " + devLog4jFile);
        }
      }
    }

    getLog().debug("terracottaCoreVersion: " + terracottaCoreVersion);
    if (!skipToolkitResolve && terracottaCoreVersion != null
        && terracottaCoreVersion.length() != 0) {
      try {
        updateToolkitDependencies();
      } catch (Exception e) {
        getLog().error(e);
        throw new MojoExecutionException("error updating toolkit references", e);
      }
    }

    try {
      // recheck should_skip_test maven propperty to decide if tests should be
      // skipped
      String shouldSkipTestsValue = project.getProperties().getProperty(
          "should_skip_tests");
      if (shouldSkipTestsValue != null) {
        getLog().warn(
            "'should_skip_tests' property found, value is "
                + shouldSkipTestsValue
                + ". This value overrides the 'skipTests' original setting.");
        boolean shouldSkipTests = Boolean.valueOf(shouldSkipTestsValue);
        this.setSkipTests(shouldSkipTests);
      }

      // handle listFile
      if (listFile != null) {
        if (!listFile.exists()) {
          getLog().warn(
              "listFile '" + listFile
                  + "' specified but does not exist. No tests will be run");
          return;
        }
        getLog().info("Running tests found in file " + listFile);
        FileInputStream input = null;
        try {
          input = new FileInputStream(listFile);
          List<String> line = IOUtils.readLines(input);
          List<String> includeList = new ArrayList<String>();
          for (String test : line) {
            test = test.trim();
            if (test.length() == 0 || test.startsWith("#")) {
              continue;
            }
            if (!test.endsWith(".java")) {
              test += ".java";
            }
            includeList.add("**/" + test);
          }
          getLog().info("Tests to run: " + includeList);
          this.setIncludes(includeList);
        } catch (IOException e) {
          throw new MojoExecutionException(e.getMessage());
        } finally {
          IOUtils.closeQuietly(input);
        }
      }

      if (poundTimes > 1) {
        if (this.getTest() == null) {
          getLog().error("poundTimes was set but -Dtest isn't");
          throw new MojoFailureException("poundTimes was set but -Dtest isn't");
        } else {
          for (int i = 1; i <= poundTimes; i++) {
            getLog().info("* POUNDING ITERATION: " + i);
            try {
              super.execute();
            } catch (MojoExecutionException e) {
              getLog().error("Test failed after iteration #" + i);
              throw e;
            }
          }
        }
        // done pounding, exit
        getLog().info("*** Pounded " + poundTimes + " times! Test passed.");
      } else {
        // invoke surefire plugin normally
        super.execute();
      }

    } catch (MojoExecutionException e) {
      if (e.getCause() instanceof SurefireBooterForkException) {
        // test timeout, don't throw this exception
        // so Jenkins could parse JUnit reports and treat timeout failures as
        // regular failures
        SurefireHelper.reportExecution(this, 1, getLog());
      } else {
        throw e;
      }
    } finally {
      if (cleanJunitReports) {
        getLog().info("Fix Junit reports if needed");
        FixJUnitReportMojo fixUnitReportMojo = new FixJUnitReportMojo();
        fixUnitReportMojo.setPluginContext(getPluginContext());
        fixUnitReportMojo.setProject(project);
        fixUnitReportMojo.execute();
      }
    }
  }

  @Override
  protected boolean hasExecutedBefore() {
    // if we're pounding test, we have to lie so that the test can be run again
    if (poundTimes > 1)
      return false;
    return super.hasExecutedBefore();
  }

  @SuppressWarnings("rawtypes")
  private void updateToolkitDependencies() throws Exception {
    ProcessResult artifactsResults = process(wrap(project.getArtifacts()));
    ProcessResult dependenciesResult = process(wrap(project.getDependencies()));

    List dependencies = new ArrayList();
    for (Wrapper w : dependenciesResult.processedItems) {
      Dependency dep = (Dependency) w.unwrap();

      for (ToolkitAPIVersion ver : baseCoreVersions.keySet()) {
        Exclusion exclusion;

        exclusion = new Exclusion();
        exclusion.setArtifactId("terracotta-toolkit-" + ver.toString());
        exclusion.setGroupId(TOOLKIT_TIM_GROUP_ID);
        dep.addExclusion(exclusion);

        exclusion = new Exclusion();
        exclusion.setArtifactId("terracotta-toolkit-" + ver.toString() + "-ee");
        exclusion.setGroupId(TOOLKIT_TIM_GROUP_ID);
        dep.addExclusion(exclusion);

        exclusion = new Exclusion();
        exclusion.setArtifactId("terracotta-toolkit-" + ver.toString()
            + "-runtime");
        exclusion.setGroupId(TOOLKIT_RUNTIME_GROUP_ID);
        dep.addExclusion(exclusion);

        exclusion = new Exclusion();
        exclusion.setArtifactId("terracotta-toolkit-" + ver.toString()
            + "-runtime-ee");
        exclusion.setGroupId(TOOLKIT_RUNTIME_GROUP_ID);
        dep.addExclusion(exclusion);
      }

      dependencies.add(dep);
    }

    // Add an explicit toolkit reference if transitive dependency found
    if (artifactsResults.tim != null && dependenciesResult.tim == null) {
      dependencies.add(artifactsResults.tim.asDependency());
    }

    if (artifactsResults.runtime != null && dependenciesResult.runtime == null) {
      dependencies.add(artifactsResults.runtime.asDependency());
    }

    project.setDependencies(dependencies);

    if (artifactResolver != null) {
      // recompute the artifact set with cleaned up toolkit references
      Set artifacts = project.createArtifacts(artifactFactory, null, null);

      artifacts = new HashSet(artifactResolver.resolveTransitively(artifacts,
          project.getArtifact(), remoteRepositories, localRepository,
          metadataSource).getArtifacts());

      // Due to a what seems like a maven 2.x bug we need to potentially remove
      // transitive toolkit dependencies that
      // might have crept back in here. The exclusions added above don't seem to
      // working as desired sometimes
      for (Iterator iter = artifacts.iterator(); iter.hasNext();) {
        Artifact a = (Artifact) iter.next();
        if (a.getGroupId().equals(TOOLKIT_RUNTIME_GROUP_ID)
            || a.getGroupId().equals(TOOLKIT_TIM_GROUP_ID)) {
          if (a.getArtifactId().startsWith("terracotta-toolkit-")
              && a.getDependencyTrail().size() > 2) {
            iter.remove();
          }
        }
      }

      project.setArtifacts(artifacts);
    }
  }

  private static Collection<Wrapper> wrap(Collection toWrap) {
    if (toWrap == null)
      return null;

    Collection<Wrapper> rv = toWrap instanceof List ? new ArrayList<Wrapper>()
        : new HashSet<Wrapper>();

    for (Object o : toWrap) {
      if (o instanceof Artifact) {
        rv.add(new Wrapper((Artifact) o));
      } else if (o instanceof Dependency) {
        rv.add(new Wrapper((Dependency) o));
      } else {
        throw new AssertionError(o.getClass().getName());
      }

    }

    return rv;
  }

  private ProcessResult process(Collection<Wrapper> collection)
      throws MojoExecutionException {
    Wrapper TIM = null;
    Wrapper Runtime = null;
    EnumMap<ArtifactType, ToolkitAPIVersion> maxVersions = new EnumMap<ArtifactType, ToolkitAPIVersion>(
        ArtifactType.class);

    List<Wrapper> remove = new ArrayList<Wrapper>();

    for (Wrapper artifact : collection) {

      final ToolkitAPIVersion ver = getToolkitAPIVersion(artifact);

      if (ver != null) {
        adjustMaxVersion(artifact, ver, maxVersions);

        if (isToolkitTIM(artifact)) {
          if (TIM != null) {
            if (ver.getMinor() > getToolkitAPIVersion(TIM).getMinor()) {
              remove.add(TIM);
              TIM = artifact;
            } else {
              remove.add(artifact);
            }
          } else {
            TIM = artifact;
          }
        } else if (isToolkitRuntime(artifact)) {
          if (Runtime != null) {
            if (ver.getMinor() > getToolkitAPIVersion(Runtime).getMinor()) {
              remove.add(Runtime);
              Runtime = artifact;
            } else {
              remove.add(artifact);
            }
          } else {
            Runtime = artifact;
          }
        } else {
          throw new AssertionError(artifact.toString());
        }
      }
    }

    for (Wrapper artifact : remove) {
      getLog().info("Removing " + artifact + " from dependency set");
      collection.remove(artifact);
    }

    for (Entry<ArtifactType, ToolkitAPIVersion> entry : maxVersions.entrySet()) {
      getLog().info(
          "Targeting Toolkit API version " + entry.getValue()
              + ", but higher minor version may be used based on availability");

      switch (entry.getKey()) {
      case RUNTIME: {
        setVersionForDependency(Runtime, entry.getValue());
        break;
      }
      case TIM: {
        setVersionForDependency(TIM, entry.getValue());
        break;
      }
      default: {
        throw new AssertionError(entry.getKey());
      }
      }
    }

    return new ProcessResult(collection, TIM, Runtime);
  }

  private final ArtifactType getArtifactType(Wrapper artifact) {
    if (isToolkitTIM(artifact)) {
      return ArtifactType.TIM;
    } else if (isToolkitRuntime(artifact)) {
      return ArtifactType.RUNTIME;
    } else {
      throw new AssertionError(artifact.toString());
    }
  }

  private void adjustMaxVersion(Wrapper artifact, ToolkitAPIVersion ver,
      EnumMap<ArtifactType, ToolkitAPIVersion> maxVersions)
      throws MojoExecutionException {
    ArtifactType key = getArtifactType(artifact);

    ToolkitAPIVersion existingMax = maxVersions.get(key);

    if (existingMax == null) {
      maxVersions.put(key, ver);
    } else {
      if (ver.getMajor() != existingMax.getMajor()) {
        // This isn't likely to happen but it seems good to catch anyway
        throw new MojoExecutionException(
            "Crossing major toolkit API versions: " + ver.getMajor() + " and "
                + existingMax.getMajor() + " for " + artifact);
      }

      if (ver.getMinor() > existingMax.getMinor()) {
        getLog().info(
            "Higher toolkit minor version detected (" + ver + ") vs ("
                + existingMax + ")");
        maxVersions.put(key, ver);
      }
    }
  }

  private void setVersionForDependency(Wrapper artifact,
      final ToolkitAPIVersion startingApiVer) throws MojoExecutionException {
    String base = this.baseCoreVersions.get(startingApiVer);
    if (base == null) {
      // This should only go off when we add a new API version but have not yet
      // updated the base versions
      throw new AssertionError(
          "This plugin has no base core TC version defined for toolkit API version "
              + startingApiVer);
    }

    ToolkitAPIVersion apiVer = startingApiVer;
    String artifactId = artifact.getArtifactId();
    String toolkitRange = toolkitVersionRange(base);
    artifact.setVersion(toolkitRange);

    String coreQualifier = new DefaultArtifactVersion(terracottaCoreVersion)
        .getQualifier();

    if (artifactResolver != null) {
      while (true) {

        try {
          VersionRange toolkitMavenRange = VersionRange
              .createFromVersionSpec(toolkitRange);

          Artifact a = artifactFactory.createDependencyArtifact(
              artifact.getGroupId(), artifactId, toolkitMavenRange,
              artifact.getType(), artifact.getClassifier(),
              artifact.getScope(), false);

          List<ArtifactVersion> allAvailableVersions = metadataSource
              .retrieveAvailableVersions(a, localRepository, remoteRepositories);

          ArtifactVersion selectedVersion = null;
          // pick the latest version in the range *and* with a matching
          // qualifier
          for (ArtifactVersion version : allAvailableVersions) {
            if (toolkitMavenRange.containsVersion(version)) {

              if (skipQualifierMatch()
                  || isQualifierMatch(coreQualifier, version)) {
                if (selectedVersion == null) {
                  selectedVersion = version;
                } else {
                  if (selectedVersion.compareTo(version) < 0) {
                    selectedVersion = version;
                  }
                }
              }
            }
          }

          if (selectedVersion == null) {
            getLog().warn(
                "Cannot resolve " + artifactId + " with range " + toolkitRange
                    + ". Moving to next minor API version");

            String prev = apiVer.getMajor() + "." + apiVer.getMinor();
            apiVer = apiVer.nextMinorVersion();
            String next = apiVer.getMajor() + "." + apiVer.getMinor();
            base = this.baseCoreVersions.get(apiVer);
            if (base == null) {
              // we've exceeded all the minor versions we have data for
              throw new MojoExecutionException(
                  "No suitable toolkit can be resolved");
            }
            artifactId = artifactId.replace(prev, next);
            continue;
          }

          // Found a suitable toolkit, woo-hoo!
          getLog().info(
              "Changing toolkit reference (" + artifact.getArtifactId() + ")");
          artifact.setArtifactId(a.getArtifactId());
          artifact.setVersion(selectedVersion.toString());
          a.setVersion(selectedVersion.toString());

          Set<Artifact> collect = new HashSet<Artifact>();
          collect.add(a);
          artifactCollector.collect(collect, project.getArtifact(),
              localRepository, remoteRepositories, metadataSource, null,
              Collections.EMPTY_LIST);

          artifactResolver.resolve(a, remoteRepositories, localRepository);

          getLog().info(
              "   New reference: " + artifact.getArtifactId() + " "
                  + artifact.getVersion());
          break;
        } catch (Exception e) {
          throw new MojoExecutionException("Error resolving toolkit", e);
        }
      }
    }
  }

  private boolean isQualifierMatch(String coreQualifier, ArtifactVersion version) {
    return ((coreQualifier == null && version.getQualifier() == null) || (coreQualifier != null && coreQualifier
        .equals(version.getQualifier())));
  }

  private boolean skipQualifierMatch() {
    return skipQualifierMatch;
  }

  /**
   * Compute the toolkit version range for the given base core version
   */
  private String toolkitVersionRange(String base) {
    DefaultArtifactVersion baseCoreVersion = new DefaultArtifactVersion(base);
    DefaultArtifactVersion coreVersion = new DefaultArtifactVersion(
        terracottaCoreVersion);

    if (baseCoreVersion.getMajorVersion() != coreVersion.getMajorVersion()) {
      // XXX: We can maybe fix this someday by silly tricks like using the first
      // 100 major versions (ie. 1.0.0 to
      // 100.0.0) before crossing major TC versions
      return null;
    }

    int major = 1 + (coreVersion.getMinorVersion() - baseCoreVersion
        .getMinorVersion());
    int minor = (coreVersion.getIncrementalVersion() - baseCoreVersion
        .getIncrementalVersion());

    String qualifier = "";
    if (coreVersion.getQualifier() != null) {
      qualifier = "-" + coreVersion.getQualifier();
    }

    return "[" + major + "." + minor + ".0" + qualifier + "," + major + "."
        + (minor + 1) + ".0-SNAPSHOT)";
  }

  private static boolean isToolkitTIM(Wrapper artifact) {
    return TOOLKIT_TIM_GROUP_ID.equals(artifact.getGroupId())
        && TOOLKIT_TIM_ARTIFACT_ID_PATTERN.matcher(artifact.getArtifactId())
            .matches();
  }

  private static boolean isToolkitRuntime(Wrapper artifact) {
    return TOOLKIT_RUNTIME_GROUP_ID.equals(artifact.getGroupId())
        && TOOLKIT_RUNTIME_ARTIFACT_ID_PATTERN
            .matcher(artifact.getArtifactId()).matches();
  }

  private ToolkitAPIVersion getToolkitAPIVersion(Wrapper artifact) {
    String groupId = artifact.getGroupId();
    String artifactId = artifact.getArtifactId();

    if (TOOLKIT_TIM_GROUP_ID.equals(groupId)) {
      Matcher m = TOOLKIT_TIM_ARTIFACT_ID_PATTERN.matcher(artifactId);
      if (m.matches()) {
        return new ToolkitAPIVersion(m.group(1), m.group(2));
      }
    } else if (TOOLKIT_RUNTIME_GROUP_ID.equals(groupId)) {
      Matcher m = TOOLKIT_RUNTIME_ARTIFACT_ID_PATTERN.matcher(artifactId);
      if (m.matches()) {
        return new ToolkitAPIVersion(m.group(1), m.group(2));
      }
    }

    return null;
  }

  @Override
  public void setProject(MavenProject project) {
    this.project = project;
  }

  public void setTerracottaCoreVersion(String terracottaCoreVersion) {
    this.terracottaCoreVersion = terracottaCoreVersion;
  }

  private static class ProcessResult {
    private final Collection<Wrapper> processedItems;
    private final Wrapper             runtime;
    private final Wrapper             tim;

    ProcessResult(Collection<Wrapper> processedItems, Wrapper tim,
        Wrapper runtime) {
      this.processedItems = processedItems;
      this.tim = tim;
      this.runtime = runtime;
    }
  }

  private static class Wrapper {

    private final String groupId;
    private String       artifactId;
    private String       version;
    private final String classifier;
    private final String type;
    private final String scope;
    private final Object obj;

    public Wrapper(Artifact a) {
      this(a, a.getGroupId(), a.getArtifactId(), a.getVersion(), a
          .getClassifier(), a.getType(), a.getScope());
    }

    public Dependency asDependency() {
      Dependency d = new Dependency();
      d.setArtifactId(artifactId);
      d.setClassifier(classifier);
      d.setGroupId(groupId);
      d.setScope(scope);
      d.setType(type);
      d.setVersion(version);
      return d;
    }

    public Wrapper(Dependency d) {
      this(d, d.getGroupId(), d.getArtifactId(), d.getVersion(), d
          .getClassifier(), d.getType(), d.getScope());
    }

    private Wrapper(Object obj, String groupId, String artifactId,
        String version, String classifier, String type, String scope) {
      this.obj = obj;
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.classifier = classifier;
      this.type = type;
      this.scope = scope;
    }

    public String getScope() {
      return scope;
    }

    public String getGroupId() {
      return groupId;
    }

    public String getClassifier() {
      return classifier;
    }

    public String getType() {
      return type;
    }

    public String getVersion() {
      return version;
    }

    public String getArtifactId() {
      return artifactId;
    }

    public void setVersion(String version) {
      if (obj instanceof Artifact) {
        ((Artifact) obj).setVersion(version);
      } else if (obj instanceof Dependency) {
        ((Dependency) obj).setVersion(version);
      } else {
        throw new AssertionError(obj.getClass().getName());
      }

      this.version = version;
    }

    public void setArtifactId(String artifactId) {
      if (obj instanceof Artifact) {
        ((Artifact) obj).setArtifactId(artifactId);
      } else if (obj instanceof Dependency) {
        ((Dependency) obj).setArtifactId(artifactId);
      } else {
        throw new AssertionError(obj.getClass().getName());
      }

      this.artifactId = artifactId;
    }

    public Object unwrap() {
      return obj;
    }

    @Override
    public String toString() {
      return obj.getClass().getSimpleName() + "(" + groupId + "." + artifactId
          + " " + version + ")";
    }
  }

  private enum ArtifactType {
    TIM, RUNTIME;
  }

}
