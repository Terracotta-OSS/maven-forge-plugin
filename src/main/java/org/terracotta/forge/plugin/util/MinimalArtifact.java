/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
 */
package org.terracotta.forge.plugin.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinimalArtifact {
  public static final Pattern MAVEN_COORDS_REGX = Pattern.compile("([^: ]+):([^: ]+)(:([^: ])+)?");

  private String              groupId;
  private String              artifactId;
  private String              version;

  public MinimalArtifact() {
  }

  public MinimalArtifact(String groupId, String artifactId) {
    this.groupId = groupId;
    this.artifactId = artifactId;
  }

  public MinimalArtifact(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public MinimalArtifact(String coords) {
    Matcher m = MAVEN_COORDS_REGX.matcher(coords);
    if (!m.matches()) { throw new RuntimeException("Bad coords, expected pattern groupId:artifactId(:version)?"); }
    this.groupId = m.group(1);
    this.artifactId = m.group(2);
    this.version = m.group(3) == null ? null : m.group(3);
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

}
