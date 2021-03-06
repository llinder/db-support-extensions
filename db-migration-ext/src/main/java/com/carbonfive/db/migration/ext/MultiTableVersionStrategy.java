package com.carbonfive.db.migration.ext;

import static com.carbonfive.db.jdbc.DatabaseType.HSQL;
import static com.carbonfive.db.jdbc.DatabaseType.SQL_SERVER;
import static java.lang.String.format;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections.map.DefaultedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carbonfive.db.jdbc.DatabaseType;
import com.carbonfive.db.migration.MigrationException;
import com.carbonfive.db.migration.VersionStrategy;

public class MultiTableVersionStrategy
    implements VersionStrategy
{

    public static final String DEFAULT_VERSION_TABLE = "schema_version";

    public static final String DEFAULT_VERSION_COLUMN = "version";

    public static final String DEFAULT_APPLIED_DATE_COLUMN = "applied_on";

    public static final String DEFAULT_DURATION_COLUMN = "duration";

    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    private String versionTable = DEFAULT_VERSION_TABLE;

    private String versionColumn = DEFAULT_VERSION_COLUMN;

    private String appliedDateColumn = DEFAULT_APPLIED_DATE_COLUMN;

    private String durationColumn = DEFAULT_DURATION_COLUMN;

    private String prefix;

    private static final DefaultedMap enableVersioningDDL;

    static
    {
        enableVersioningDDL =
            new DefaultedMap(
                              "create table %s (%s varchar(32) not null unique, %s timestamp not null, %s int not null)" );
        enableVersioningDDL.put( HSQL,
                                 "create table %s (%s varchar not null, %s datetime not null, %s int not null, constraint %1$s_%2$s_unique unique (%2$s))" );
        enableVersioningDDL.put( SQL_SERVER,
                                 "create table %s (%s varchar(32) not null unique, %s datetime not null, %s int not null)" );
    }

    public boolean isEnabled( DatabaseType dbType, Connection connection )
    {
        try
        {
            connection.createStatement().executeQuery( "select count(*) from " + getVersionTable() );
        }
        catch ( SQLException e )
        {
            return false;
        }

        return true;
    }

    public void enableVersioning( DatabaseType dbType, Connection connection )
    {
        try
        {
            String ddl =
                format( (String) enableVersioningDDL.get( dbType ), getVersionTable(), versionColumn, appliedDateColumn,
                        durationColumn );
            connection.createStatement().executeUpdate( ddl );
        }
        catch ( SQLException e )
        {
            throw new MigrationException( "Could not create version-tracking table '" + getVersionTable() + "'.", e );
        }
    }

    public Set<String> appliedMigrations( DatabaseType dbType, Connection connection )
    {
        // Make sure migrations is enabled.
        if ( !isEnabled( dbType, connection ) )
        {
            return null;
        }

        Set<String> migrations = new HashSet<String>();

        try
        {
            ResultSet rs =
                connection.createStatement().executeQuery( "select " + versionColumn + " from " + getVersionTable() );
            while ( rs.next() )
            {
                migrations.add( rs.getString( versionColumn ) );
            }
        }
        catch ( SQLException e )
        {
            throw new MigrationException( e );
        }

        return migrations;
    }

    public void recordMigration( DatabaseType dbType, Connection connection, String version, Date startTime,
                                 long duration )
    {
        try
        {
            PreparedStatement statement =
                connection.prepareStatement( "insert into " + getVersionTable() + " values (?, ?, ?)" );
            statement.setString( 1, version );
            statement.setTimestamp( 2, new Timestamp( startTime.getTime() ) );
            statement.setLong( 3, duration );
            statement.execute();
        }
        catch ( SQLException e )
        {
            throw new MigrationException( e );
        }
    }

    protected String getVersionTable()
    {
        return ( prefix != null ) ? prefix + "_" + versionTable : versionTable;
    }

    public void setVersionTable( String versionTable )
    {
        this.versionTable = versionTable;
    }

    public void setVersionColumn( String versionColumn )
    {
        this.versionColumn = versionColumn;
    }

    public void setAppliedDateColumn( String appliedDateColumn )
    {
        this.appliedDateColumn = appliedDateColumn;
    }

    public void setDurationColumn( String durationColumn )
    {
        this.durationColumn = durationColumn;
    }

    public void setPrefix( String prefix )
    {
        this.prefix = prefix;
    }

}
