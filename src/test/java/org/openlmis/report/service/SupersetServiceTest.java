/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.report.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.report.exception.ServerException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@RunWith(MockitoJUnitRunner.class)
public class SupersetServiceTest {

  private static final String SUPERSET_URL = "http://superset:8088";
  private static final String ADMIN_USER = "admin";
  private static final String ADMIN_PASSWORD = "secret";
  private static final String EMBEDDED_UUID = "test-dashboard-uuid";
  private static final String USERNAME = "testuser";
  private static final String ACCESS_TOKEN = "mock-access-token";
  private static final String CSRF_TOKEN = "mock-csrf-token";
  private static final String GUEST_TOKEN = "mock-guest-token";
  private static final String LOGIN_URL =
      SUPERSET_URL + "/api/v1/security/login";
  private static final String CSRF_URL =
      SUPERSET_URL + "/api/v1/security/csrf_token/";
  private static final String GUEST_TOKEN_URL =
      SUPERSET_URL + "/api/v1/security/guest_token/";

  @Mock
  private RestTemplate restTemplate;

  private SupersetService supersetService;

  @Before
  public void setUp() {
    supersetService = new SupersetService();
    ReflectionTestUtils.setField(supersetService, "restTemplate", restTemplate);
    ReflectionTestUtils.setField(supersetService, "supersetUrl", SUPERSET_URL);
    ReflectionTestUtils.setField(supersetService, "adminUser", ADMIN_USER);
    ReflectionTestUtils.setField(supersetService, "adminPassword", ADMIN_PASSWORD);
    ReflectionTestUtils.setField(supersetService, "cachedAccessToken", null);
    ReflectionTestUtils.setField(supersetService, "tokenExpiresAt", 0L);
  }

  @Test
  public void shouldLoginAndReturnGuestToken() {
    // given
    mockLoginResponse();
    mockCsrfResponse(null);
    mockGuestTokenResponse();

    // when
    String token = supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);

    // then
    assertEquals(GUEST_TOKEN, token);
  }

  @Test
  public void shouldUseCachedTokenOnSecondCall() {
    // given
    mockLoginResponse();
    mockCsrfResponse(null);
    mockGuestTokenResponse();

    // when
    supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
    supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);

    // then - login is called only once, but CSRF and guest_token are called twice each
    verify(restTemplate, times(1)).exchange(
        eq(LOGIN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    );
    verify(restTemplate, times(2)).exchange(
        eq(CSRF_URL),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(Map.class)
    );
    verify(restTemplate, times(2)).exchange(
        eq(GUEST_TOKEN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    );
  }

  @Test
  public void shouldRetryOnUnauthorized() {
    // given
    mockLoginResponse();
    mockCsrfResponse(null);

    // First guest_token call throws 401, subsequent calls succeed
    Map<String, Object> guestTokenBody = new HashMap<>();
    guestTokenBody.put("token", GUEST_TOKEN);

    when(restTemplate.exchange(
        eq(GUEST_TOKEN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    ))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
        .thenReturn(new ResponseEntity<>(guestTokenBody, HttpStatus.OK));

    // when
    String token = supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);

    // then
    assertEquals(GUEST_TOKEN, token);
    // login should be called twice: once initially and once after 401 retry
    verify(restTemplate, times(2)).exchange(
        eq(LOGIN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    );
  }

  @Test
  public void shouldThrowWhenSupersetIsNotConfigured() {
    for (String field : new String[] {"supersetUrl", "adminUser", "adminPassword"}) {
      setUp();
      ReflectionTestUtils.setField(supersetService, field, "");
      ServerException thrown = null;
      try {
        supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
      } catch (ServerException ex) {
        thrown = ex;
      }
      if (thrown == null) {
        fail("Expected ServerException when " + field + " is empty");
      }
    }
  }

  @Test(expected = ServerException.class)
  public void shouldThrowOnNullLoginResponse() {
    // given
    when(restTemplate.exchange(
        eq(LOGIN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

    // when
    supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
  }

  @Test(expected = ServerException.class)
  public void shouldThrowOnNullGuestTokenResponse() {
    // given
    mockLoginResponse();
    mockCsrfResponse(null);

    when(restTemplate.exchange(
        eq(GUEST_TOKEN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

    // when
    supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldExtractCookiesFromCsrfResponse() {
    // given
    mockLoginResponse();
    mockCsrfResponse(Arrays.asList("session=abc123; Path=/; HttpOnly", "csrftoken=xyz; Path=/"));
    mockGuestTokenResponse();

    // when
    supersetService.getGuestToken(EMBEDDED_UUID, USERNAME, USERNAME, USERNAME);

    // then - verify the guest token request includes cookies
    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate).exchange(
        eq(GUEST_TOKEN_URL),
        eq(HttpMethod.POST),
        captor.capture(),
        eq(Map.class)
    );

    HttpEntity capturedRequest = captor.getValue();
    String cookieHeader = capturedRequest.getHeaders().getFirst("Cookie");
    assertEquals("session=abc123; csrftoken=xyz", cookieHeader);
  }

  private void mockLoginResponse() {
    Map<String, Object> loginBody = new HashMap<>();
    loginBody.put("access_token", ACCESS_TOKEN);

    when(restTemplate.exchange(
        eq(LOGIN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(new ResponseEntity<>(loginBody, HttpStatus.OK));
  }

  @SuppressWarnings("unchecked")
  private void mockCsrfResponse(java.util.List<String> setCookies) {
    Map<String, Object> csrfBody = new HashMap<>();
    csrfBody.put("result", CSRF_TOKEN);

    HttpHeaders responseHeaders = new HttpHeaders();
    if (setCookies != null) {
      for (String cookie : setCookies) {
        responseHeaders.add(HttpHeaders.SET_COOKIE, cookie);
      }
    }

    when(restTemplate.exchange(
        eq(CSRF_URL),
        eq(HttpMethod.GET),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(new ResponseEntity<>(csrfBody, responseHeaders, HttpStatus.OK));
  }

  private void mockGuestTokenResponse() {
    Map<String, Object> guestTokenBody = new HashMap<>();
    guestTokenBody.put("token", GUEST_TOKEN);

    when(restTemplate.exchange(
        eq(GUEST_TOKEN_URL),
        eq(HttpMethod.POST),
        any(HttpEntity.class),
        eq(Map.class)
    )).thenReturn(new ResponseEntity<>(guestTokenBody, HttpStatus.OK));
  }
}
