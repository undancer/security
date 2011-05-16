package org.sonatype.security.realms.kenai.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.locks.ReentrantLock;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.sonatype.configuration.ConfigurationException;
import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.configuration.validation.ValidationMessage;
import org.sonatype.configuration.validation.ValidationResponse;
import org.sonatype.security.SecuritySystem;

import com.sonatype.security.realms.kenai.config.model.Configuration;
import com.sonatype.security.realms.kenai.config.model.io.xpp3.KenaiRealmConfigurationXpp3Reader;
import com.sonatype.security.realms.kenai.config.model.io.xpp3.KenaiRealmConfigurationXpp3Writer;

@Singleton
@Named
@Typed( value = KenaiRealmConfiguration.class )
public class DefaultKenaiRealmConfiguration
    implements KenaiRealmConfiguration
{
    
    @Inject
    @Named( value = "${application-conf}/kenai-realm.xml" )
    private File configurationFile;
    
    @Inject
    private Logger logger;
    
    @Inject
    private SecuritySystem securitySystem; // used for validation

    private Configuration configuration;
    
    private ReentrantLock lock = new ReentrantLock();
    
    public Configuration getConfiguration()
    {
        Reader fileReader = null;
        try
        {
            lock.lock();
        
            if( configuration != null )
            {
                return configuration;
            }   
            
            fileReader = new FileReader( this.getConfigFile() );
            KenaiRealmConfigurationXpp3Reader reader = new KenaiRealmConfigurationXpp3Reader();

            configuration = reader.read( fileReader );
            
        }
        catch ( FileNotFoundException e )
        {
            logger.error( "Kenai Realm configuration file does not exist: " + this.getConfigFile().getAbsolutePath() );
        }
        catch ( IOException e )
        {
            logger.error( "IOException while retrieving configuration file", e );
        }
        catch ( XmlPullParserException e )
        {
            logger.error( "Invalid XML Configuration", e );
        }
        finally
        {
            IOUtil.close( fileReader );
            if ( configuration == null )
            {
                configuration = new Configuration();
            }
            
            lock.unlock();
        }
        
        return configuration;
    }

    public void save() throws ConfigurationException
    {
        FileWriter fileWriter = null;
        try
        {
            lock.lock();
            
            File configFile = this.getConfigFile();
            // make the parent dirs first
            configFile.getParentFile().mkdirs();
            
            fileWriter = new FileWriter( configFile );

            KenaiRealmConfigurationXpp3Writer writer = new KenaiRealmConfigurationXpp3Writer();
            writer.write( fileWriter, this.configuration );
        }
        catch ( IOException e )
        {
            throw new ConfigurationException( "Failed to save configuration: "+ e.getMessage(), e );
        }
        finally
        {
            lock.unlock();
        }

    }

    public void updateConfiguration( Configuration newConfig )
        throws ConfigurationException
    {
        try
        {
            lock.lock();
            
            newConfig.setVersion( Configuration.MODEL_VERSION );

            ValidationResponse validationResponse = this.validateConfig( newConfig );
            if ( !validationResponse.isValid() )
            {
                throw new InvalidConfigurationException( validationResponse );
            }

            this.configuration = newConfig;
            
            this.save();
            
        }
        finally
        {
            lock.unlock();
        }

    }
    
    private ValidationResponse validateConfig( Configuration config )
    {
        ValidationResponse response = new ValidationResponse();

        if ( StringUtils.isEmpty( config.getBaseUrl() ) )
        {
            ValidationMessage msg = new ValidationMessage( "baseUrl", "Base Url cannot be empty." );
            response.addValidationError( msg );
        }
        else
        {
            try
            {
                new URL( config.getBaseUrl() );
            }
            catch ( MalformedURLException e )
            {
                ValidationMessage msg = new ValidationMessage( "baseUrl", "Base Url is not valid: "+ e.getMessage() );
                response.addValidationError( msg );
            }
        }
        
        if ( StringUtils.isEmpty( config.getEmailDomain() ) )
        {
            ValidationMessage msg = new ValidationMessage( "emailDomain", "Email domain cannot be empty." );
            response.addValidationError( msg );
        }
        
        if ( StringUtils.isEmpty( config.getDefaultRole() ) )
        {
            ValidationMessage msg = new ValidationMessage( "defaultRole", "Default role cannot be empty." );
            response.addValidationError( msg );
        }
        else
        {
            // check that this is a valid role
            try
            {
                this.securitySystem.getAuthorizationManager( "default" ).getRole( config.getDefaultRole(), "default" );
            }
            catch ( Exception e )
            {
                logger.debug( "Failed to find role: "+ config.getDefaultRole() + " durring validation.", e );
                ValidationMessage msg = new ValidationMessage( "defaultRole", "Failed to find role." );
                response.addValidationError( msg );
            }
        }


        return response;
    }
    
    private File getConfigFile()
    {
        return configurationFile;
    }
}
