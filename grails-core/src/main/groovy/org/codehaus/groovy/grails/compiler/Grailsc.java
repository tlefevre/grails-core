/*
 * Copyright 2003-2007 Graeme Rocher.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.compiler;

import groovy.lang.GroovyResourceLoader;
import org.codehaus.groovy.ant.Groovyc;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.grails.compiler.injection.GrailsAwareInjectionOperation;
import org.codehaus.groovy.grails.compiler.support.GrailsResourceLoader;
import org.codehaus.groovy.grails.compiler.support.GrailsResourceLoaderHolder;
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Grailsc extends Groovyc {

    private List<File> destList = new ArrayList<File>();


    @Override protected CompilationUnit makeCompileUnit() {
        CompilationUnit unit = super.makeCompileUnit();
        GrailsAwareInjectionOperation operation = new GrailsAwareInjectionOperation();
        unit.addPhaseOperation(operation, Phases.CANONICALIZATION);
        return unit;
    }


    @Override
    protected void scanDir(File srcDir, File destDir, String[] files) {
       List<File> srcList = new ArrayList<File>();
       String srcPath = srcDir.getAbsolutePath();
       String destPath = destDir.getAbsolutePath();
       for (String f : files) {
           File sf = new File(srcPath, f);
           File df = null;
           if (f.endsWith(".groovy") ) {
               df = new File(destPath, f.substring(0, f.length()-7) + ".class");
               int i = f.lastIndexOf('/');
               if (!df.exists() && i > -1) {
                   // check root package
                   File tmp = new File(destPath, f.substring(i, f.length()-7) + ".class");
                   if (tmp.exists()) {
                       df = tmp;
                   }
               }
           }
           else if (f.endsWith(".java")) {
               df = new File(destPath, f.substring(0, f.length()-5) + ".class");
           }
           else {
               continue;
           }

           if (sf.lastModified() > df.lastModified()) {
               srcList.add(sf);
               destList.add(df);
           }
       }
       addToCompileList(srcList.toArray(new File[srcList.size()]));
    }

    @Override
    protected void compile() {


        if (compileList.length > 0) {
            long now = System.currentTimeMillis();
            try {
                try {
                    super.compile();
                } catch (RuntimeException e) {
                    if(!(e instanceof MultipleCompilationErrorsException) && !(e instanceof CompilationFailedException)) {
                        e.printStackTrace();
                        System.out.println("Groovy Compiler error: " + e.getMessage());
                        throw e;
                    }

                }
                getDestdir().setLastModified(now);
            }
            finally {
                // set the destination files as modified so recompile doesn't happen continuously
                for (File f : destList) {
                    f.setLastModified(now);
                }
            }
        }
    }

    private GroovyResourceLoader configureResourceLoader() {
        Resource[] resources = GrailsPluginUtils.getPluginBuildSettings().getArtefactResources();
        GrailsResourceLoader resourceLoader = new GrailsResourceLoader(resources);
        GrailsResourceLoaderHolder.setResourceLoader(resourceLoader);
        return resourceLoader;
    }
}
