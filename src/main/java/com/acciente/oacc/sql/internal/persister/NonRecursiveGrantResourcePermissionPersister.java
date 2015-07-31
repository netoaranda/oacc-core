/*
 * Copyright 2009-2015, Acciente LLC
 *
 * Acciente LLC licenses this file to you under the
 * Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.acciente.oacc.sql.internal.persister;

import com.acciente.oacc.Resource;
import com.acciente.oacc.ResourcePermission;
import com.acciente.oacc.ResourcePermissions;
import com.acciente.oacc.sql.internal.persister.id.DomainId;
import com.acciente.oacc.sql.internal.persister.id.Id;
import com.acciente.oacc.sql.internal.persister.id.ResourceClassId;
import com.acciente.oacc.sql.internal.persister.id.ResourceId;
import com.acciente.oacc.sql.internal.persister.id.ResourcePermissionId;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class NonRecursiveGrantResourcePermissionPersister extends CommonGrantResourcePermissionPersister {
   public NonRecursiveGrantResourcePermissionPersister(SQLStrings sqlStrings) {
      super(sqlStrings);
   }

   @Override
   public Set<Resource> getResourcesByResourcePermission(SQLConnection connection,
                                                         Resource accessorResource,
                                                         Id<ResourceClassId> resourceClassId,
                                                         ResourcePermission resourcePermission,
                                                         Id<ResourcePermissionId> resourcePermissionId) {
      if (resourcePermission.isSystemPermission()) {
         throw new IllegalArgumentException("Permission: " + resourcePermission + " is not a non-system permission");
      }

      SQLStatement statement = null;
      try {
         // first get all the resources from which the accessor inherits any permissions
         final Set<Id<ResourceId>> accessorResourceIds
               = NonRecursivePersisterHelper.getInheritedAccessorResourceIds(sqlStrings, connection, accessorResource);

         // now accumulate the objects of the specified type that each (inherited) accessor has the specified permission to
         SQLResult resultSet;
         Set<Resource> resources = new HashSet<>();
         statement = connection.prepareStatement(sqlStrings.SQL_findInGrantResourcePermission_withoutInheritance_ResourceID_BY_AccessorID_ResourceClassID_PermissionID_IsWithGrant);

         for (Id<ResourceId> accessorResourceId : accessorResourceIds) {
            statement.setResourceId(1, accessorResourceId);
            statement.setResourceClassId(2, resourceClassId);
            statement.setResourcePermissionId(3, resourcePermissionId);
            statement.setBoolean(4, resourcePermission.isWithGrant());
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
               resources.add(resultSet.getResource("ResourceId"));
            }
            resultSet.close();
         }
         return resources;

      }
      catch (SQLException e) {
         throw new RuntimeException(e);
      }
      finally {
         closeStatement(statement);
      }
   }

   @Override
   public Set<Resource> getResourcesByResourcePermission(SQLConnection connection,
                                                         Resource accessorResource,
                                                         Id<ResourceClassId> resourceClassId,
                                                         Id<DomainId> resourceDomainId,
                                                         ResourcePermission resourcePermission,
                                                         Id<ResourcePermissionId> resourcePermissionId) {
      if (resourcePermission.isSystemPermission()) {
         throw new IllegalArgumentException("Permission: " + resourcePermission + " is not a non-system permission");
      }

      SQLStatement statement = null;
      try {
         // first get all the resources from which the accessor inherits any permissions
         final Set<Id<ResourceId>> accessorResourceIds
               = NonRecursivePersisterHelper.getInheritedAccessorResourceIds(sqlStrings, connection, accessorResource);

         // then get all the descendants of the specified domain
         final Set<Id<DomainId>> descendantDomainIds
               = NonRecursivePersisterHelper.getDescendantDomainIds(sqlStrings, connection, resourceDomainId);

         // now accumulate the objects of the specified type that each (inherited) accessor
         // has the specified permission to in each of the descendant domains
         SQLResult resultSet;
         Set<Resource> resources = new HashSet<>();
         statement = connection.prepareStatement(sqlStrings.SQL_findInGrantResourcePermission_ResourceID_BY_AccessorID_DomainID_ResourceClassID_PermissionID_IsWithGrant);

         for (Id<ResourceId> accessorResourceId : accessorResourceIds) {
            for (Id<DomainId> descendantDomainId : descendantDomainIds) {
               statement.setResourceId(1, accessorResourceId);
               statement.setResourceDomainId(2, descendantDomainId);
               statement.setResourceClassId(3, resourceClassId);
               statement.setResourcePermissionId(4, resourcePermissionId);
               statement.setBoolean(5, resourcePermission.isWithGrant());
               resultSet = statement.executeQuery();

               while (resultSet.next()) {
                  resources.add(resultSet.getResource("ResourceId"));
               }
               resultSet.close();
            }
         }

         return resources;
      }
      catch (SQLException e) {
         throw new RuntimeException(e);
      }
      finally {
         closeStatement(statement);
      }
   }

   @Override
   public Set<ResourcePermission> getResourcePermissionsIncludeInherited(SQLConnection connection,
                                                                         Resource accessorResource,
                                                                         Resource accessedResource) {
      SQLStatement statement = null;
      try {
         // first get all the resources from which the accessor inherits any permissions
         final Set<Id<ResourceId>> accessorResourceIds
               = NonRecursivePersisterHelper.getInheritedAccessorResourceIds(sqlStrings, connection, accessorResource);

         // now accumulate the permissions on the accessed resource from each of the (inherited) accessors
         SQLResult resultSet;
         Set<ResourcePermission> resourcePermissions = new HashSet<>();
         statement = connection.prepareStatement(sqlStrings.SQL_findInGrantResourcePermission_withoutInheritance_ResourceClassName_PermissionName_IsWithGrant_BY_AccessorID_AccessedID);

         for (Id<ResourceId> accessorResourceId : accessorResourceIds) {
            statement.setResourceId(1, accessorResourceId);
            statement.setResourceId(2, accessedResource);
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
               resourcePermissions.add(ResourcePermissions.getInstance(
                     resultSet.getString("PermissionName"),
                     resultSet.getBoolean("IsWithGrant"),
                     0,
                     0 /* zero since domain level does not apply in context of direct permissions */));
            }
            resultSet.close();
         }

         return resourcePermissions;
      }
      catch (SQLException e) {
         throw new RuntimeException(e);
      }
      finally {
         closeStatement(statement);
      }
   }

}
