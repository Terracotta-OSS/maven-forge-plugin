/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.forge.plugin;

public class ToolkitAPIVersion {

  private final int minor;
  private final int major;

  public ToolkitAPIVersion(int major, int minor) {
    this.minor = minor;
    this.major = major;
  }

  public ToolkitAPIVersion(String major, String minor) {
    this(Integer.parseInt(major), Integer.parseInt(minor));
  }

  public int getMajor() {
    return this.major;
  }

  public int getMinor() {
    return this.minor;
  }

  @Override
  public String toString() {
    return major + "." + minor;
  }

  @Override
  public int hashCode() {
    return major + minor;
  }

  public ToolkitAPIVersion nextMinorVersion() {
    return new ToolkitAPIVersion(major, minor + 1);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ToolkitAPIVersion) {
      ToolkitAPIVersion other = (ToolkitAPIVersion) obj;
      return this.major == other.major && this.minor == other.minor;
    }
    return false;
  }

}
