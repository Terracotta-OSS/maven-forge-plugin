/*
 * Copyright Terracotta, Inc.
 * Copyright IBM Corp. 2024, 2025
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
