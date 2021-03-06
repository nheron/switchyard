/*
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.switchyard.component.common.composer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.namespace.QName;

import org.switchyard.Context;
import org.switchyard.ServiceDomain;
import org.switchyard.ServiceReference;
import org.switchyard.component.common.CommonCommonMessages;
import org.switchyard.common.lang.Strings;
import org.switchyard.common.property.PropertyConstants;
import org.switchyard.common.xml.XMLHelper;

/**
 * Base class for RegexContextMapper; adds the regex pattern matching ability.
 *
 * @param <D> the type of binding data
 *
 * @author David Ward &lt;<a href="mailto:dward@jboss.org">dward@jboss.org</a>&gt; &copy; 2012 Red Hat Inc.
 */
public class BaseRegexContextMapper<D extends BindingData> extends BaseContextMapper<D> implements RegexContextMapper<D> {

    private final List<Pattern> _includes = new ArrayList<Pattern>();
    private final List<Pattern> _excludes = new ArrayList<Pattern>();
    private final List<Pattern> _includeNamespaces = new ArrayList<Pattern>();
    private final List<Pattern> _excludeNamespaces = new ArrayList<Pattern>();


    // RTGOV Resubmission ID / property propagation
    private boolean _prefixPropagationSet = false;
    private static final String SERVICE_REFERENCE_PROPERTY = "org.switchyard.bus.camel.consumer";
    private final List<Pattern> _includeRegexes = new ArrayList<Pattern>();

    private void setPatternList(String regexs, List<Pattern> patternList) {
        Set<String> regexSet = Strings.uniqueSplitTrimToNull(regexs, ",");
        List<Pattern> tmpList = new ArrayList<Pattern>();
        for (String regex : regexSet) {
            try {
                Pattern pattern = Pattern.compile(regex);
                tmpList.add(pattern);
            } catch (PatternSyntaxException pse) {
                throw CommonCommonMessages.MESSAGES.isNotAValidRegexPattern(regex, pse.getMessage());
            }
        }
        synchronized (patternList) {
            patternList.clear();
            patternList.addAll(tmpList);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMapper<D> setIncludes(String includes) {
        setPatternList(includes, _includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMapper<D> setExcludes(String excludes) {
        setPatternList(excludes, _excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMapper<D> setIncludeNamespaces(String includeNamespaces) {
        setPatternList(includeNamespaces, _includeNamespaces);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContextMapper<D> setExcludeNamespaces(String excludeNamespaces) {
        setPatternList(excludeNamespaces, _excludeNamespaces);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(String name) {
        return matches(XMLHelper.createQName(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(QName qname) {
        return qname != null && matches(qname.getLocalPart(), _includes, _excludes) && matches(qname.getNamespaceURI(), _includeNamespaces, _excludeNamespaces);
    }

    /**
     * Check if the property name matches specified condition.
     * @param test test
     * @param includes includes
     * @param excludes excludes
     * @return true if it matches
     */
    public boolean matches(String test, List<Pattern> includes, List<Pattern> excludes) {
        boolean green = false;
        boolean red = false;
        for (Pattern include : includes) {
            if (include.matcher(test).matches()) {
                green = true;
                break;
            } else {
                red = true;
            }
        }
        boolean matches = green || !red;
        if (matches) {
            green = false;
            red = false;
            for (Pattern exclude : excludes) {
                if (!exclude.matcher(test).matches()) {
                    green = true;
                    break;
                } else {
                    red = true;
                }
            }
            matches = green || !red;
        }
        return matches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mapFrom(D source, Context context) throws Exception {
        setRegexPropagationList(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mapTo(Context context, D target) throws Exception {
        setRegexPropagationList(context);
    }

    /**
     * Set the list of regexes of properties that need to be propagated.
     *
     * @param context context
     */
    protected void setRegexPropagationList(Context context) {
        _includeRegexes.clear();

        if (!_prefixPropagationSet) {
            if (context.getProperty(SERVICE_REFERENCE_PROPERTY) != null) {
                ServiceDomain domain = ((ServiceReference)context.getProperty(SERVICE_REFERENCE_PROPERTY).getValue()).getDomain();

                if (domain.getProperty(PropertyConstants.DOMAIN_PROPERTY_PROPAGATE_REGEX) != null) {
                    String regexList = (String) domain.getProperty(PropertyConstants.DOMAIN_PROPERTY_PROPAGATE_REGEX);
                    setPatternList(regexList, _includeRegexes);
                }
            }
            _prefixPropagationSet = true;
        }
        Pattern rtGovResubmissionPattern = Pattern.compile(PropertyConstants.RTGOV_HEADER_RESUBMITTED_ID_PATTERN);
        _includeRegexes.add(rtGovResubmissionPattern);
    }
    
    protected List<Pattern> getIncludeRegexes() {
        return _includeRegexes;
    }
}
