/*
 * Copyright 2009-2016, Acciente LLC
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
package com.acciente.oacc;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class TestAccessControl_getSessionResource extends TestAccessControlBase {
   @Test
   public void getSessionResource_authenticated_asSystemResource() {
      authenticateSystemResource();

      // verify
      final Resource authenticatedResource = accessControlContext.getSessionResource();
      assertThat(authenticatedResource, is(SYS_RESOURCE));
   }

   @Test
   public void getSessionResource_authenticated_asNonSystemResource() {
      // set up
      final char[] password = generateUniquePassword();
      final Resource authenticatableResource = generateAuthenticatableResource(password);

      // authenticate
      accessControlContext.authenticate(authenticatableResource, PasswordCredentials.newInstance(password));

      // verify
      final Resource authenticatedResource = accessControlContext.getSessionResource();
      assertThat(authenticatedResource, is(authenticatableResource));
   }

   @Test
   public void getSessionResource_impersonated() {
      authenticateSystemResource();

      final char[] password = generateUniquePassword();
      final Resource accessorResource = generateAuthenticatableResource(password);
      final Resource impersonatedResource = generateAuthenticatableResource(generateUniquePassword());

      // set up permission: accessor --IMPERSONATE-> impersonated
      accessControlContext.setResourcePermissions(accessorResource,
                                                  impersonatedResource,
                                                  setOf(ResourcePermissions.getInstance(ResourcePermissions.IMPERSONATE)));

      // authenticate & impersonate
      accessControlContext.authenticate(accessorResource, PasswordCredentials.newInstance(password));
      accessControlContext.impersonate(impersonatedResource);

      // verify
      final Resource authenticatedResource = accessControlContext.getSessionResource();
      assertThat(authenticatedResource, is(impersonatedResource));
   }

   @Test
   public void getSessionResource_withExtId() {
      final String externalId1 = generateUniqueExternalId();
      final String domainName = generateDomain();
      final String externalId2 = generateUniqueExternalId();
      final String resourceClassName = generateResourceClass(true, true);
      final String externalId3 = generateUniqueExternalId();
      final Credentials credentials = PasswordCredentials.newInstance(generateUniquePassword());

      // create resources with external id
      final Resource resource1
            = accessControlContext.createResource(resourceClassName, domainName, externalId1, credentials);
      assertThat(resource1, is(not(nullValue())));
      final Resource resource2
            = accessControlContext.createResource(resourceClassName, domainName, externalId2, credentials);
      assertThat(resource2, is(not(nullValue())));
      final Resource resource3
            = accessControlContext.createResource(resourceClassName, domainName, externalId3, credentials);
      assertThat(resource3, is(not(nullValue())));

      Resource sessionResource;

      // authenticate with external id and verify that the authenticated resource is fully resolved
      accessControlContext.authenticate(Resources.getInstance(resource1.getExternalId()), credentials);
      sessionResource = accessControlContext.getSessionResource();
      assertThat(sessionResource, is(resource1));
      assertThat(sessionResource.getId(), is(resource1.getId()));
      assertThat(sessionResource.getExternalId(), is(not(nullValue())));
      assertThat(sessionResource.getExternalId(), is(resource1.getExternalId()));

      // authenticate with resource id and external id and verify that the authenticated resource is fully resolved
      accessControlContext.authenticate(Resources.getInstance(resource2.getId(), resource2.getExternalId()), credentials);
      sessionResource = accessControlContext.getSessionResource();
      assertThat(sessionResource, is(resource2));
      assertThat(sessionResource.getId(), is(resource2.getId()));
      assertThat(sessionResource.getExternalId(), is(not(nullValue())));
      assertThat(sessionResource.getExternalId(), is(resource2.getExternalId()));

      // authenticate with resource id and verify that the authenticated resource is fully resolved
      accessControlContext.authenticate(Resources.getInstance(resource3.getId()), credentials);
      sessionResource = accessControlContext.getSessionResource();
      assertThat(sessionResource, is(resource3));
      assertThat(sessionResource.getId(), is(resource3.getId()));
      assertThat(sessionResource.getExternalId(), is(not(nullValue())));
      assertThat(sessionResource.getExternalId(), is(resource3.getExternalId()));
   }

   @Test
   public void getSessionResource_notAuthenticated_shouldFail() {
      try {
         accessControlContext.getSessionResource();
      }
      catch (NotAuthenticatedException e) {
         assertThat(e.getMessage().toLowerCase(), containsString("not authenticated"));
      }
   }
}
