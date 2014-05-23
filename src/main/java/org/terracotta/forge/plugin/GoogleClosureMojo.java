/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.SourceMap.LocationMapping;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Apply Google Closure to javascripts
 * 
 * @author hhuynh
 */
@Mojo(name = "closure-compile", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = false)
public class GoogleClosureMojo extends AbstractMojo {

  @Component
  protected MavenProject                 project;

  /**
   * either filesets or filelists has to be set. filelists has precedence over filesets if both are found
   */
  @Parameter(property = "filesets")
  private List<FileSet>                  filesets;

  /**
   * either filesets or filelists has to be set. filelists has precedence over filesets if both are found
   */
  @Parameter(property = "filelists")
  private List<FileList>                 filelists;

  @Parameter(property = "outputFile", required = true)
  private String                         outputFile;

  @Parameter(property = "charset", defaultValue = "${project.build.sourceEncoding}")
  private String                         charset;

  @Parameter(property = "compilationLevel", defaultValue = "SIMPLE_OPTIMIZATIONS")
  private String                         compilationLevel;

  @Parameter(property = "sourceMapFile", defaultValue = "")
  private String                         sourceMapFile;

  @Parameter(property = "sourceMapFormat", defaultValue = "V3")
  private String                         sourceMapFormat;

  @Parameter(property = "sourceMapUrl")
  private String                         sourceMapUrl;

  @Parameter(property = "externs")
  private ArrayList<String>              externs;

  @Parameter(property = "locationMapping")
  private List<SourceMapLocationMapping> locationMappings;

  @Parameter(property = "prettyPrint", defaultValue = "false")
  private boolean                        prettyPrint;

  /**
   * 
   */
  public void execute() throws MojoExecutionException {
    File outFile = new File(outputFile);
    outFile.getParentFile().mkdirs();
    List<SourceFile> inputs = getSourceFiles();

    CompilerOptions options = new CompilerOptions();

    CompilationLevel.valueOf(compilationLevel).setDebugOptionsForCompilationLevel(options);
    options.setOutputCharset(charset);
    options.setPrettyPrint(prettyPrint);

    boolean sourceMapEnabled = sourceMapFile.length() > 0;
    if (sourceMapEnabled) {
      options.setSourceMapOutputPath(sourceMapFile);
      options.setSourceMapFormat(SourceMap.Format.valueOf(sourceMapFormat));
      List<LocationMapping> locMap = new ArrayList<LocationMapping>();
      for (SourceMapLocationMapping sm : locationMappings) {
        locMap.add(new LocationMapping(sm.prefix.replace('/', File.separatorChar), sm.replacement == null ? ""
            : sm.replacement));
      }
      options.setSourceMapLocationMappings(locMap);
    }

    List<SourceFile> closesureExterns = new ArrayList<SourceFile>();
    for (String extern : externs) {
      closesureExterns.add(SourceFile.fromFile(extern, Charset.forName(charset)));
    }

    Compiler compiler = new Compiler();
    compiler.compile(closesureExterns, inputs, options);

    if (compiler.hasErrors()) { throw new MojoExecutionException(compiler.getErrors()[0].description); }

    PrintWriter outputFileWriter = null;
    try {
      outputFileWriter = new PrintWriter(outFile, charset);
      outputFileWriter.append(compiler.toSource());

      if (sourceMapEnabled) {
        File sourceMapFilePath = new File(sourceMapFile);
        sourceMapFilePath.getParentFile().mkdirs();
        PrintWriter sourceMapWriter = new PrintWriter(sourceMapFilePath, charset);
        compiler.getSourceMap().appendTo(sourceMapWriter, outFile.getName());
        sourceMapWriter.close();
        outputFileWriter.println();
        outputFileWriter.println("//# sourceMappingURL=" + sourceMapUrl);
      }
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    } finally {
      IOUtils.closeQuietly(outputFileWriter);
    }
  }

  private List<SourceFile> getSourceFiles() {
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    if (filelists != null) {
      for (FileList filelist : filelists) {
        for (String file : filelist.files) {
          inputs.add(SourceFile.fromFile(new File(filelist.directory, file)));
        }
      }
    }

    if (filesets != null) {
      FileSetManager fsManager = new FileSetManager();
      for (FileSet fs : filesets) {
        for (String file : fsManager.getIncludedFiles(fs)) {
          inputs.add(SourceFile.fromFile(new File(fs.getDirectory(), file)));
        }
      }
    }

    return inputs;
  }
}
