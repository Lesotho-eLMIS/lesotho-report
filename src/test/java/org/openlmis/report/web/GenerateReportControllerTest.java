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

package org.openlmis.report.web;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openlmis.report.dto.external.GenerateReportDto;
import org.openlmis.report.exception.ReportingException;
import org.openlmis.report.service.JasperReportsViewService;
import org.openlmis.report.service.JasperTemplateService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

public class GenerateReportControllerTest {
  private static final String TEMPLATE_NAME = "Test Report";
  private static final byte[] TEMPLATE_DATA = "jasper-template-data".getBytes();
  private static final byte[] EXPECTED_REPORT = "generated-report-binary".getBytes();

  @Mock
  private JasperTemplateService jasperTemplateService;

  @Mock
  private JasperReportsViewService jasperReportsViewService;

  @Mock
  private JasperReport jasperReport;

  @InjectMocks
  private GenerateReportController controller;

  @Captor
  private ArgumentCaptor<Map<String, Object>> paramsCaptor;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(controller, "dateTimeFormat", "yyyy-MM-dd HH:mm:ss");
    ReflectionTestUtils.setField(controller, "dateFormat", "yyyy-MM-dd");
    ReflectionTestUtils.setField(controller, "groupingSeparator", ",");
    ReflectionTestUtils.setField(controller, "groupingSize", "3");
    ReflectionTestUtils.setField(controller, "timeZoneId", "UTC");

    when(jasperTemplateService.loadReport(any(byte[].class))).thenReturn(jasperReport);
    when(jasperReportsViewService.getJasperReportsView(any(byte[].class), any(Map.class)))
        .thenReturn(EXPECTED_REPORT);
  }

  @Test
  public void shouldGenerateReportAsPdfByDefault() throws Exception {
    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA,
        new HashMap<>());

    ResponseEntity<byte[]> response = controller.generateReport(request);

    assertEquals(200, response.getStatusCodeValue());
    assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
    assertTrue(response.getHeaders().getFirst("Content-Disposition")
        .contains("filename=Test_Report.pdf"));
    assertArrayEquals(EXPECTED_REPORT, response.getBody());
  }

  @Test
  public void shouldGenerateReportAsCsv() throws Exception {
    Map<String, Object> params = new HashMap<>();
    params.put("format", "csv");
    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA, params);

    ResponseEntity<byte[]> response = controller.generateReport(request);

    assertEquals(200, response.getStatusCodeValue());
    MediaType expectedMediaType = new MediaType("text", "csv",
        StandardCharsets.UTF_8);
    assertEquals(expectedMediaType, response.getHeaders().getContentType());
    assertTrue(response.getHeaders().getFirst("Content-Disposition")
        .contains("filename=Test_Report.csv"));
  }

  @Test
  public void shouldGenerateReportAsXls() throws Exception {
    Map<String, Object> params = new HashMap<>();
    params.put("format", "xls");
    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA, params);

    ResponseEntity<byte[]> response = controller.generateReport(request);

    assertEquals(200, response.getStatusCodeValue());
    MediaType expectedMediaType = new MediaType("application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet", StandardCharsets.UTF_8);
    assertEquals(expectedMediaType, response.getHeaders().getContentType());
    assertTrue(response.getHeaders().getFirst("Content-Disposition")
        .contains("filename=Test_Report.xls"));
  }

  @Test
  public void shouldConvertCollectionDatasourceToJasperMapDataSource() throws Exception {
    Map<String, Object> params = new HashMap<>();
    List<Map<String, Object>> mockJsonList = Collections.singletonList(
        Collections.singletonMap("key", "value")
    );
    params.put("datasource", mockJsonList);

    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA, params);

    controller.generateReport(request);

    verify(jasperReportsViewService).getJasperReportsView(eq(TEMPLATE_DATA),
        paramsCaptor.capture());
    Map<String, Object> capturedParams = paramsCaptor.getValue();

    Object processedDatasource = capturedParams.get("datasource");
    assertNotNull(processedDatasource);
    assertTrue("Datasource should be wrapped in JRMapCollectionDataSource",
        processedDatasource instanceof JRMapCollectionDataSource);
  }

  @Test
  public void shouldInjectFormattingParameters() throws Exception {
    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA,
        new HashMap<>());

    controller.generateReport(request);

    verify(jasperReportsViewService).getJasperReportsView(eq(TEMPLATE_DATA),
        paramsCaptor.capture());
    Map<String, Object> capturedParams = paramsCaptor.getValue();

    assertEquals("pdf", capturedParams.get("format"));
    assertEquals("yyyy-MM-dd HH:mm:ss", capturedParams.get("dateTimeFormat"));
    assertEquals("UTC", capturedParams.get("timeZoneId"));

    assertTrue(capturedParams.get("decimalFormat") instanceof DecimalFormat);
    DecimalFormat df = (DecimalFormat) capturedParams.get("decimalFormat");
    assertEquals(3, df.getGroupingSize());
    assertEquals(',', df.getDecimalFormatSymbols().getGroupingSeparator());
  }

  @Test
  public void shouldHandleTranslationExceptionsGracefully() throws Exception {
    when(jasperTemplateService.getLocaleBundleParameters(anyString()))
        .thenThrow(new MalformedURLException("Simulated failure"));

    Map<String, Object> params = new HashMap<>();
    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA, params);

    ResponseEntity<byte[]> response = controller.generateReport(request);

    assertEquals(200, response.getStatusCodeValue());
    assertArrayEquals(EXPECTED_REPORT, response.getBody());
  }

  @Test(expected = ReportingException.class)
  public void shouldThrowReportingExceptionWhenSubreportCannotBeCompiled() throws Exception {
    String invalidSubreport = "invalid-base64";
    Map<String, Object> params = new HashMap<>();
    params.put("subreport_bytes", invalidSubreport);

    when(jasperTemplateService.loadReport(any(byte[].class)))
        .thenThrow(new ReportingException("Cannot compile subreport"));

    GenerateReportDto request = new GenerateReportDto(TEMPLATE_NAME, TEMPLATE_DATA, params);

    controller.generateReport(request);
  }
}
