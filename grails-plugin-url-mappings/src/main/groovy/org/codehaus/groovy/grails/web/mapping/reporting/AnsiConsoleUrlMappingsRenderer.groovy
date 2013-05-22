/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.mapping.reporting

import grails.build.logging.GrailsConsole
import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.web.mapping.RegexUrlMapping
import org.codehaus.groovy.grails.web.mapping.ResponseCodeMappingData
import org.codehaus.groovy.grails.web.mapping.ResponseCodeUrlMapping
import org.codehaus.groovy.grails.web.mapping.UrlMapping
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.*;
import org.fusesource.jansi.Ansi;

/**
 * Renders URL mappings to the console
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class AnsiConsoleUrlMappingsRenderer implements UrlMappingsRenderer {
    public static final String DEFAULT_ACTION = '(default action)'
    PrintStream targetStream = System.out
    boolean isAnsiEnabled = GrailsConsole.getInstance().isAnsiEnabled()

    AnsiConsoleUrlMappingsRenderer(PrintStream targetStream) {
        this.targetStream = targetStream
    }

    AnsiConsoleUrlMappingsRenderer() {
    }

    @Override
    void render(List<UrlMapping> urlMappings) {
        final mappingsByController = urlMappings.groupBy { UrlMapping mapping -> mapping.controllerName }
        def longestMapping = establishUrlPattern(urlMappings.max { UrlMapping mapping -> establishUrlPattern(mapping, false).length() }, false).length() + 5
        final longestActionName = urlMappings.max { UrlMapping mapping ->
            if (mapping?.actionName) {
                return mapping?.actionName?.toString()?.length() ?: 0
            }
            return 0
        }
        def tmp = longestActionName.actionName ? longestActionName.actionName?.toString()?.length() : 0
        def longestAction = tmp + 10
        longestAction = longestAction < DEFAULT_ACTION.length() ? DEFAULT_ACTION.length() : longestAction
        final controllerNames = mappingsByController.keySet().sort()

        for(controller in controllerNames) {
            if (controller == null) {
                targetStream.println(header("Dynamic Mappings"))
            }
            else {
                targetStream.println(header("Controller", controller.toString()))
            }
            final controllerUrlMappings = mappingsByController.get(controller)
            for(UrlMapping urlMapping in controllerUrlMappings) {
                def urlPattern = establishUrlPattern(urlMapping, isAnsiEnabled, longestMapping)

                def actionName = urlMapping.actionName?.toString() ?: DEFAULT_ACTION
                if (actionName && !urlMapping.viewName) {
                    targetStream.println("${yellowBar()}${urlMapping.httpMethod.center(8)}${yellowBar()}${urlPattern}${yellowBar()}${bold('Action: ')}${actionName.padRight(longestAction)}${endBar()}")
                }
                else if (urlMapping.viewName) {
                    targetStream.println("${yellowBar()}${urlMapping.httpMethod.center(8)}${yellowBar()}${urlPattern}${yellowBar()}${bold('View:   ')}${urlMapping.viewName.toString().padRight(longestAction)}${endBar()}")
                }
            }
            targetStream.println()
        }
    }

    String bold(String text) {
        if (isAnsiEnabled)
            return ansi().a(Ansi.Attribute.INTENSITY_BOLD).a(text).a(Ansi.Attribute.INTENSITY_BOLD_OFF)
        else
            return text
    }

    protected String establishUrlPattern(UrlMapping urlMapping, boolean withAnsi = isAnsiEnabled, int padding = -1) {
        if (urlMapping instanceof ResponseCodeUrlMapping) {
            def errorCode = "ERROR: "+ ((ResponseCodeMappingData)urlMapping.urlData).responseCode
            if (withAnsi) {
                return padAnsi(error(errorCode), errorCode, padding)
            }
            else {
                return errorCode.padRight(padding)
            }
        }
        final constraints = urlMapping.constraints
        final tokens = urlMapping.urlData.tokens
        StringBuilder urlPattern = new StringBuilder(UrlMapping.SLASH)
        int constraintIndex = 0
        tokens.eachWithIndex { String token, int i ->
            boolean hasTokens = token.contains(UrlMapping.CAPTURED_WILDCARD) || token.contains(UrlMapping.CAPTURED_DOUBLE_WILDCARD)
            if (hasTokens) {
                String finalToken = token
                while(hasTokens) {
                    if (finalToken.contains(UrlMapping.CAPTURED_WILDCARD)) {
                        def constraint = constraints[constraintIndex++]
                        def prop = '\\${' + constraint.propertyName + '}'
                        finalToken = finalToken.replaceFirst(/\(\*\)/, withAnsi ? variable(prop) : prop)
                    }
                    else if (finalToken.contains(UrlMapping.CAPTURED_DOUBLE_WILDCARD)) {
                        def constraint = constraints[constraintIndex++]
                        def prop = '\\\${' + constraint.propertyName + '}**'
                        finalToken =  finalToken.replaceFirst(/\(\*\*\)/, prop)
                    }
                    hasTokens = finalToken.contains(UrlMapping.CAPTURED_WILDCARD) || finalToken.contains(UrlMapping.CAPTURED_DOUBLE_WILDCARD)
                }
                urlPattern << finalToken
            }
            else {
                urlPattern << token
            }


            if (i < (tokens.length-1)) {
                urlPattern << UrlMapping.SLASH
            }
        }
        if (padding) {
            if (withAnsi) {

                final nonAnsiPattern = establishUrlPattern(urlMapping, false)
                return padAnsi(urlPattern.toString(), nonAnsiPattern, padding)

            }
            else {
                return urlPattern.toString().padRight(padding)
            }
        }
        else {
            return urlPattern.toString()
        }
    }

    protected String padAnsi(String ansiString, String nonAnsiString, int padding) {
        def toPad = padding - nonAnsiString.length()
        if (toPad > 0) {
            final padText = getPadding(" ", toPad)
            return "${ansiString}$padText".toString()
        } else {
            return ansiString.toString()
        }
    }

    static String getPadding(String padding, int length) {
        if (padding.length() < length) {
            return padding.multiply(length / padding.length() + 1).substring(0, length);
        } else {
            return padding.substring(0, length);
        }
    }

    String error(String errorCode) {
        return ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(RED).a(errorCode).a(Ansi.Attribute.INTENSITY_BOLD_OFF).fg(DEFAULT)
    }

    String variable(String name, boolean withAnsi = isAnsiEnabled) {
        return ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(CYAN).a(name).a(Ansi.Attribute.INTENSITY_BOLD_OFF).fg(DEFAULT).reset()
    }

    String header(String text) {
        if (isAnsiEnabled) {
            ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(GREEN).a(text).a(Ansi.Attribute.INTENSITY_BOLD_OFF).fg(DEFAULT)
        }
        else {
            return text
        }
    }

    String header(String text, String description) {
        if (isAnsiEnabled) {
            ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(GREEN).a("$text: ".toString()).fg(DEFAULT).a(description).a(Ansi.Attribute.INTENSITY_BOLD_OFF)
        }
        else {
            return "$text: $description"
        }
    }

    String yellowBar() {
        if (isAnsiEnabled) {
            return ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(YELLOW).a(" | ").a(Ansi.Attribute.INTENSITY_BOLD_OFF).fg(DEFAULT)
        }
        return " | "
    }

    String endBar() {
        if (isAnsiEnabled) {
            return ansi().a(Ansi.Attribute.INTENSITY_BOLD).fg(YELLOW).a(" |").a(Ansi.Attribute.INTENSITY_BOLD_OFF).fg(DEFAULT)
        }
        return " |"
    }
}
