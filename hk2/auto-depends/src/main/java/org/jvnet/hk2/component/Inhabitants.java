/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.jvnet.hk2.component;

import com.sun.hk2.component.ScopedInhabitant;
import com.sun.hk2.component.SingletonInhabitant;
import com.sun.hk2.component.ExistingSingletonInhabitant;
import org.jvnet.hk2.annotations.Scoped;

import javax.print.attribute.UnmodifiableSetException;
import java.util.*;

/**
 * Factory for {@link Inhabitant}.
 * @author Kohsuke Kawaguchi
 */
public class Inhabitants {
    /**
     * Creates a singleton wrapper around existing object.
     */
    public static <T> Inhabitant<T> create(T instance) {
        return new ExistingSingletonInhabitant<T>(instance);
    }
    
    /**
     * Creates a {@link Inhabitant} by looking at annotations of the given type.
     */
    public static <T> Inhabitant<T> create(Class<T> c, Habitat habitat, MultiMap<String,String> metadata) {
        return wrapByScope(c, Wombs.create(c,habitat,metadata), habitat);
    }

    /**
     * Creates a {@link Inhabitant} by wrapping {@link Womb} to handle scoping right.
     */
    public static <T> Inhabitant<T> wrapByScope(Class<T> c, Womb<T> womb, Habitat habitat) {
        Scoped scoped = c.getAnnotation(Scoped.class);
        if(scoped==null)
            return new SingletonInhabitant<T>(womb); // treated as singleton

        Class<? extends Scope> scopeClass = scoped.value();

        // those two scopes are so common and different that they deserve
        // specialized code optimized for them.
        if(scopeClass== PerLookup.class)
            return womb;
        if(scopeClass== Singleton.class)
            return new SingletonInhabitant<T>(womb);

        // other general case
        Scope scope = habitat.getByType(scopeClass);
        if(scope==null)
            throw new ComponentException("Failed to look up %s for %s",scopeClass,c);
        return new ScopedInhabitant<T>(womb,scope);
    }

    /**
     * Calculate the list of indexes under which the inhabitant is registered.
     * An index is usually obtained from a contract implementation, a service can
     * be implementing more than one contract and therefore be indexed by multiple
     * contract names.
     *
     * @param i instance of inhabitant to obtain the indexes from
     * @param <T> Contract type, optional
     * @return a collection of indexes (usually there is only one) under which this
     * service is available.
     */
    public static <T> Collection<String> getIndexes(Inhabitant<T> i) {
        ArrayList<String> indexes = new ArrayList<String>();
        Iterator<Map.Entry<String, List<String>>> itr = i.metadata().entrySet().iterator();
        while (itr.hasNext()) {
            indexes.add(itr.next().getKey());
        }
        return indexes;
    }

    /**
     * Returns the list of names the service implementation in known. Services in hk2 are
     * indexed by the contract name and an optional name. There can also be some aliasing
     * so the same service can be known under different names.
     *
     * @param i instance of inhabitant to obtain its registration name
     * @param indexName the contract name this service is implementing
     * @param <T> contract type, optional
     * @return a collection of names (usually there is only one) under which this service
     * is registered for the passed contract name
     */
    public static <T> Collection<String> getNamesFor(Inhabitant<T> i, String indexName) {
        return new ArrayList<String>(i.metadata().get(indexName));
    }

}
