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
package org.sonatype.security.realms.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.configuration.ConfigurationException;
import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.configuration.validation.ValidationMessage;
import org.sonatype.configuration.validation.ValidationResponse;
import org.sonatype.security.authorization.NoSuchPrivilegeException;
import org.sonatype.security.authorization.NoSuchRoleException;
import org.sonatype.security.model.CPrivilege;
import org.sonatype.security.model.CProperty;
import org.sonatype.security.model.CRole;
import org.sonatype.security.model.CUser;
import org.sonatype.security.model.CUserRoleMapping;
import org.sonatype.security.model.Configuration;
import org.sonatype.security.model.source.SecurityModelConfigurationSource;
import org.sonatype.security.realms.privileges.PrivilegeDescriptor;
import org.sonatype.security.realms.validator.SecurityConfigurationValidator;
import org.sonatype.security.realms.validator.SecurityValidationContext;
import org.sonatype.security.usermanagement.StringDigester;
import org.sonatype.security.usermanagement.UserNotFoundException;
import org.sonatype.security.usermanagement.xml.SecurityXmlUserManager;

@Singleton
@Typed( value = ConfigurationManager.class )
@Named( value = "default" )
public class DefaultConfigurationManager
    extends AbstractConfigurationManager
{

    private final SecurityModelConfigurationSource configurationSource;

    private final SecurityConfigurationValidator validator;

    private final List<PrivilegeDescriptor> privilegeDescriptors;

    private final SecurityConfigurationCleaner configCleaner;

    private final List<SecurityConfigurationModifier> configurationModifiers;

    private final ReadWriteLock cfgLock = new ReentrantReadWriteLock();

    private List<CPrivilege> privileges;

    private List<CUser> users;

    private List<CRole> roles;

    @Inject
    public DefaultConfigurationManager( @Named( value = "file" ) SecurityModelConfigurationSource configurationSource,
                                        SecurityConfigurationValidator validator,
                                        List<PrivilegeDescriptor> privilegeDescriptors,
                                        SecurityConfigurationCleaner configCleaner,
                                        List<SecurityConfigurationModifier> configurationModifiers )
    {
        this.configurationSource = configurationSource;
        this.validator = validator;
        this.privilegeDescriptors = privilegeDescriptors;
        this.configCleaner = configCleaner;
        this.configurationModifiers = configurationModifiers;
    }

    public List<CPrivilege> listPrivileges()
    {
        if ( privileges == null )
        {
            Lock lock = cfgLock.writeLock();
            try
            {
                lock.lock();
                if ( privileges == null )
                {
                    privileges = getConfiguration().getPrivileges();
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return privileges;
    }

    public List<CRole> listRoles()
    {
        if ( roles == null )
        {
            Lock lock = cfgLock.writeLock();
            try
            {
                lock.lock();
                if ( roles == null )
                {
                    roles = getConfiguration().getRoles();
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return roles;
    }

    public List<CUser> listUsers()
    {
        if ( users == null )
        {
            Lock lock = cfgLock.writeLock();
            try
            {
                lock.lock();
                if ( users == null )
                {
                    users = getConfiguration().getUsers();
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return users;
    }

    public void createPrivilege( CPrivilege privilege )
        throws InvalidConfigurationException
    {
        createPrivilege( privilege, initializeContext() );
    }

    public void createPrivilege( CPrivilege privilege, SecurityValidationContext context )
        throws InvalidConfigurationException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            ValidationResponse vr = validator.validatePrivilege( context, privilege, false );

            if ( vr.isValid() )
            {
                getConfiguration().addPrivilege( privilege );
                privileges = getConfiguration().getPrivileges();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void createRole( CRole role )
        throws InvalidConfigurationException
    {
        createRole( role, initializeContext() );
    }

    public void createRole( CRole role, SecurityValidationContext context )
        throws InvalidConfigurationException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            ValidationResponse vr = validator.validateRole( context, role, false );

            if ( vr.isValid() )
            {
                getConfiguration().addRole( role );
                roles = getConfiguration().getRoles();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void createUser( CUser user, Set<String> roles )
        throws InvalidConfigurationException
    {
        createUser( user, null, roles, initializeContext() );
    }

    public void createUser( CUser user, String password, Set<String> roles )
        throws InvalidConfigurationException
    {
        createUser( user, password, roles, initializeContext() );
    }

    public void createUser( CUser user, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException
    {
        createUser( user, null, roles, context );
    }

    public void createUser( CUser user, String password, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            // set the password if its not null
            if ( password != null && password.trim().length() > 0 )
            {
                user.setPassword( StringDigester.getSha1Digest( password ) );
            }

            ValidationResponse vr = validator.validateUser( context, user, roles, false );

            if ( vr.isValid() )
            {
                getConfiguration().addUser( user );
                createOrUpdateUserRoleMapping( buildUserRoleMapping( user.getId(), roles ) );

                users = getConfiguration().getUsers();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    private void createOrUpdateUserRoleMapping( CUserRoleMapping roleMapping )
    {
        // delete first, ask questions later
        // we are always updating, its possible that this object could have already existed, because we cannot fully
        // sync with external realms.
        try
        {
            deleteUserRoleMapping( roleMapping.getUserId(), roleMapping.getSource() );
        }
        catch ( NoSuchRoleMappingException e )
        {
            // it didn't exist, thats ok.
        }

        // now add it
        getConfiguration().addUserRoleMapping( roleMapping );

    }

    private CUserRoleMapping buildUserRoleMapping( String userId, Set<String> roles )
    {
        CUserRoleMapping roleMapping = new CUserRoleMapping();

        roleMapping.setUserId( userId );
        roleMapping.setSource( SecurityXmlUserManager.SOURCE );
        roleMapping.setRoles( new ArrayList<String>( roles ) );

        return roleMapping;

    }

    public void deletePrivilege( String id )
        throws NoSuchPrivilegeException
    {
        deletePrivilege( id, true );
    }

    public void deletePrivilege( String id, boolean clean )
        throws NoSuchPrivilegeException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            boolean found = getConfiguration().removePrivilegeById( id );

            if ( !found )
            {
                throw new NoSuchPrivilegeException( id );
            }

            privileges = getConfiguration().getPrivileges();

            if ( clean )
            {
                cleanRemovedPrivilege( id );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void deleteRole( String id )
        throws NoSuchRoleException
    {
        deleteRole( id, true );
    }

    protected void deleteRole( String id, boolean clean )
        throws NoSuchRoleException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            boolean found = getConfiguration().removeRoleById( id );

            if ( !found )
            {
                throw new NoSuchRoleException( id );
            }

            roles = getConfiguration().getRoles();

            if ( clean )
            {
                cleanRemovedRole( id );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void deleteUser( String id )
        throws UserNotFoundException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            boolean found = getConfiguration().removeUserById( id );

            if ( !found )
            {
                throw new UserNotFoundException( id );
            }

            users = getConfiguration().getUsers();

            // delete the user role mapping for this user too
            try
            {
                deleteUserRoleMapping( id, SecurityXmlUserManager.SOURCE );
            }
            catch ( NoSuchRoleMappingException e )
            {
                this.getLogger().debug(
                    "User role mapping for user: " + id + " source: " + SecurityXmlUserManager.SOURCE
                        + " could not be deleted because it does not exist." );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public CPrivilege readPrivilege( String id )
        throws NoSuchPrivilegeException
    {
        Lock lock = cfgLock.readLock();
        try
        {
            lock.lock();

            CPrivilege privilege = getConfiguration().getPrivilegeById( id );

            if ( privilege != null )
            {
                return privilege;
            }
            else
            {
                throw new NoSuchPrivilegeException( id );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public CRole readRole( String id )
        throws NoSuchRoleException
    {
        Lock lock = cfgLock.readLock();
        try
        {
            lock.lock();

            CRole role = getConfiguration().getRoleById( id );

            if ( role != null )
            {
                return role;
            }
            else
            {
                throw new NoSuchRoleException( id );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public CUser readUser( String id )
        throws UserNotFoundException
    {
        Lock lock = cfgLock.readLock();
        try
        {
            lock.lock();

            CUser user = getConfiguration().getUserById( id );

            if ( user != null )
            {
                return user;
            }
            else
            {
                throw new UserNotFoundException( id );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void updatePrivilege( CPrivilege privilege )
        throws InvalidConfigurationException, NoSuchPrivilegeException
    {
        updatePrivilege( privilege, initializeContext() );
    }

    public void updatePrivilege( CPrivilege privilege, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchPrivilegeException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            ValidationResponse vr = validator.validatePrivilege( context, privilege, true );

            if ( vr.isValid() )
            {
                deletePrivilege( privilege.getId(), false );
                getConfiguration().addPrivilege( privilege );

                privileges = getConfiguration().getPrivileges();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void updateRole( CRole role )
        throws InvalidConfigurationException, NoSuchRoleException
    {
        updateRole( role, initializeContext() );
    }

    public void updateRole( CRole role, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchRoleException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            ValidationResponse vr = validator.validateRole( context, role, true );

            if ( vr.isValid() )
            {
                deleteRole( role.getId(), false );
                getConfiguration().addRole( role );

                roles = getConfiguration().getRoles();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void updateUser( CUser user, Set<String> roles )
        throws InvalidConfigurationException, UserNotFoundException
    {
        updateUser( user, roles, initializeContext() );
    }

    public void updateUser( CUser user, Set<String> roles, SecurityValidationContext context )
        throws InvalidConfigurationException, UserNotFoundException
    {
        Lock lock = cfgLock.writeLock();
        try
        {
            lock.lock();

            if ( context == null )
            {
                context = initializeContext();
            }

            ValidationResponse vr = validator.validateUser( context, user, roles, true );

            if ( vr.isValid() )
            {
                deleteUser( user.getId() );
                getConfiguration().addUser( user );
                this.createOrUpdateUserRoleMapping( this.buildUserRoleMapping( user.getId(), roles ) );

                users = getConfiguration().getUsers();
            }
            else
            {
                throw new InvalidConfigurationException( vr );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public String getPrivilegeProperty( CPrivilege privilege, String key )
    {
        if ( privilege != null && privilege.getProperties() != null )
        {
            for ( CProperty prop : privilege.getProperties() )
            {
                if ( prop.getKey().equals( key ) )
                {
                    return prop.getValue();
                }
            }
        }

        return null;
    }

    public void createUserRoleMapping( CUserRoleMapping userRoleMapping )
        throws InvalidConfigurationException
    {
        createUserRoleMapping( userRoleMapping, initializeContext() );
    }

    public void createUserRoleMapping( CUserRoleMapping userRoleMapping, SecurityValidationContext context )
        throws InvalidConfigurationException
    {
        if ( context == null )
        {
            context = this.initializeContext();
        }

        try
        {
            // this will throw a NoSuchRoleMappingException, if there isn't one
            readUserRoleMapping( userRoleMapping.getUserId(), userRoleMapping.getSource() );

            ValidationResponse vr = new ValidationResponse();
            vr.addValidationError( new ValidationMessage( "*", "User Role Mapping for user '"
                + userRoleMapping.getUserId() + "' already exists." ) );

            throw new InvalidConfigurationException( vr );
        }
        catch ( NoSuchRoleMappingException e )
        {
            // expected
        }

        ValidationResponse vr = validator.validateUserRoleMapping( context, userRoleMapping, false );

        if ( vr.getValidationErrors().size() > 0 )
        {
            throw new InvalidConfigurationException( vr );
        }

        getConfiguration().addUserRoleMapping( userRoleMapping );
    }

    private CUserRoleMapping readCUserRoleMapping( String userId, String source )
        throws NoSuchRoleMappingException
    {
        CUserRoleMapping mapping = getConfiguration().getUserRoleMappingByUserId( userId, source );

        if ( mapping != null )
        {
            return mapping;
        }
        else
        {
            throw new NoSuchRoleMappingException( "No User Role Mapping for user: " + userId );
        }
    }

    public CUserRoleMapping readUserRoleMapping( String userId, String source )
        throws NoSuchRoleMappingException
    {
        return readCUserRoleMapping( userId, source );
    }

    public void updateUserRoleMapping( CUserRoleMapping userRoleMapping )
        throws InvalidConfigurationException, NoSuchRoleMappingException
    {
        updateUserRoleMapping( userRoleMapping, initializeContext() );
    }

    public void updateUserRoleMapping( CUserRoleMapping userRoleMapping, SecurityValidationContext context )
        throws InvalidConfigurationException, NoSuchRoleMappingException
    {
        if ( context == null )
        {
            context = initializeContext();
        }

        if ( readUserRoleMapping( userRoleMapping.getUserId(), userRoleMapping.getSource() ) == null )
        {
            ValidationResponse vr = new ValidationResponse();
            vr.addValidationError( new ValidationMessage( "*", "No User Role Mapping found for user '"
                + userRoleMapping.getUserId() + "'." ) );

            throw new InvalidConfigurationException( vr );
        }

        ValidationResponse vr = validator.validateUserRoleMapping( context, userRoleMapping, true );

        if ( vr.getValidationErrors().size() > 0 )
        {
            throw new InvalidConfigurationException( vr );
        }

        deleteUserRoleMapping( userRoleMapping.getUserId(), userRoleMapping.getSource() );
        getConfiguration().addUserRoleMapping( userRoleMapping );
    }

    public List<CUserRoleMapping> listUserRoleMappings()
    {
        return getConfiguration().getUserRoleMappings();
    }

    public void deleteUserRoleMapping( String userId, String source )
        throws NoSuchRoleMappingException
    {
        boolean found = getConfiguration().removeUserRoleMappingByUserId( userId, source );

        if ( !found )
        {
            throw new NoSuchRoleMappingException( "No User Role Mapping for user: " + userId );
        }
    }

    public String getPrivilegeProperty( String id, String key )
        throws NoSuchPrivilegeException
    {
        return getPrivilegeProperty( readPrivilege( id ), key );
    }

    public synchronized void save()
    {
        try
        {
            this.configurationSource.storeConfiguration();
        }
        catch ( IOException e )
        {
            getLogger().error( "IOException while storing configuration file", e );
        }
    }

    @Override
    protected Configuration doGetConfiguration()
    {
        try
        {
            this.configurationSource.loadConfiguration();

            boolean modified = false;
            for ( SecurityConfigurationModifier modifier : configurationModifiers )
            {
                modified |= modifier.apply( configurationSource.getConfiguration() );
            }

            if ( modified )
            {
                configurationSource.backupConfiguration();
                configurationSource.storeConfiguration();
            }

            return this.configurationSource.getConfiguration();
        }
        catch ( IOException e )
        {
            getLogger().error( "IOException while retrieving configuration file", e );

            throw new IllegalStateException( "Cannot load configuration!", e );
        }
        catch ( ConfigurationException e )
        {
            getLogger().error( "Invalid Configuration", e );

            throw new IllegalStateException( "Invalid configuration!", e );
        }
    }

    public SecurityValidationContext initializeContext()
    {
        SecurityValidationContext context = new SecurityValidationContext();

        context.addExistingUserIds();
        context.addExistingRoleIds();
        context.addExistingPrivilegeIds();

        for ( CUser user : listUsers() )
        {
            context.getExistingUserIds().add( user.getId() );

            context.getExistingEmailMap().put( user.getId(), user.getEmail() );
        }

        for ( CRole role : listRoles() )
        {
            context.getExistingRoleIds().add( role.getId() );

            ArrayList<String> containedRoles = new ArrayList<String>();

            containedRoles.addAll( role.getRoles() );

            context.getRoleContainmentMap().put( role.getId(), containedRoles );

            context.getExistingRoleNameMap().put( role.getId(), role.getName() );
        }

        for ( CPrivilege priv : listPrivileges() )
        {
            context.getExistingPrivilegeIds().add( priv.getId() );
        }

        for ( CUserRoleMapping roleMappings : listUserRoleMappings() )
        {
            context.getExistingUserRoleMap().put( roleMappings.getUserId(), roleMappings.getRoles() );
        }

        return context;
    }

    public List<PrivilegeDescriptor> listPrivilegeDescriptors()
    {
        return Collections.unmodifiableList( privilegeDescriptors );
    }

    public void cleanRemovedPrivilege( String privilegeId )
    {
        configCleaner.privilegeRemoved( getConfiguration(), privilegeId );
    }

    public void cleanRemovedRole( String roleId )
    {
        configCleaner.roleRemoved( getConfiguration(), roleId );
    }

    @Override
    public synchronized void clearCache()
    {
        super.clearCache();

        this.users = null;
        this.roles = null;
        this.privileges = null;
    }
}
