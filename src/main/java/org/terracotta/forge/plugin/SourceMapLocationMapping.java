/*
 * Copyright Terracotta, Inc.
 * Copyright Super iPaaS Integration LLC, an IBM Company 2024
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
