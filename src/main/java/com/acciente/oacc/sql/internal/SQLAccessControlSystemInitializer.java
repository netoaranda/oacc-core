/*
 * Copyright 2009-2014, Acciente LLC
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
package com.acciente.oacc.sql.internal;

import com.acciente.oacc.AccessControlContext;
import com.acciente.oacc.DomainCreatePermission;
import com.acciente.oacc.DomainPermission;
import com.acciente.oacc.Resources;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLAccessControlSystemInitializer {
   public static void initializeOACC(Connection connection,
                                     String dbSchema,
                                     char[] oaccRootPwd) throws SQLException {
      System.out.println("Initializing password encryptor...");
      CleanablePasswordEncryptor passwordEncryptor = new StrongCleanablePasswordEncryptor();

      PreparedStatement statement;
      ResultSet resultSet;

      final String schemaNameAndTablePrefix = dbSchema != null ? dbSchema + ".OAC_" : "OAC_";
      
      System.out.println("Checking database...needs empty tables");

      // create an initial domain to hold the system user
      statement = connection.prepareStatement("SELECT  DomainId FROM " + schemaNameAndTablePrefix + "Domain WHERE DomainId = 0");
      resultSet = statement.executeQuery();

      if (resultSet.next()) {
         System.out.println("Cannot initialize, likely that this OACC is already initialized! (check: found a system domain)");
         resultSet.close();
         return;
      }

      System.out.println("Initializing database...assuming empty tables (will fail safely if tables have data)");

      // create an initial domain to hold the system user
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "Domain( DomainId, DomainName ) VALUES ( 0, ? )");
      statement.setString(1, AccessControlContext.SYSTEM_DOMAIN);
      statement.executeUpdate();

      // create a resource type for the system user
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "ResourceClass( ResourceClassId, ResourceClassName, IsAuthenticatable, IsUnauthenticatedCreateAllowed ) VALUES ( 0, ?, 1, 0 )");
      statement.setString(1, AccessControlContext.SYSTEM_RESOURCE_CLASS);
      statement.executeUpdate();

      // create the system user
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "Resource( ResourceId, ResourceClassId, DomainId ) VALUES ( 0, 0, 0 )");
      statement.executeUpdate();

      // set the system user's password
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "ResourcePassword( ResourceId, Password ) VALUES ( 0, ? )");
      char[] boundPassword = null;
      try {
         boundPassword = PasswordUtils.computeBoundPassword(Resources.getInstance(0), oaccRootPwd);
         statement.setString(1, passwordEncryptor.encryptPassword(boundPassword));
         statement.executeUpdate();
      }
      finally {
         PasswordUtils.cleanPassword(boundPassword);
      }

      // grant the system user [super user w/ grant] to the system domain
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "Grant_DomPerm_Sys( AccessorResourceId, GrantorResourceId, AccessedDomainId, SysPermissionId, IsWithGrant )"
                                                    + " VALUES ( 0, 0, 0, ?, 1 )");
      statement.setLong(1, DomainPermission.getInstance(DomainPermission.SUPER_USER).getSystemPermissionId());
      statement.executeUpdate();

      // grant the system user [create w/ grant], and [super user w/ grant] to any domains it creates
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "Grant_DomCrPerm_Sys( AccessorResourceId, GrantorResourceId, SysPermissionId, IsWithGrant )"
                                                    + " VALUES ( 0, 0, ?, 1 )");
      statement.setLong(1, DomainCreatePermission.getInstance(DomainCreatePermission.CREATE).getSystemPermissionId());
      statement.executeUpdate();
      statement = connection.prepareStatement("INSERT INTO " + schemaNameAndTablePrefix + "Grant_DomCrPerm_PostCr_Sys( AccessorResourceId, GrantorResourceId, PostCreateSysPermissionId, PostCreateIsWithGrant, IsWithGrant )"
                                                    + " VALUES ( 0, 0, ?, 1, 1 )");
      statement.setLong(1, DomainPermission.getInstance(DomainPermission.SUPER_USER).getSystemPermissionId());
      statement.executeUpdate();
      statement.setLong(1, DomainPermission.getInstance(DomainPermission.CREATE_CHILD_DOMAIN).getSystemPermissionId());
      statement.executeUpdate();
   }
}
