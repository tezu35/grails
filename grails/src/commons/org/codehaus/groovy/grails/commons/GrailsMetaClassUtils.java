/* Copyright 2004-2005 Graeme Rocher
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
package org.codehaus.groovy.grails.commons;

import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaClass;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.grails.commons.metaclass.AdapterMetaClass;
import org.codehaus.groovy.grails.commons.metaclass.ExpandoMetaClass;
import org.codehaus.groovy.grails.commons.metaclass.ClosureInvokingMethod;
import org.codehaus.groovy.grails.commons.metaclass.ThreadManagedMetaBeanProperty;
import org.codehaus.groovy.grails.web.metaclass.TagLibMetaClass;
import org.springframework.beans.BeanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Iterator;
import java.lang.reflect.Constructor;

/**
 * A class that provides utility methods for working with the Groovy MetaClass API
 *
 * @author Graeme Rocher
 * @since 0.5
 *        <p/>
 *
 *        Created: Feb 21, 2007
 *        Time: 6:01:07 PM
 */
public class GrailsMetaClassUtils {
    private static final Log LOG = LogFactory.getLog(GrailsMetaClassUtils.class);

    /**
     * Retrieves the MetaClassRegistry instance
     *
     * @return The registry
     */
    public static MetaClassRegistry getRegistry() {
        return InvokerHelper.getInstance().getMetaRegistry();
    }

    /**
     * Copies the ExpandoMetaClass dynamic methods and properties from one Class to another
     *
     * @param fromClass The source class
     * @param toClass  The destination class
     * @param removeSource Whether to remove the source class after completion. True if yes
     */
    public static void copyExpandoMetaClass(Class fromClass, Class toClass, boolean removeSource) {
        MetaClassRegistry registry = getRegistry();

        MetaClass oldMetaClass = registry.getMetaClass(fromClass);


        AdapterMetaClass adapter = null;
        ExpandoMetaClass emc;

        if(oldMetaClass instanceof AdapterMetaClass) {
            adapter = ((AdapterMetaClass)oldMetaClass);
            emc = (ExpandoMetaClass)adapter.getAdaptee();
            if(LOG.isDebugEnabled()) {
                LOG.debug("Obtained adapted MetaClass ["+emc+"] from AdapterMetaClass instance ["+adapter+"]");
            }

            if(removeSource)
                registry.removeMetaClass(fromClass);
        }
        else {
            emc = (ExpandoMetaClass)oldMetaClass;
            if(LOG.isDebugEnabled()) {
                LOG.debug("No adapter MetaClass found, using original ["+emc+"]");
            }            
        }

        List metaMethods = emc.getExpandoMethods();
        ExpandoMetaClass replacement = new ExpandoMetaClass(toClass);
        replacement.setAllowChangesAfterInit(true);
        for (Iterator i = metaMethods.iterator(); i.hasNext();) {
            Object obj = i.next();
            if(obj instanceof ClosureInvokingMethod) {
                ClosureInvokingMethod cim = (ClosureInvokingMethod) obj;
                Closure callable = cim.getClosure();
                if(!cim.isStatic()) {
                    replacement.setProperty(cim.getName(), callable);
                }
                else {
                    ((GroovyObject)replacement.getProperty(ExpandoMetaClass.STATIC_QUALIFIER)).setProperty(cim.getName(),callable);
                }
            }
        }
        List metaProperties = emc.getExpandoProperties();
        for (Iterator i = metaProperties.iterator(); i.hasNext();) {
            Object o = i.next();
            if(o instanceof ThreadManagedMetaBeanProperty) {
                ThreadManagedMetaBeanProperty mbp = (ThreadManagedMetaBeanProperty)o;
                replacement.setProperty( mbp.getName(), mbp.getInitialValue() );
            }
        }
        replacement.initialize();
        TagLibArtefactHandler handler = new TagLibArtefactHandler();
        if(adapter == null && !handler.isArtefact(toClass)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Adding MetaClass for class ["+toClass+"] MetaClass ["+replacement+"]");
            }
            registry.setMetaClass(toClass, replacement);
        }
        else if(handler.isArtefact(toClass)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Replacing tag library ["+toClass+"] MetaClass ["+replacement+"]");
            }

            registry.setMetaClass(toClass, new TagLibMetaClass(replacement));            
        }
        else {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Adding MetaClass for class ["+toClass+"] MetaClass ["+replacement+"] with adapter ["+adapter+"]");
            }
            try {
                Constructor c = adapter.getClass().getConstructor(new Class[]{MetaClass.class});
                MetaClass newAdapter = (MetaClass) BeanUtils.instantiateClass(c,new Object[]{replacement});
                registry.setMetaClass(toClass,newAdapter);

            } catch (NoSuchMethodException e) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Exception thrown construction to MetaClass adapter when reloading: " + e.getMessage(),e);
                }
            }

        }

    }

    public static ExpandoMetaClass getExpandoMetaClass(Class aClass) {
        MetaClassRegistry registry = getRegistry();

        MetaClass mc = registry.getMetaClass(aClass);
        if(mc instanceof ExpandoMetaClass) {
            return (ExpandoMetaClass)mc;
        }
        else {
            registry.removeMetaClass(aClass);
            mc = registry.getMetaClass(aClass);
            if(mc instanceof ExpandoMetaClass) {
                return (ExpandoMetaClass)mc;
            }
            else {
                ExpandoMetaClass emc = new ExpandoMetaClass(aClass, true);
                registry.setMetaClass(aClass, emc);
                return emc;
            }
        }
    }
}
