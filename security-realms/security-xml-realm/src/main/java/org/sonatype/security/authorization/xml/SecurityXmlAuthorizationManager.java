/**
 * Copyright (c) 2007-2012 Sonatype, Inc. All rights reserved.
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
package org.sonatype.security.authorization.xml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.plexus.appevents.ApplicationEventMulticaster;
import org.sonatype.security.authorization.AuthorizationManager;
import org.sonatype.security.authorization.NoSuchPrivilegeException;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.authorization.Privilege;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.events.AuthorizationConfigurationChangedEvent;
import org.sonatype.security.model.CPrivilege;
import org.sonatype.security.model.CProperty;
import org.sonatype.security.model.CRole;
import org.sonatype.security.realms.tools.ConfigurationManager;

/**
 * AuthorizationManager that wraps roles from security-xml-realm.
 */
@Singleton
@Typed( AuthorizationManager.class )
@Named( "default" )
public class SecurityXmlAuthorizationManager
    implements AuthorizationManager
{
    public static final String SOURCE = "default";

    private final ConfigurationManager configuration;

    private final PrivilegeInheritanceManager privInheritance;

    private final ApplicationEventMulticaster eventMulticaster;

    @Inject
    public SecurityXmlAuthorizationManager( @Named( "resourceMerging" ) ConfigurationManager configuration,
                                            PrivilegeInheritanceManager privInheritance,
                                            ApplicationEventMulticaster eventMulticaster )
    {
        this.configuration = configuration;
        this.privInheritance = privInheritance;
        this.eventMulticaster = eventMulticaster;
    }

    public String getSource()
    {
        return SOURCE;
    }

    protected Role toRole( CRole secRole )
    {
        Role role = new Role();

        role.setRoleId( secRole.getId() );
        role.setName( secRole.getName() );
        role.setSource( SOURCE );
        role.setDescription( secRole.getDescription() );
        role.setReadOnly( secRole.isReadOnly() );
        role.setPrivileges( new HashSet<String>( secRole.getPrivileges() ) );
        role.setRoles( new HashSet<String>( secRole.getRoles() ) );

        return role;
    }

    protected CRole toRole( Role role )
    {
        CRole secRole = new CRole();

        secRole.setId( role.getRoleId() );
        secRole.setName( role.getName() );
        secRole.setDescription( role.getDescription() );
        secRole.setReadOnly( role.isReadOnly() );
        // null check
        if ( role.getPrivileges() != null )
        {
            secRole.setPrivileges( new ArrayList<String>( role.getPrivileges() ) );
        }
        else
        {
            secRole.setPrivileges( new ArrayList<String>() );
        }

        // null check
        if ( role.getRoles() != null )
        {
            secRole.setRoles( new ArrayList<String>( role.getRoles() ) );
        }
        else
        {
            secRole.setRoles( new ArrayList<String>() );
        }

        return secRole;
    }

    protected CPrivilege toPrivilege( Privilege privilege )
    {
        CPrivilege secPriv = new CPrivilege();
        secPriv.setId( privilege.getId() );
        secPriv.setName( privilege.getName() );
        secPriv.setDescription( privilege.getDescription() );
        secPriv.setReadOnly( privilege.isReadOnly() );
        secPriv.setType( privilege.getType() );

        if ( privilege.getProperties() != null && privilege.getProperties().entrySet() != null )
        {
            for ( Entry<String, String> entry : privilege.getProperties().entrySet() )
            {
                CProperty prop = new CProperty();
                prop.setKey( entry.getKey() );
                prop.setValue( entry.getValue() );
                secPriv.addProperty( prop );
            }
        }

        return secPriv;
    }

    protected Privilege toPrivilege( CPrivilege secPriv )
    {
        Privilege privilege = new Privilege();
        privilege.setId( secPriv.getId() );
        privilege.setName( secPriv.getName() );
        privilege.setDescription( secPriv.getDescription() );
        privilege.setReadOnly( secPriv.isReadOnly() );
        privilege.setType( secPriv.getType() );

        if ( secPriv.getProperties() != null )
        {
            for ( CProperty prop : (List<CProperty>) secPriv.getProperties() )
            {
                privilege.addProperty( prop.getKey(), prop.getValue() );
            }
        }

        return privilege;
    }

    // //
    // ROLE CRUDS
    // //

    public Set<Role> listRoles()
    {
        Set<Role> roles = new HashSet<Role>();
        List<CRole> secRoles = this.configuration.listRoles();

        for ( CRole CRole : secRoles )
        {
            roles.add( this.toRole( CRole ) );
        }

        return roles;
    }

    public Role getRole( String roleId )
        throws NoSuchRoleException
    {
        return this.toRole( this.configuration.readRole( roleId ) );
    }

    public Role addRole( Role role )
        throws InvalidConfigurationException
    {
        // the roleId of the secRole might change, so we need to keep the reference
        CRole secRole = this.toRole( role );

        this.configuration.createRole( secRole );
        this.saveConfiguration();

        return this.toRole( secRole );
    }

    public Role updateRole( Role role )
        throws NoSuchRoleException, InvalidConfigurationException
    {
        CRole secRole = this.toRole( role );

        this.configuration.updateRole( secRole );
        this.saveConfiguration();

        return this.toRole( secRole );
    }

    public void deleteRole( String roleId )
        throws NoSuchRoleException
    {
        this.configuration.deleteRole( roleId );
        this.saveConfiguration();
    }

    // //
    // PRIVILEGE CRUDS
    // //

    public Set<Privilege> listPrivileges()
    {
        Set<Privilege> privileges = new HashSet<Privilege>();
        List<CPrivilege> secPrivs = this.configuration.listPrivileges();

        for ( CPrivilege CPrivilege : secPrivs )
        {
            privileges.add( this.toPrivilege( CPrivilege ) );
        }

        return privileges;
    }

    public Privilege getPrivilege( String privilegeId )
        throws NoSuchPrivilegeException
    {
        return this.toPrivilege( this.configuration.readPrivilege( privilegeId ) );
    }

    public Privilege addPrivilege( Privilege privilege )
        throws InvalidConfigurationException
    {
        CPrivilege secPriv = this.toPrivilege( privilege );
        // create implies read, so we need to add logic for that
        addInheritedPrivileges( secPriv );

        this.configuration.createPrivilege( secPriv );
        this.saveConfiguration();

        return this.toPrivilege( secPriv );
    }

    public Privilege updatePrivilege( Privilege privilege )
        throws NoSuchPrivilegeException, InvalidConfigurationException
    {
        CPrivilege secPriv = this.toPrivilege( privilege );
        this.configuration.updatePrivilege( secPriv );
        this.saveConfiguration();

        return this.toPrivilege( secPriv );
    }

    public void deletePrivilege( String privilegeId )
        throws NoSuchPrivilegeException
    {
        this.configuration.deletePrivilege( privilegeId );
        this.saveConfiguration();
    }

    private void saveConfiguration()
    {
        this.configuration.save();

        // notify any listeners that the config changed
        this.fireAuthorizationChangedEvent();
    }

    public boolean supportsWrite()
    {
        return true;
    }

    private void addInheritedPrivileges( CPrivilege privilege )
    {
        CProperty methodProperty = null;

        for ( CProperty property : (List<CProperty>) privilege.getProperties() )
        {
            if ( property.getKey().equals( "method" ) )
            {
                methodProperty = property;
                break;
            }
        }

        if ( methodProperty != null )
        {
            List<String> inheritedMethods = privInheritance.getInheritedMethods( methodProperty.getValue() );

            StringBuffer buf = new StringBuffer();

            for ( String method : inheritedMethods )
            {
                buf.append( method );
                buf.append( "," );
            }

            if ( buf.length() > 0 )
            {
                buf.setLength( buf.length() - 1 );

                methodProperty.setValue( buf.toString() );
            }
        }
    }

    private void fireAuthorizationChangedEvent()
    {
        this.eventMulticaster.notifyEventListeners( new AuthorizationConfigurationChangedEvent( null ) );
    }

}
