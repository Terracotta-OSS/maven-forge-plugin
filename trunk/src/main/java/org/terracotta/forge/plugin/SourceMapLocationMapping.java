/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package org.terracotta.forge.plugin;

public class SourceMapLocationMapping {
  String prefix;
  String replacement;

  public SourceMapLocationMapping() {
  }

  public SourceMapLocationMapping(String prefix, String replacement) {
    this.prefix = prefix;
    this.replacement = replacement;
  }
}
