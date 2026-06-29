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

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openlmis.report.i18n.AuthorizationMessageKeys.ERROR_RIGHT_NOT_FOUND;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_FILE_EMPTY;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_FILE_INCORRECT_TYPE;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_FILE_INVALID;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_FILE_MISSING;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_PARAMETER_INCORRECT_TYPE;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_PARAMETER_MISSING;
import static org.openlmis.report.i18n.ReportingMessageKeys.ERROR_REPORTING_TEMPLATE_EXIST;
import static org.openlmis.report.service.JasperTemplateService.REPORT_TYPE_PROPERTY;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPropertiesMap;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.util.JRLoader;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.openlmis.report.domain.JasperTemplate;
import org.openlmis.report.domain.JasperTemplateParameter;
import org.openlmis.report.domain.ReportCategory;
import org.openlmis.report.domain.ReportImage;
import org.openlmis.report.dto.external.referencedata.RightDto;
import org.openlmis.report.exception.JasperReportViewException;
import org.openlmis.report.exception.ReportingException;
import org.openlmis.report.exception.ValidationMessageException;
import org.openlmis.report.i18n.ReportImageMessageKeys;
import org.openlmis.report.repository.JasperTemplateRepository;
import org.openlmis.report.repository.ReportCategoryRepository;
import org.openlmis.report.repository.ReportImageRepository;
import org.openlmis.report.service.referencedata.RightReferenceDataService;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(BlockJUnit4ClassRunner.class)
@PrepareForTest({
    JasperTemplateService.class,
    JasperCompileManager.class,
    ResourceBundle.class,
    JRLoader.class,
    java.nio.file.Files.class
})
@SuppressWarnings("PMD.TooManyMethods")
public class JasperTemplateServiceTest {

  @Mock
  private JasperTemplateRepository jasperTemplateRepository;

  @Mock
  private RightReferenceDataService rightReferenceDataService;

  @Mock
  private ReportImageRepository reportImageRepository;

  @Mock
  private ReportCategoryRepository reportCategoryRepository;

  @InjectMocks
  private JasperTemplateService jasperTemplateService;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private static final String NAME_OF_FILE = "report.jrxml";
  private static final String DISPLAY_NAME = "displayName";
  private static final String CATEGORY_NAME = "categoryName";
  private static final String PARAM_DISPLAY_NAME = "Param Display Name";
  private static final String REQUIRED = "required";
  private static final String PARAM1 = "param1";
  private static final String PARAM2 = "param2";
  private static final String PARAM3 = "param3";
  private static final String PARAM4 = "param4";
  private static final String PARAM_NAME = "name";
  private static final String IMAGE_NAME = "image";
  private static final String RESOURCE_BUNDLE_NAME = "report_translations";
  private static final String RESOURCE_BUNDLE_CLASSPATH = "resourceBundles/report_translations";
  private static final String RESOURCE_BUNDLE_KEY = "resource_bundle_key";
  private static final String RESOURCE_BUNDLE_PATH = "/config/reports/resourceBundles";
  private static final String HEADER_PARAM_NAME = "headerTemplate";
  private static final String CONFIG_PATH_CONST = "/config/reports/";
  private static final String DUMMY_FILE_URI = "file://dummy";
  private static final String JRXML_EXTENSION = ".jrxml";
  private static final String HEADER_CONFIG_PROPERTIES = "/config/reports/header_config.properties";
  private static final String GLOBAL_HEADER_LANDSCAPE = "GlobalHeaderLandscape";
  private static final String GLOBAL_HEADER_PORTRAIT = "GlobalHeaderPortrait";
  private static final String DESC = "desc";
  private static final String USERS_MANAGE = "USERS_MANAGE";
  private static final String JAVA_LANG_STRING = "java.lang.String";
  private static final String DEFAULT_EXPR_TEXT = "text";
  
  private HttpServletRequest request;
  private JasperTemplate template;

  @Before
  public void setUp() {
    request = mock(HttpServletRequest.class);
    template = mock(JasperTemplate.class);
  }

  @Test
  public void shouldSaveValidNonExistentTemplate() throws Exception {
    ReportCategory reportCategory = new ReportCategory();
    reportCategory.setId(UUID.randomUUID());
    reportCategory.setName(CATEGORY_NAME);

    // given
    given(jasperTemplateRepository.findByName(anyString()))
        .willReturn(null);

    given(reportCategoryRepository.findByName(anyString()))
      .willReturn(Optional.of(reportCategory));

    // when
    testSaveTemplate();
  }

  @Test
  public void shouldUpdateValidExistentTemplate() throws Exception {
    // given
    ReportCategory reportCategory = new ReportCategory();
    reportCategory.setId(UUID.randomUUID());
    reportCategory.setName(CATEGORY_NAME);

    UUID oldId = UUID.randomUUID();

    JasperTemplate oldTemplate = new JasperTemplate();
    oldTemplate.setName(DISPLAY_NAME);
    oldTemplate.setId(oldId);
    oldTemplate.setRequiredRights(new ArrayList<>());
    oldTemplate.setCategory(reportCategory);

    given(jasperTemplateRepository.findByName(anyString()))
        .willReturn(oldTemplate);

    given(reportCategoryRepository.findByName(anyString()))
      .willReturn(Optional.ofNullable(oldTemplate.getCategory()));

    // when
    JasperTemplate template = testSaveTemplate();

    // then
    assertEquals(template.getId(), oldId);
  }

  @Test
  public void shouldThrowWhenTemplateExistsAndOverrideIsNull() throws Exception {
    expectedException.expect(ValidationMessageException.class);
    expectedException.expectMessage(ERROR_REPORTING_TEMPLATE_EXIST);

    ReportCategory reportCategory = new ReportCategory();
    reportCategory.setId(UUID.randomUUID());
    reportCategory.setName(CATEGORY_NAME);

    JasperTemplate existing = new JasperTemplate();
    existing.setName(DISPLAY_NAME);
    existing.setId(UUID.randomUUID());
    existing.setRequiredRights(new ArrayList<>());
    existing.setCategory(reportCategory);

    given(jasperTemplateRepository.findByName(anyString())).willReturn(existing);
    given(reportCategoryRepository.findByName(anyString())).willReturn(Optional.of(reportCategory));
    given(rightReferenceDataService.findRight(anyString())).willReturn(new RightDto());

    jasperTemplateService.saveTemplate(mock(MultipartFile.class), DISPLAY_NAME, DESC,
        Collections.singletonList(USERS_MANAGE), CATEGORY_NAME, null);
  }

  @Test
  public void shouldThrowWhenTemplateExistsAndOverrideIsFalse() throws Exception {
    expectedException.expect(ValidationMessageException.class);
    expectedException.expectMessage(ERROR_REPORTING_TEMPLATE_EXIST);

    ReportCategory reportCategory = new ReportCategory();
    reportCategory.setId(UUID.randomUUID());
    reportCategory.setName(CATEGORY_NAME);

    JasperTemplate existing = new JasperTemplate();
    existing.setName(DISPLAY_NAME);
    existing.setId(UUID.randomUUID());
    existing.setRequiredRights(new ArrayList<>());
    existing.setCategory(reportCategory);

    given(jasperTemplateRepository.findByName(anyString())).willReturn(existing);
    given(reportCategoryRepository.findByName(anyString())).willReturn(Optional.of(reportCategory));
    given(rightReferenceDataService.findRight(anyString())).willReturn(new RightDto());

    jasperTemplateService.saveTemplate(mock(MultipartFile.class), DISPLAY_NAME, DESC,
        Collections.singletonList(USERS_MANAGE), CATEGORY_NAME, false);
  }

  private JasperTemplate testSaveTemplate() throws ReportingException {
    JasperTemplateService service = spy(jasperTemplateService);
    MultipartFile file = mock(MultipartFile.class);
    String description = "description";
    List<String> requiredRights = Collections.singletonList(USERS_MANAGE);

    given(rightReferenceDataService.findRight(requiredRights.get(0)))
        .willReturn(new RightDto());

    // validating and saving file is checked by other tests
    doNothing().when(service)
        .validateFileAndSaveTemplate(any(JasperTemplate.class), eq(file));

    // when
    JasperTemplate resultTemplate = service.saveTemplate(file,
        JasperTemplateServiceTest.DISPLAY_NAME, description, requiredRights, CATEGORY_NAME, true);

    // then
    assertEquals(JasperTemplateServiceTest.DISPLAY_NAME, resultTemplate.getName());
    assertEquals(description, resultTemplate.getDescription());
    assertEquals(requiredRights, resultTemplate.getRequiredRights());

    return resultTemplate;
  }

  @Test
  public void shouldThrowErrorIfReportRequiredRightDoesNotExist() throws Exception {
    expectedException.expect(ValidationMessageException.class);
    expectedException.expectMessage(ERROR_RIGHT_NOT_FOUND);

    // given
    String rejectedRight = "REPORTS_DELETE";
    given(rightReferenceDataService.findRight(rejectedRight)).willReturn(null);

    // when
    jasperTemplateService.saveTemplate(null, null, null, Collections.singletonList(rejectedRight),
        null, false);
  }
  
  @Test
  public void shouldThrowErrorIfFileNotOfTypeJasperXml() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_INCORRECT_TYPE);

    MockMultipartFile file = new MockMultipartFile("report.pdf", new byte[1]);
    jasperTemplateService.validateFileAndInsertTemplate(new JasperTemplate(), file);
  }

  @Test
  public void shouldThrowErrorIfFileEmpty() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_EMPTY);
    MockMultipartFile file = new MockMultipartFile(
        NAME_OF_FILE, NAME_OF_FILE, "", new byte[0]);

    jasperTemplateService.validateFileAndInsertTemplate(new JasperTemplate(), file);
  }

  @Test
  public void shouldThrowErrorIfFileNotPresent() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_MISSING);

    jasperTemplateService.validateFileAndInsertTemplate(new JasperTemplate(), null);
  }

  @Test
  public void shouldThrowErrorIfFileIsInvalid() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_INVALID);

    jasperTemplateService.validateFileAndInsertTemplate(new JasperTemplate(),
        new MockMultipartFile(NAME_OF_FILE, NAME_OF_FILE, "", new byte[1]));
  }

  @Test
  public void shouldThrowErrorIfTemplateNameAlreadyExists() throws Exception {
    JasperTemplate jasperTemplate = new JasperTemplate();
    jasperTemplate.setName("Name");
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_TEMPLATE_EXIST);
    when(jasperTemplateRepository.findByName(Matchers.anyObject())).thenReturn(jasperTemplate);

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, null);
  }

  @Test
  public void shouldThrowErrorIfImageDoesNotExist() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ReportImageMessageKeys.ERROR_NOT_FOUND_WITH_NAME);

    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);

    when(report.getParameters()).thenReturn(new JRParameter[]{param1});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);

    when(param1.isForPrompting()).thenReturn(false);
    when(param1.isSystemDefined()).thenReturn(false);
    when(param1.getName()).thenReturn(IMAGE_NAME);
    when(param1.getValueClassName()).thenReturn("java.awt.Image");

    when(reportImageRepository.findByName(IMAGE_NAME)).thenReturn(null);

    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    verify(jasperTemplateService, never()).saveWithParameters(jasperTemplate);
  }

  @Test
  public void shouldThrowErrorIfDisplayNameOfParameterIsMissing() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expect(hasProperty("params", arrayContaining("displayName")));
    expectedException.expectMessage(ERROR_REPORTING_PARAMETER_MISSING);

    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);
    JRParameter param2 = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);

    when(report.getParameters()).thenReturn(new JRParameter[]{param1, param2});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(param1.getPropertiesMap()).thenReturn(propertiesMap);
    when(param1.isForPrompting()).thenReturn(true);
    when(param2.isForPrompting()).thenReturn(true);

    String[] propertyNames = {"name1"};
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(null);
    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    verify(jasperTemplateService, never()).saveWithParameters(jasperTemplate);
  }

  @Test
  public void shouldValidateFileAndSetData() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);
    JRParameter param2 = mock(JRParameter.class);
    JRParameter param3 = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);
    JRExpression jrExpression = mock(JRExpression.class);

    String[] propertyNames = {DISPLAY_NAME};
    when(report.getParameters()).thenReturn(new JRParameter[]{param1, param2, param3});
    when(report.getProperty(REPORT_TYPE_PROPERTY)).thenReturn("test type");
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(PARAM_DISPLAY_NAME);
    when(propertiesMap.getProperty(REQUIRED)).thenReturn("true");
    when(propertiesMap.getProperty("options")).thenReturn("option 1,opt\\,ion 2");

    when(param1.getPropertiesMap()).thenReturn(propertiesMap);
    when(param1.getValueClassName()).thenReturn(JAVA_LANG_STRING);
    when(param1.getName()).thenReturn(PARAM_NAME);
    when(param1.isForPrompting()).thenReturn(true);
    when(param1.getDescription()).thenReturn(DESC);
    when(param1.getDefaultValueExpression()).thenReturn(jrExpression);
    when(jrExpression.getText()).thenReturn(DEFAULT_EXPR_TEXT);

    when(param2.getPropertiesMap()).thenReturn(propertiesMap);
    when(param2.getValueClassName()).thenReturn("java.lang.Integer");
    when(param2.getName()).thenReturn(PARAM_NAME);
    when(param2.isForPrompting()).thenReturn(true);
    when(param2.getDescription()).thenReturn(DESC);
    when(param2.getDefaultValueExpression()).thenReturn(jrExpression);

    when(param3.getValueClassName()).thenReturn("java.awt.Image");
    when(param3.isForPrompting()).thenReturn(false);
    when(param3.isSystemDefined()).thenReturn(false);
    when(param3.getName()).thenReturn(IMAGE_NAME);
    ReportImage reportImage = mock(ReportImage.class);
    when(reportImageRepository.findByName(IMAGE_NAME)).thenReturn(reportImage);

    ByteArrayOutputStream byteOutputStream = mock(ByteArrayOutputStream.class);
    whenNew(ByteArrayOutputStream.class).withAnyArguments().thenReturn(byteOutputStream);
    ObjectOutputStream objectOutputStream = spy(new ObjectOutputStream(byteOutputStream));
    whenNew(ObjectOutputStream.class).withArguments(byteOutputStream)
        .thenReturn(objectOutputStream);
    doNothing().when(objectOutputStream).writeObject(report);
    byte[] byteData = new byte[1];
    when(byteOutputStream.toByteArray()).thenReturn(byteData);
    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    verify(jasperTemplateRepository).save(jasperTemplate);

    assertEquals("test type", jasperTemplate.getType());
    assertThat(jasperTemplate.getTemplateParameters().get(0).getDisplayName(),
        is(PARAM_DISPLAY_NAME));
    assertThat(jasperTemplate.getTemplateParameters().get(0).getDescription(), is(DESC));
    assertThat(jasperTemplate.getTemplateParameters().get(0).getName(), is(PARAM_NAME));
    assertThat(jasperTemplate.getTemplateParameters().get(0).getRequired(), is(true));
    assertThat(jasperTemplate.getTemplateParameters().get(0).getOptions(), contains("option 1",
            "opt,ion 2"));
    assertEquals(1, jasperTemplate.getReportImages().size());
    assertTrue(jasperTemplate.getReportImages().contains(reportImage));
  }

  @Test
  public void shouldValidateFileAndSetDataIfDefaultValueExpressionIsNull() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);
    JRParameter param2 = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);
    JRExpression jrExpression = mock(JRExpression.class);
    String[] propertyNames = {DISPLAY_NAME};

    when(report.getParameters()).thenReturn(new JRParameter[]{param1, param2});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(PARAM_DISPLAY_NAME);

    when(param1.getPropertiesMap()).thenReturn(propertiesMap);
    when(param1.getValueClassName()).thenReturn(JAVA_LANG_STRING);
    when(param1.isForPrompting()).thenReturn(true);
    when(param1.getDefaultValueExpression()).thenReturn(jrExpression);
    when(jrExpression.getText()).thenReturn(DEFAULT_EXPR_TEXT);

    when(param2.getPropertiesMap()).thenReturn(propertiesMap);
    when(param2.getValueClassName()).thenReturn("java.lang.Integer");
    when(param2.isForPrompting()).thenReturn(true);
    when(param2.getDefaultValueExpression()).thenReturn(null);

    ByteArrayOutputStream byteOutputStream = mock(ByteArrayOutputStream.class);
    whenNew(ByteArrayOutputStream.class).withAnyArguments().thenReturn(byteOutputStream);
    ObjectOutputStream objectOutputStream = spy(new ObjectOutputStream(byteOutputStream));
    whenNew(ObjectOutputStream.class).withArguments(byteOutputStream)
        .thenReturn(objectOutputStream);
    doNothing().when(objectOutputStream).writeObject(report);
    byte[] byteData = new byte[1];
    when(byteOutputStream.toByteArray()).thenReturn(byteData);
    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    verify(jasperTemplateRepository).save(jasperTemplate);
    assertThat(jasperTemplate.getTemplateParameters().get(0).getDisplayName(),
        is(PARAM_DISPLAY_NAME));
  }

  @Test
  public void mapRequestParametersToTemplateShouldReturnEmptyMapIfNoParameters() {
    when(request.getParameterMap()).thenReturn(Collections.emptyMap());
    when(template.getTemplateParameters()).thenReturn(null);

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void mapRequestParametersToTemplateShouldReturnEmptyMapIfNoTemplateParameters() {
    when(request.getParameterMap()).thenReturn(Collections.singletonMap("key1",
        new String[]{"value1"}));
    when(template.getTemplateParameters()).thenReturn(null);

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void mapRequestParametersToTemplateShouldReturnEmptyMapIfNoRequestParameters() {
    JasperTemplateParameter templateParameter = new JasperTemplateParameter();
    templateParameter.setTemplate(template);
    templateParameter.setName(PARAM1);

    when(request.getParameterMap()).thenReturn(Collections.emptyMap());
    when(template.getTemplateParameters()).thenReturn(Collections.singletonList(templateParameter));

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void mapRequestParametersToTemplateShouldMatchCaseInsensitively() {
    JasperTemplateParameter param1 = new JasperTemplateParameter();
    param1.setTemplate(template);
    param1.setName(PARAM1);

    Map<String, String[]> requestParameterMap = new HashMap<>();
    requestParameterMap.put("PARAM1", new String[]{"value1"});

    List<JasperTemplateParameter> templateParameterList = Arrays.asList(param1);

    when(request.getParameterMap()).thenReturn(requestParameterMap);
    when(template.getTemplateParameters()).thenReturn(templateParameterList);

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(1));
    assertTrue(resultMap.containsKey(PARAM1));
    assertEquals("value1", resultMap.get(PARAM1));
  }

  @Test
  public void mapRequestParametersToTemplateShouldHandleMultipleValues() {
    JasperTemplateParameter param1 = new JasperTemplateParameter();
    param1.setTemplate(template);
    param1.setName(PARAM1);

    Map<String, String[]> requestParameterMap = new HashMap<>();
    requestParameterMap.put(PARAM1, new String[]{});

    List<JasperTemplateParameter> templateParameterList = Arrays.asList(param1);

    when(request.getParameterMap()).thenReturn(requestParameterMap);
    when(template.getTemplateParameters()).thenReturn(templateParameterList);

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void shouldThrowErrorIfParameterDataTypeIsInvalid() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_PARAMETER_INCORRECT_TYPE);

    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);

    when(report.getParameters()).thenReturn(new JRParameter[]{param1});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(param1.getPropertiesMap()).thenReturn(propertiesMap);
    when(param1.getValueClassName()).thenReturn("invalid.Class.Name");
    when(param1.isForPrompting()).thenReturn(true);
    when(param1.getName()).thenReturn(PARAM1);

    String[] propertyNames = {DISPLAY_NAME};
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(PARAM_DISPLAY_NAME);

    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);
  }

  @Test
  public void shouldExtractDependenciesFromParameter() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter param1 = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);
    JRExpression jrExpression = mock(JRExpression.class);

    String[] propertyNames = {DISPLAY_NAME};
    when(report.getParameters()).thenReturn(new JRParameter[]{param1});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(PARAM_DISPLAY_NAME);
    when(propertiesMap.getProperty("dependencies")).thenReturn("field1:equal:value1,"
        + "field2:contains:value2");

    when(param1.getPropertiesMap()).thenReturn(propertiesMap);
    when(param1.getValueClassName()).thenReturn(JAVA_LANG_STRING);
    when(param1.getName()).thenReturn(PARAM_NAME);
    when(param1.isForPrompting()).thenReturn(true);
    when(param1.getDefaultValueExpression()).thenReturn(jrExpression);
    when(jrExpression.getText()).thenReturn(DEFAULT_EXPR_TEXT);

    ByteArrayOutputStream byteOutputStream = mock(ByteArrayOutputStream.class);
    whenNew(ByteArrayOutputStream.class).withAnyArguments().thenReturn(byteOutputStream);
    ObjectOutputStream objectOutputStream = spy(new ObjectOutputStream(byteOutputStream));
    whenNew(ObjectOutputStream.class).withArguments(byteOutputStream)
        .thenReturn(objectOutputStream);
    doNothing().when(objectOutputStream).writeObject(report);
    byte[] byteData = new byte[1];
    when(byteOutputStream.toByteArray()).thenReturn(byteData);
    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    assertEquals(2, jasperTemplate.getTemplateParameters().get(0).getDependencies().size());
  }

  @Test
  public void mapRequestParametersToTemplateShouldNotReturnBlankNullOrUndefinedStringValues() {
    JasperTemplateParameter param1 = new JasperTemplateParameter();
    param1.setTemplate(template);
    param1.setName(PARAM1);

    JasperTemplateParameter param2 = new JasperTemplateParameter();
    param2.setTemplate(template);
    param2.setName(PARAM2);

    JasperTemplateParameter param3 = new JasperTemplateParameter();
    param3.setTemplate(template);
    param3.setName(PARAM3);

    JasperTemplateParameter param4 = new JasperTemplateParameter();
    param4.setTemplate(template);
    param4.setName(PARAM4);

    Map<String, String[]> requestParameterMap = new HashMap<>();
    requestParameterMap.put(PARAM1, new String[]{""});
    requestParameterMap.put(PARAM2, new String[]{" "});
    requestParameterMap.put(PARAM3, new String[]{"null"});
    requestParameterMap.put(PARAM4, new String[]{"undefined"});

    List<JasperTemplateParameter> templateParameterList =
        Arrays.asList(param1, param2, param3, param4);

    when(request.getParameterMap()).thenReturn(requestParameterMap);
    when(template.getTemplateParameters()).thenReturn(templateParameterList);

    Map<String, Object> resultMap = jasperTemplateService.mapRequestParametersToTemplate(request,
        template);

    assertThat(resultMap.size(), is(0));
  }


  @Test
  public void mapReportImagesToTemplateShouldReturnEmptyMapIfNoImages()
      throws JasperReportViewException {
    when(template.getReportImages()).thenReturn(null);

    Map<String, BufferedImage> resultMap = jasperTemplateService
        .mapReportImagesToTemplate(template);

    assertThat(resultMap.size(), is(0));
  }

  @Test
  public void mapReportImagesToTemplateShouldReturnMatchingImages()
      throws JasperReportViewException, IOException {
    BufferedImage image = new BufferedImage(1, 1, TYPE_INT_RGB);
    byte[] expectedData = convertImageToByteArray(image);

    ReportImage reportImage = new ReportImage();
    reportImage.setName(IMAGE_NAME);
    reportImage.setData(expectedData);

    Set<ReportImage> images = new HashSet<>();
    images.add(reportImage);

    when(template.getReportImages()).thenReturn(images);

    Map<String, BufferedImage> resultMap = jasperTemplateService
        .mapReportImagesToTemplate(template);

    assertThat(resultMap.size(), is(1));
    assertTrue(resultMap.containsKey(IMAGE_NAME));
    assertTrue(Arrays.equals(expectedData,
        convertImageToByteArray(resultMap.get(IMAGE_NAME))));
  }

  @Test
  public void saveWithParametersShouldSaveTemplate() {
    template = new JasperTemplate();

    jasperTemplateService.saveWithParameters(template);

    verify(jasperTemplateRepository).save(template);
  }

  @Test
  public void shouldThrowErrorIfCategoryNotFound() throws Exception {
    expectedException.expect(ReportingException.class);
    expectedException.expectMessage("report.error.reportCategory.notFound");

    given(jasperTemplateRepository.findByName(anyString()))
        .willReturn(null);

    given(reportCategoryRepository.findByName(anyString()))
        .willReturn(Optional.empty());

    MultipartFile file = mock(MultipartFile.class);
    List<String> requiredRights = Collections.singletonList(USERS_MANAGE);

    given(rightReferenceDataService.findRight(requiredRights.get(0)))
        .willReturn(new RightDto());

    jasperTemplateService.saveTemplate(file, "TestName", "Description",
        requiredRights, "NonExistentCategory", false);
  }

  @Test
  public void shouldUpdateTemplateDescriptionAndRights() throws Exception {
    ReportCategory reportCategory = new ReportCategory();
    reportCategory.setId(UUID.randomUUID());
    reportCategory.setName(CATEGORY_NAME);

    JasperTemplate oldTemplate = new JasperTemplate();
    oldTemplate.setName(DISPLAY_NAME);
    oldTemplate.setId(UUID.randomUUID());
    oldTemplate.setRequiredRights(new ArrayList<>());
    oldTemplate.setCategory(reportCategory);
    oldTemplate.setDescription("Old Description");

    given(jasperTemplateRepository.findByName(anyString()))
        .willReturn(oldTemplate);

    given(reportCategoryRepository.findByName(anyString()))
        .willReturn(Optional.of(reportCategory));

    given(rightReferenceDataService.findRight(anyString()))
        .willReturn(new RightDto());

    JasperTemplateService service = spy(jasperTemplateService);
    MultipartFile file = mock(MultipartFile.class);
    String newDescription = "New Description";
    List<String> newRights = Collections.singletonList(USERS_MANAGE);

    doNothing().when(service)
        .validateFileAndSaveTemplate(any(JasperTemplate.class), eq(file));

    JasperTemplate resultTemplate = service.saveTemplate(file,
        DISPLAY_NAME, newDescription, newRights, CATEGORY_NAME, true);

    assertEquals(newDescription, resultTemplate.getDescription());
    assertEquals(newRights, resultTemplate.getRequiredRights());
  }

  @Test
  public void getLocaleBundleShouldReturnEmptyMapForInvalidResourceBundleNames() throws Exception {
    assertTrue(jasperTemplateService.getLocaleBundleParameters(null).isEmpty());
  }

  @Test
  public void getLocaleBundleShouldFallbackToEnglishWhenLocaleBuilderThrows() throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    ResourceBundle mockBundle = mock(ResourceBundle.class);
    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class),
        any(URLClassLoader.class))).thenReturn(mockBundle);

    Map<String, Object> result = jasperTemplateService
        .getLocaleBundleParameters("invalid@locale#string");

    assertEquals(Locale.ENGLISH, result.get(JRParameter.REPORT_LOCALE));
  }

  @Test
  public void getLocaleBundleShouldReturnEmptyMapIfResourceBundleDirectoryDoesNotExist()
      throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);

    when(mockDir.exists()).thenReturn(false);

    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class)))
        .thenThrow(new MissingResourceException("Test", RESOURCE_BUNDLE_NAME, RESOURCE_BUNDLE_KEY));

    assertTrue(jasperTemplateService.getLocaleBundleParameters("en").isEmpty());

    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(false);
    assertTrue(jasperTemplateService.getLocaleBundleParameters("en").isEmpty());
  }

  @Test
  public void getLocaleBundleShouldFallbackToInternalBundleWhenConfigDirectoryNotFound()
      throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(false);

    ResourceBundle fallbackBundle = mock(ResourceBundle.class);

    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_CLASSPATH), any(Locale.class)))
        .thenReturn(fallbackBundle);

    Map<String, Object> result = jasperTemplateService
        .getLocaleBundleParameters("en");

    assertEquals(2, result.size());
    assertEquals(fallbackBundle, result.get(JRParameter.REPORT_RESOURCE_BUNDLE));
    assertEquals(Locale.ENGLISH, result.get(JRParameter.REPORT_LOCALE));
  }

  @Test
  public void getLocaleBundleShouldFallbackToInternalBundleWhenConfigBundleNotFound()
      throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    ResourceBundle fallbackBundle = mock(ResourceBundle.class);

    mockStatic(ResourceBundle.class);

    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class),
        any(URLClassLoader.class)))
        .thenThrow(new MissingResourceException("Missing resource from config",
            "ResourceBundle", RESOURCE_BUNDLE_KEY));

    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_CLASSPATH), any(Locale.class)))
        .thenReturn(fallbackBundle);

    Map<String, Object> result = jasperTemplateService
        .getLocaleBundleParameters("fr");

    assertEquals(2, result.size());
    assertEquals(fallbackBundle, result.get(JRParameter.REPORT_RESOURCE_BUNDLE));
    assertEquals(new Locale.Builder().setLanguageTag("fr").build(),
        result.get(JRParameter.REPORT_LOCALE));
  }

  @Test
  public void getLocaleBundleShouldReturnEmptyMapIfTranslationBundleIsMissingFromDisk()
      throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    mockStatic(ResourceBundle.class);

    when(ResourceBundle.getBundle(anyString(), any(Locale.class), any(URLClassLoader.class)))
        .thenThrow(new MissingResourceException("Missing resource exception occurred",
            "ResourceBundle", RESOURCE_BUNDLE_KEY));
    assertTrue(jasperTemplateService.getLocaleBundleParameters("en").isEmpty());
  }

  @Test
  public void getLocaleBundleShouldReturnMapWithBundleAndLocale() throws Exception {
    // Mock the Config Directory
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    ResourceBundle mockBundle = mock(ResourceBundle.class);
    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class),
        any(URLClassLoader.class))).thenReturn(mockBundle);

    Map<String, Object> result = jasperTemplateService
        .getLocaleBundleParameters("fr");

    assertEquals(2, result.size());
    assertEquals(mockBundle, result.get(JRParameter.REPORT_RESOURCE_BUNDLE));
    assertEquals(new Locale.Builder().setLanguageTag("fr").build(),
        result.get(JRParameter.REPORT_LOCALE));
  }

  @Test
  public void getLocaleBundleShouldFallbackToEnglishForInvalidLocale() throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    ResourceBundle mockBundle = mock(ResourceBundle.class);
    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class),
        any(URLClassLoader.class))).thenReturn(mockBundle);

    Map<String, Object> result = jasperTemplateService
        .getLocaleBundleParameters("invalid_locale");

    assertEquals(Locale.ENGLISH, result.get(JRParameter.REPORT_LOCALE));
  }

  @Test
  public void getLocaleBundleShouldFallBackToInternalWhenExternalBundleMissing()
      throws Exception {
    File mockDir = mock(File.class);
    whenNew(File.class).withArguments(RESOURCE_BUNDLE_PATH).thenReturn(mockDir);
    when(mockDir.exists()).thenReturn(true);
    when(mockDir.isDirectory()).thenReturn(true);
    when(mockDir.toURI()).thenReturn(new java.net.URI(DUMMY_FILE_URI));

    mockStatic(ResourceBundle.class);
    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_NAME), any(Locale.class),
        any(URLClassLoader.class)))
        .thenThrow(new MissingResourceException("Missing", RESOURCE_BUNDLE_NAME,
            RESOURCE_BUNDLE_KEY));

    when(ResourceBundle.getBundle(eq(RESOURCE_BUNDLE_CLASSPATH), any(Locale.class)))
        .thenThrow(new MissingResourceException("Fallback missing", RESOURCE_BUNDLE_NAME,
            RESOURCE_BUNDLE_KEY));

    assertTrue(jasperTemplateService.getLocaleBundleParameters("en").isEmpty());
  }

  @Test
  public void loadReportWithByteArrayShouldReturnNullForEmptyArray() throws Exception {
    byte[] emptyData = new byte[0];
    JasperReport result = jasperTemplateService.loadReport(emptyData);
    assertNull(result);
  }

  @Test
  public void loadReportWithByteArrayShouldSuccessfullyLoadValidData() throws Exception {
    byte[] templateData = new byte[]{1, 2, 3};

    JasperReport mockReport = mock(JasperReport.class);
    mockStatic(JRLoader.class);
    when(JRLoader.loadObject(any(InputStream.class))).thenReturn(mockReport);

    JasperReport result = jasperTemplateService.loadReport(templateData);

    assertEquals(mockReport, result);
  }

  @Test
  public void loadReportWithByteArrayShouldThrowExceptionForInvalidReportFile() throws Exception {
    mockStatic(JRLoader.class);
    when(JRLoader.loadObject(any(InputStream.class))).thenThrow(new JRException("Invalid file"));

    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_INVALID);

    byte[] templateData = new byte[]{1, 2, 3};
    jasperTemplateService.loadReport(templateData);
  }

  @Test
  public void loadReportShouldReturnNullForNullTemplate() throws Exception {
    JasperReport result = jasperTemplateService.loadReport((JasperTemplate) null);
    assertNull(result);
  }

  @Test
  public void loadReportShouldSuccessfullyLoadValidTemplate() throws Exception {
    template = mock(JasperTemplate.class);
    byte[] templateData = new byte[]{1, 2, 3};
    when(template.getData()).thenReturn(templateData);

    JasperReport mockReport = mock(JasperReport.class);
    mockStatic(JRLoader.class);
    when(JRLoader.loadObject(any(InputStream.class))).thenReturn(mockReport);

    JasperReport result = jasperTemplateService.loadReport(template);

    assertEquals(mockReport, result);
  }

  @Test
  public void loadReportShouldThrowExceptionForInvalidReportFile() throws Exception {
    template = mock(JasperTemplate.class);
    byte[] templateData = new byte[]{1, 2, 3};
    when(template.getData()).thenReturn(templateData);

    mockStatic(JRLoader.class);
    when(JRLoader.loadObject(any(InputStream.class))).thenThrow(new JRException("Invalid file"));

    expectedException.expect(ReportingException.class);
    expectedException.expectMessage(ERROR_REPORTING_FILE_INVALID);

    jasperTemplateService.loadReport(template);
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapWhenHeaderConfigFileDoesNotExist()
      throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.PORTRAIT);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    File tempJrxml = File.createTempFile(GLOBAL_HEADER_PORTRAIT, JRXML_EXTENSION);
    tempJrxml.deleteOnExit();

    File mockHeaderFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/GlobalHeaderPortrait.jrxml")
        .thenReturn(mockHeaderFile);
    when(mockHeaderFile.exists()).thenReturn(true);
    when(mockHeaderFile.toPath()).thenReturn(tempJrxml.toPath());

    JasperReport mockCompiledHeader = mock(JasperReport.class);
    mockStatic(JasperCompileManager.class);
    when(JasperCompileManager.compileReport(any(InputStream.class)))
        .thenReturn(mockCompiledHeader);

    File mockConfigFile = mock(File.class);
    whenNew(File.class).withArguments(HEADER_CONFIG_PROPERTIES)
        .thenReturn(mockConfigFile);
    when(mockConfigFile.exists()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertEquals(1, result.size());
    assertEquals(mockCompiledHeader, result.get(HEADER_PARAM_NAME));
  }

  @Test
  public void getGlobalHeaderShouldSkipImageParamsWhenImageFileDoesNotExist() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.PORTRAIT);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    File tempJrxml = File.createTempFile(GLOBAL_HEADER_PORTRAIT, JRXML_EXTENSION);
    tempJrxml.deleteOnExit();

    File tempPropsFile = File.createTempFile("header_config", ".properties");
    tempPropsFile.deleteOnExit();

    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempPropsFile)) {
      fos.write("title=Test Title\nlogoImage=nonexistent.png".getBytes());
    }

    File mockHeaderFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/GlobalHeaderPortrait.jrxml")
        .thenReturn(mockHeaderFile);
    when(mockHeaderFile.exists()).thenReturn(true);
    when(mockHeaderFile.toPath()).thenReturn(tempJrxml.toPath());

    JasperReport mockCompiledHeader = mock(JasperReport.class);
    mockStatic(JasperCompileManager.class);
    when(JasperCompileManager.compileReport(any(InputStream.class)))
        .thenReturn(mockCompiledHeader);

    File mockConfigFile = mock(File.class);
    whenNew(File.class).withArguments(HEADER_CONFIG_PROPERTIES)
        .thenReturn(mockConfigFile);
    when(mockConfigFile.exists()).thenReturn(true);
    when(mockConfigFile.toPath()).thenReturn(tempPropsFile.toPath());

    File mockImageFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/nonexistent.png")
        .thenReturn(mockImageFile);
    when(mockImageFile.exists()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertEquals(2, result.size());
    assertEquals(mockCompiledHeader, result.get(HEADER_PARAM_NAME));
    assertEquals("Test Title", result.get("title"));
    assertNull(result.get("logoImage"));
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfNoHeaderParam() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter param = mock(JRParameter.class);
    when(param.getName()).thenReturn("someOtherParam");
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{param});

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapForNullParentReport() throws Exception {
    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(null);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfParametersAreNull() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    when(parentReport.getParameters()).thenReturn(null);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfConfigDirectoryDoesNotExist()
      throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.PORTRAIT);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfConfigDirectoryIsNotADirectory()
      throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.PORTRAIT);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfHeaderFileDoesNotExist() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.PORTRAIT);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    File mockHeaderFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/GlobalHeaderPortrait.jrxml")
        .thenReturn(mockHeaderFile);
    when(mockHeaderFile.exists()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldUseLandscapeHeaderForLandscapeOrientation() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.LANDSCAPE);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    File tempJrxml = File.createTempFile(GLOBAL_HEADER_LANDSCAPE, JRXML_EXTENSION);
    tempJrxml.deleteOnExit();

    File mockHeaderFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/GlobalHeaderLandscape.jrxml")
        .thenReturn(mockHeaderFile);
    when(mockHeaderFile.exists()).thenReturn(true);
    when(mockHeaderFile.toPath()).thenReturn(tempJrxml.toPath());

    JasperReport mockCompiledHeader = mock(JasperReport.class);
    mockStatic(JasperCompileManager.class);
    when(JasperCompileManager.compileReport(any(InputStream.class)))
        .thenReturn(mockCompiledHeader);

    File mockConfigFile = mock(File.class);
    whenNew(File.class).withArguments(HEADER_CONFIG_PROPERTIES)
        .thenReturn(mockConfigFile);
    when(mockConfigFile.exists()).thenReturn(false);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertEquals(1, result.size());
    assertEquals(mockCompiledHeader, result.get(HEADER_PARAM_NAME));
  }

  @Test
  public void getGlobalHeaderShouldReturnEmptyMapIfOrientationNotRecognized() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});

    // Unrecognized orientation
    when(parentReport.getOrientationValue()).thenReturn(null);

    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertTrue(result.isEmpty());
  }

  @Test
  public void getGlobalHeaderShouldReturnCompiledHeaderAndDynamicParams() throws Exception {
    JasperReport parentReport = mock(JasperReport.class);
    JRParameter headerParam = mock(JRParameter.class);
    when(headerParam.getName()).thenReturn(HEADER_PARAM_NAME);
    when(parentReport.getParameters()).thenReturn(new JRParameter[]{headerParam});
    when(parentReport.getOrientationValue()).thenReturn(OrientationEnum.LANDSCAPE);

    // Mock the Config Directory
    File mockConfigDir = mock(File.class);
    whenNew(File.class).withArguments(CONFIG_PATH_CONST).thenReturn(mockConfigDir);
    when(mockConfigDir.exists()).thenReturn(true);
    when(mockConfigDir.isDirectory()).thenReturn(true);

    File tempJrxml = File.createTempFile(GLOBAL_HEADER_LANDSCAPE, JRXML_EXTENSION);
    tempJrxml.deleteOnExit();

    File tempPropsFile = File.createTempFile("header_config", ".properties");
    tempPropsFile.deleteOnExit();

    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempPropsFile)) {
      fos.write("title=Test Ministry\nlogoImage=logo.png".getBytes());
    }

    File mockHeaderFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/GlobalHeaderLandscape.jrxml")
        .thenReturn(mockHeaderFile);
    when(mockHeaderFile.exists()).thenReturn(true);

    when(mockHeaderFile.toPath()).thenReturn(tempJrxml.toPath());

    JasperReport mockCompiledHeader = mock(JasperReport.class);
    mockStatic(JasperCompileManager.class);

    when(JasperCompileManager.compileReport(any(InputStream.class))).thenReturn(mockCompiledHeader);

    File mockConfigFile = mock(File.class);
    whenNew(File.class).withArguments(HEADER_CONFIG_PROPERTIES)
        .thenReturn(mockConfigFile);
    when(mockConfigFile.exists()).thenReturn(true);

    when(mockConfigFile.toPath()).thenReturn(tempPropsFile.toPath());

    // Image File check
    File mockImageFile = mock(File.class);
    whenNew(File.class).withArguments("/config/reports/logo.png").thenReturn(mockImageFile);
    when(mockImageFile.exists()).thenReturn(true);
    when(mockImageFile.getAbsolutePath()).thenReturn("/config/reports/logo.png");

    Map<String, Object> result = jasperTemplateService
        .getMapSubreportGlobalHeaderParameters(parentReport);

    assertEquals(3, result.size());
    assertEquals(mockCompiledHeader, result.get(HEADER_PARAM_NAME));
    assertEquals("Test Ministry", result.get("title"));
    assertEquals("/config/reports/logo.png", result.get("logoImage"));
  }

  @Test
  public void shouldSetDisplayOrderFromJrxmlDeclarationOrder() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(NAME_OF_FILE);

    mockStatic(JasperCompileManager.class);
    JasperReport report = mock(JasperReport.class);
    InputStream inputStream = mock(InputStream.class);
    when(file.getInputStream()).thenReturn(inputStream);

    JRParameter first = mock(JRParameter.class);
    JRParameter imageParam = mock(JRParameter.class);
    JRParameter second = mock(JRParameter.class);
    JRParameter third = mock(JRParameter.class);
    JRPropertiesMap propertiesMap = mock(JRPropertiesMap.class);
    JRExpression jrExpression = mock(JRExpression.class);

    String[] propertyNames = {DISPLAY_NAME};
    when(report.getParameters())
        .thenReturn(new JRParameter[]{first, imageParam, second, third});
    when(JasperCompileManager.compileReport(inputStream)).thenReturn(report);
    when(propertiesMap.getPropertyNames()).thenReturn(propertyNames);
    when(propertiesMap.getProperty(DISPLAY_NAME)).thenReturn(PARAM_DISPLAY_NAME);
    when(jrExpression.getText()).thenReturn(DEFAULT_EXPR_TEXT);

    for (JRParameter p : new JRParameter[]{first, second, third}) {
      when(p.getPropertiesMap()).thenReturn(propertiesMap);
      when(p.getValueClassName()).thenReturn(JAVA_LANG_STRING);
      when(p.isForPrompting()).thenReturn(true);
      when(p.getDefaultValueExpression()).thenReturn(jrExpression);
    }
    when(first.getName()).thenReturn(PARAM1);
    when(second.getName()).thenReturn(PARAM2);
    when(third.getName()).thenReturn(PARAM3);

    when(imageParam.getValueClassName()).thenReturn("java.awt.Image");
    when(imageParam.isForPrompting()).thenReturn(false);
    when(imageParam.isSystemDefined()).thenReturn(false);
    when(imageParam.getName()).thenReturn(IMAGE_NAME);
    when(reportImageRepository.findByName(IMAGE_NAME)).thenReturn(mock(ReportImage.class));

    ByteArrayOutputStream byteOutputStream = mock(ByteArrayOutputStream.class);
    whenNew(ByteArrayOutputStream.class).withAnyArguments().thenReturn(byteOutputStream);
    ObjectOutputStream objectOutputStream = spy(new ObjectOutputStream(byteOutputStream));
    whenNew(ObjectOutputStream.class).withArguments(byteOutputStream)
        .thenReturn(objectOutputStream);
    doNothing().when(objectOutputStream).writeObject(report);
    when(byteOutputStream.toByteArray()).thenReturn(new byte[1]);

    JasperTemplate jasperTemplate = new JasperTemplate();

    jasperTemplateService.validateFileAndInsertTemplate(jasperTemplate, file);

    List<JasperTemplateParameter> params = jasperTemplate.getTemplateParameters();
    assertEquals(3, params.size());
    assertEquals(PARAM1, params.get(0).getName());
    assertEquals(Integer.valueOf(0), params.get(0).getDisplayOrder());
    assertEquals(PARAM2, params.get(1).getName());
    assertEquals(Integer.valueOf(1), params.get(1).getDisplayOrder());
    assertEquals(PARAM3, params.get(2).getName());
    assertEquals(Integer.valueOf(2), params.get(2).getDisplayOrder());
  }

  private byte[] convertImageToByteArray(BufferedImage image) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    ImageIO.write(image, "png", os);
    final byte[] expectedData = os.toByteArray();
    os.close();
    return expectedData;
  }
}
