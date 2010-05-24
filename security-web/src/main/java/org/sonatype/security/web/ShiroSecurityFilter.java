/**
 * Copyright (c) 2008 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.security.web;

import java.util.Map;

import org.apache.shiro.config.Ini;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.security.SecuritySystem;

/**
 * Extension of ShiroFilter that uses Plexus lookup to get the configuration, if any role param is given. Otherwise it
 * fallbacks to the standard stuff from JSecurityFilter.
 * 
 * @author cstamas
 */
public class ShiroSecurityFilter
    extends IniShiroFilter
{

    private SecuritySystem securitySystem;

    private WebSecurityManager securityManager;

    @Override
    protected Map<String, ?> applySecurityManager( Ini ini )
    {
        // use this to customize loading of the securityManager.
        // we are just going to use the component from SecuritySystem, so we do not need it.

        // bean map used to load custom filters
        // TODO: ( at least to think about ) we could inject filters using the container
        return null;
    }

    @Override
    public WebSecurityManager getSecurityManager()
    {
        return securityManager;
    }

    public PlexusContainer getPlexusContainer()
    {
        return (PlexusContainer) getContextAttribute( PlexusConstants.PLEXUS_KEY );
    }

    @Override
    protected void configure()
        throws Exception
    {

        // start up security
        this.getSecuritySystem().start();
        this.securityManager = (WebSecurityManager) this.getPlexusContainer().lookup( RealmSecurityManager.class );

        // call super
        super.configure();

        FilterChainManager filterChainManager =
            ( (PathMatchingFilterChainResolver) super.getFilterChainResolver() ).getFilterChainManager();

        ProtectedPathManager protectedPathManager = this.getPlexusContainer().lookup( ProtectedPathManager.class );
        // this cannot be injected as long as the configuration comes from the servlet config.
        // TODO: push the ini config in its own file.
        if ( FilterChainManagerAware.class.isInstance( protectedPathManager ) )
        {
            ( (FilterChainManagerAware) protectedPathManager ).setFilterChainManager( filterChainManager );
        }
    }

    private SecuritySystem getSecuritySystem()
    {
        // lazy load it using the container
        if ( this.securitySystem == null )
        {
            try
            {
                this.securitySystem = this.getPlexusContainer().lookup( SecuritySystem.class );
            }
            catch ( ComponentLookupException e )
            {
                throw new IllegalStateException( "Failed to load the Security System.", e );
            }
        }

        return this.securitySystem;
    }

}