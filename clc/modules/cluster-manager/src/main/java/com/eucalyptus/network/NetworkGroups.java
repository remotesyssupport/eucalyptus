/*******************************************************************************
 * Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */

package com.eucalyptus.network;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.EntityTransaction;
import org.apache.log4j.Logger;
import org.hibernate.exception.ConstraintViolationException;
import com.eucalyptus.cloud.util.DuplicateMetadataException;
import com.eucalyptus.cloud.util.MetadataException;
import com.eucalyptus.cloud.util.NoSuchMetadataException;
import com.eucalyptus.cluster.ClusterConfiguration;
import com.eucalyptus.configurable.ConfigurableClass;
import com.eucalyptus.configurable.ConfigurableField;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.PersistenceExceptions;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.entities.Transactions;
import com.eucalyptus.records.Logs;
import com.eucalyptus.util.Callback;
import com.eucalyptus.util.Numbers;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.TypeMapper;
import com.eucalyptus.util.TypeMappers;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import edu.ucsb.eucalyptus.msgs.IpPermissionType;
import edu.ucsb.eucalyptus.msgs.SecurityGroupItemType;
import edu.ucsb.eucalyptus.msgs.UserIdGroupPairType;

@ConfigurableClass( root = "net", description = "Default values used to bootstrap networking state discovery." )
public class NetworkGroups {
  private static final String DEFAULT_NETWORK_NAME      = "default";
  private static Logger       LOG                       = Logger.getLogger( NetworkGroups.class );
  private static String       NETWORK_DEFAULT_NAME      = "default";
  
  @ConfigurableField( initial = "" + 2048l, description = "Default max network index." )
  public static Long          DEFAULT_MAX_NETWORK_INDEX = 128l;
  @ConfigurableField( initial = "" + 9, description = "Default min network index." )
  public static Long          DEFAULT_MIN_NETWORK_INDEX = 9l;
  @ConfigurableField( initial = "" + 4096, description = "Default max vlan tag." )
  public static Integer       DEFAULT_MAX_NETWORK_TAG   = 4096;
  @ConfigurableField( initial = "" + 9, description = "Default min vlan tag." )
  public static Integer       DEFAULT_MIN_NETWORK_TAG   = 9;
  
  public static class NetworkRangeConfiguration {
    private Boolean useNetworkTags  = Boolean.TRUE;
    private Integer minNetworkTag   = DEFAULT_MIN_NETWORK_TAG;
    private Integer maxNetworkTag   = DEFAULT_MAX_NETWORK_TAG;
    private Long    minNetworkIndex = DEFAULT_MIN_NETWORK_INDEX;
    private Long    maxNetworkIndex = DEFAULT_MAX_NETWORK_INDEX;
    
    public Boolean hasNetworking( ) {
      return this.useNetworkTags;
    }
    
    public Boolean getUseNetworkTags( ) {
      return this.useNetworkTags;
    }
    
    public void setUseNetworkTags( Boolean useNetworkTags ) {
      this.useNetworkTags = useNetworkTags;
    }
    
    public Integer getMinNetworkTag( ) {
      return this.minNetworkTag;
    }
    
    public void setMinNetworkTag( Integer minNetworkTag ) {
      this.minNetworkTag = minNetworkTag;
    }
    
    public Integer getMaxNetworkTag( ) {
      return this.maxNetworkTag;
    }
    
    public void setMaxNetworkTag( Integer maxNetworkTag ) {
      this.maxNetworkTag = maxNetworkTag;
    }
    
    public Long getMaxNetworkIndex( ) {
      return this.maxNetworkIndex;
    }
    
    public void setMaxNetworkIndex( Long maxNetworkIndex ) {
      this.maxNetworkIndex = maxNetworkIndex;
    }
    
    public Long getMinNetworkIndex( ) {
      return this.minNetworkIndex;
    }
    
    public void setMinNetworkIndex( Long minNetworkIndex ) {
      this.minNetworkIndex = minNetworkIndex;
    }
    
  }
  
  static final NetworkRangeConfiguration netConfig = new NetworkRangeConfiguration( );
  
  public static synchronized void updateNetworkRangeConfiguration( ) {
    final AtomicBoolean netTagging = new AtomicBoolean( true );
    try {
      Transactions.each( new ClusterConfiguration( ), new Callback<ClusterConfiguration>( ) {
        
        @Override
        public void fire( ClusterConfiguration input ) {
          netConfig.setUseNetworkTags( netTagging.compareAndSet( true, input.getUseNetworkTags( ) ) );
          
          netConfig.setMinNetworkTag( Ints.max( netConfig.getMinNetworkTag( ), input.getMinNetworkTag( ) ) );
          netConfig.setMaxNetworkTag( Ints.min( netConfig.getMaxNetworkTag( ), input.getMaxNetworkTag( ) ) );
          
          netConfig.setMinNetworkIndex( Longs.max( netConfig.getMinNetworkIndex( ), input.getMinNetworkIndex( ) ) );
          netConfig.setMaxNetworkIndex( Longs.min( netConfig.getMaxNetworkIndex( ), input.getMaxNetworkIndex( ) ) );
          
        }
      } );
    } catch ( TransactionException ex ) {
      Logs.extreme( ).error( ex, ex );
    }
  }
  
  public static List<Long> networkIndexInterval( ) {
    List<Long> interval = Lists.newArrayList( );
    for ( Long i = NetworkGroups.networkingConfiguration( ).getMinNetworkIndex( ); i < NetworkGroups.networkingConfiguration( ).getMaxNetworkIndex( ); i++ ) {
      interval.add( i );
    }
//    Collections.shuffle( interval );
    return interval;
  }
  
  public static List<Integer> networkTagInterval( ) {
    List<Integer> interval = Lists.newArrayList( );
    for ( int i = NetworkGroups.networkingConfiguration( ).getMinNetworkTag( ); i < NetworkGroups.networkingConfiguration( ).getMaxNetworkTag( ); i++ ) {
      interval.add( i );
    }
//    Collections.shuffle( interval );
    return interval;
  }
  
  public static NetworkRangeConfiguration networkingConfiguration( ) {
    return netConfig;
  }
  
  public static NetworkGroup lookup( final String uuid ) throws NoSuchMetadataException {
    try {
      return Transactions.find( new NetworkGroup( ) {
        {
          this.setNaturalId( uuid );
        }
      } );
    } catch ( Exception ex ) {
      throw new NoSuchMetadataException( "Failed to find security group: " + uuid, ex );
    }
  }
  
  public static NetworkGroup lookup( OwnerFullName ownerFullName, String groupName ) throws MetadataException {
    if ( defaultNetworkName( ).equals( groupName ) ) {
      return createDefault( ownerFullName );
    } else {
      try {
        return Transactions.find( new NetworkGroup( ownerFullName, groupName ) );
      } catch ( Exception ex ) {
        throw new NoSuchMetadataException( "Failed to find security group: " + groupName + " for " + ownerFullName, ex );
      }
    }
  }
  
  public static List<NetworkGroup> lookupAll( OwnerFullName ownerFullName, String groupNamePattern ) throws MetadataException {
    if ( defaultNetworkName( ).equals( groupNamePattern ) ) {
      createDefault( ownerFullName );
    } else {
      
      EntityTransaction db = Entities.get( NetworkGroups.class );
      try {
        List<NetworkGroup> results = Entities.query( new NetworkGroup( ownerFullName, groupNamePattern ) );
        List<NetworkGroup> ret = Lists.newArrayList( results );
        db.commit( );
        return ret;
      } catch ( Exception ex ) {
        Logs.exhaust( ).error( ex, ex );
        db.rollback( );
        throw new NoSuchMetadataException( "Failed to find security group: " + groupNamePattern + " for " + ownerFullName, ex );
      }
    }
    try {
      return Transactions.findAll( new NetworkGroup( ownerFullName, groupNamePattern ) );
    } catch ( Exception ex ) {
      throw new NoSuchMetadataException( "Failed to find security group: " + groupNamePattern + " for " + ownerFullName, ex );
    }
  }
  
  static NetworkGroup createDefault( OwnerFullName ownerFullName ) throws MetadataException {
    EntityTransaction db = Entities.get( NetworkGroup.class );
    try {
      NetworkGroup defaultNet = new NetworkGroup( ownerFullName, NETWORK_DEFAULT_NAME, "default group" );
      NetworkGroup entity = Entities.merge( defaultNet );
      db.commit( );
      return entity;
    } catch ( ConstraintViolationException ex ) {
      Logs.exhaust( ).error( ex );
      db.rollback( );
      throw new DuplicateMetadataException( "Failed to create default group: " + ownerFullName.toString( ), ex );
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      db.rollback( );
      throw new RuntimeException( "Failed to create default group: " + ownerFullName.toString( ), ex );
    }
  }
  
  public static String defaultNetworkName( ) {
    return DEFAULT_NETWORK_NAME;
  }
  
  public static NetworkGroup create( OwnerFullName ownerFullName, String groupName, String groupDescription ) throws MetadataException {
    EntityTransaction db = Entities.get( NetworkGroup.class );
    try {
      NetworkGroup entity = Entities.merge( new NetworkGroup( ownerFullName, NETWORK_DEFAULT_NAME ) );
      db.commit( );
      return entity;
    } catch ( ConstraintViolationException ex ) {
      Logs.exhaust( ).error( ex );
      db.rollback( );
      throw new DuplicateMetadataException( "Failed to create default group: " + ownerFullName.toString( ), ex );
    } catch ( Exception ex ) {
      Logs.exhaust( ).error( ex, ex );
      db.rollback( );
      throw new MetadataException( "Failed to create default group: " + ownerFullName.toString( ), PersistenceExceptions.transform( ex ) );
    }
  }
  
  @TypeMapper
  public enum NetworkPeerAsUserIdGroupPairType implements Function<NetworkPeer, UserIdGroupPairType> {
    INSTANCE;
    
    @Override
    public UserIdGroupPairType apply( NetworkPeer peer ) {
      return new UserIdGroupPairType( peer.getUserQueryKey( ), peer.getGroupName( ) );
    }
  }
  
  @TypeMapper
  public enum IpRangeAsString implements Function<IpRange, String> {
    INSTANCE;
    
    @Override
    public String apply( IpRange range ) {
      return range.getValue( );
    }
  }
  
  @TypeMapper
  public enum NetworkRuleAsIpPerm implements Function<NetworkRule, IpPermissionType> {
    INSTANCE;
    
    @Override
    public IpPermissionType apply( NetworkRule rule ) {
      IpPermissionType ipPerm = new IpPermissionType( rule.getProtocol( ), rule.getLowPort( ), rule.getHighPort( ) );
      Iterable<UserIdGroupPairType> peers = Iterables.transform( rule.getNetworkPeers( ), TypeMappers.lookup( NetworkPeer.class, UserIdGroupPairType.class ) );
      Iterables.addAll( ipPerm.getGroups( ), peers );
      Iterable<String> ipRanges = Iterables.transform( rule.getIpRanges( ), TypeMappers.lookup( IpRange.class, String.class ) );
      Iterables.addAll( ipPerm.getIpRanges( ), ipRanges );
      return ipPerm;
    }
  }
  
  @TypeMapper
  public enum NetworkGroupAsSecurityGroupItem implements Function<NetworkGroup, SecurityGroupItemType> {
    INSTANCE;
    @Override
    public SecurityGroupItemType apply( NetworkGroup input ) {
      EntityTransaction db = Entities.get( NetworkGroup.class );
      try {
        NetworkGroup netGroup = Entities.merge( input );
        SecurityGroupItemType groupInfo = new SecurityGroupItemType( netGroup.getOwnerAccountNumber( ), netGroup.getDisplayName( ), netGroup.getDescription( ) );
        Iterable<IpPermissionType> ipPerms = Iterables.transform( netGroup.getNetworkRules( ), TypeMappers.lookup( NetworkRule.class, IpPermissionType.class ) );
        Iterables.addAll( groupInfo.getIpPermissions( ), ipPerms );
        return groupInfo;
      } finally {
        db.rollback( );
      }
    }
    
  }
  
  public static List<NetworkGroup> userNetworkGroups( OwnerFullName owner ) throws MetadataException {
    return lookupAll( owner, null );
  }
}
