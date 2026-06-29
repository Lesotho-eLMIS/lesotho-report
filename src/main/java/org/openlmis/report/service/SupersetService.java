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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openlmis.report.exception.ServerException;
import org.openlmis.report.i18n.SupersetMessageKeys;
import org.openlmis.report.utils.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Service for communicating with the Superset API to obtain guest tokens
 * for embedded dashboard access.
 */
@Service
public class SupersetService {

  @Value("${superset.url:}")
  private String supersetUrl;

  @Value("${superset.admin.user:}")
  private String adminUser;

  @Value("${superset.admin.password:}")
  private String adminPassword;

  private final RestTemplate restTemplate = new RestTemplate();

  private String cachedAccessToken;
  private long tokenExpiresAt;

  /**
   * Obtain a Superset guest token for the given embedded dashboard UUID.
   *
   * @param embeddedUuid the embedded UUID of the Superset dashboard
   * @param username     the username for the guest user
   * @param firstName    the first name for the guest user
   * @param lastName     the last name for the guest user
   * @return the guest token string
   */
  public String getGuestToken(String embeddedUuid, String username,
                              String firstName, String lastName) {
    if (supersetUrl.isEmpty() || adminUser.isEmpty() || adminPassword.isEmpty()) {
      throw new ServerException(new Message(SupersetMessageKeys.ERROR_SUPERSET_NOT_CONFIGURED));
    }
    try {
      return requestGuestToken(embeddedUuid, username, firstName, lastName);
    } catch (HttpClientErrorException ex) {
      if (ex.getStatusCode().value() == 401) {
        clearCachedToken();
        return requestGuestToken(embeddedUuid, username, firstName, lastName);
      }
      throw ex;
    }
  }

  private String requestGuestToken(String embeddedUuid, String username,
                                   String firstName, String lastName) {
    String accessToken = getAccessToken();

    // Get CSRF token and session cookies
    CsrfResult csrf = getCsrfToken(accessToken);

    // Build guest token request
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + accessToken);
    headers.set("X-CSRFToken", csrf.token);
    headers.set("Referer", supersetUrl + "/");
    if (csrf.cookies != null) {
      headers.set("Cookie", csrf.cookies);
    }

    Map<String, Object> user = new HashMap<>();
    user.put("username", username);
    user.put("first_name", firstName);
    user.put("last_name", lastName);

    Map<String, Object> resource = new HashMap<>();
    resource.put("type", "dashboard");
    resource.put("id", embeddedUuid);

    Map<String, Object> body = new HashMap<>();
    body.put("user", user);
    body.put("resources", Collections.singletonList(resource));
    body.put("rls", Collections.emptyList());

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

    ResponseEntity<Map> response = restTemplate.exchange(
        supersetUrl + "/api/v1/security/guest_token/",
        HttpMethod.POST,
        request,
        Map.class
    );

    Map responseBody = response.getBody();
    if (responseBody == null || !responseBody.containsKey("token")) {
      throw new ServerException(new Message(SupersetMessageKeys.ERROR_SUPERSET_GUEST_TOKEN_FAILED));
    }
    return (String) responseBody.get("token");
  }

  private synchronized String getAccessToken() {
    if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
      return cachedAccessToken;
    }
    return login();
  }

  private String login() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, String> body = new HashMap<>();
    body.put("username", adminUser);
    body.put("password", adminPassword);
    body.put("provider", "db");
    body.put("refresh", "true");

    HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

    ResponseEntity<Map> response = restTemplate.exchange(
        supersetUrl + "/api/v1/security/login",
        HttpMethod.POST,
        request,
        Map.class
    );

    Map loginResponse = response.getBody();
    if (loginResponse == null || !loginResponse.containsKey("access_token")) {
      throw new ServerException(new Message(SupersetMessageKeys.ERROR_SUPERSET_LOGIN_FAILED));
    }
    cachedAccessToken = (String) loginResponse.get("access_token");
    // Cache token for 4 minutes (Superset default expiry is 5 minutes)
    tokenExpiresAt = System.currentTimeMillis() + (4 * 60 * 1000);

    return cachedAccessToken;
  }

  private CsrfResult getCsrfToken(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);

    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<Map> response = restTemplate.exchange(
        supersetUrl + "/api/v1/security/csrf_token/",
        HttpMethod.GET,
        request,
        Map.class
    );

    Map csrfBody = response.getBody();
    if (csrfBody == null || !csrfBody.containsKey("result")) {
      throw new ServerException(new Message(SupersetMessageKeys.ERROR_SUPERSET_CSRF_FAILED));
    }
    String csrfToken = (String) csrfBody.get("result");

    // Extract Set-Cookie headers to send back with the guest token request
    List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    String cookies = null;
    if (setCookies != null && !setCookies.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < setCookies.size(); i++) {
        if (i > 0) {
          sb.append("; ");
        }
        // Extract just the cookie name=value part (before any ;)
        String cookie = setCookies.get(i);
        int semicolonIdx = cookie.indexOf(';');
        if (semicolonIdx > 0) {
          sb.append(cookie.substring(0, semicolonIdx));
        } else {
          sb.append(cookie);
        }
      }
      cookies = sb.toString();
    }

    return new CsrfResult(csrfToken, cookies);
  }

  private synchronized void clearCachedToken() {
    cachedAccessToken = null;
    tokenExpiresAt = 0;
  }

  /**
   * Simple holder for CSRF token and associated session cookies.
   */
  private static class CsrfResult {
    final String token;
    final String cookies;

    CsrfResult(String token, String cookies) {
      this.token = token;
      this.cookies = cookies;
    }
  }
}
